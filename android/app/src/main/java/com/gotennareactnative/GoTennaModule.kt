package com.gotennareactnative

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.support.v4.content.ContextCompat
import android.util.Log
import android.widget.Toast
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

import com.gotenna.sdk.GoTenna
import com.gotenna.sdk.bluetooth.BluetoothAdapterManager
import com.gotenna.sdk.bluetooth.GTConnectionManager
import com.gotenna.sdk.commands.GTCommand
import com.gotenna.sdk.commands.GTCommandCenter
import com.gotenna.sdk.commands.GTError
import com.gotenna.sdk.commands.Place
import com.gotenna.sdk.exceptions.GTInvalidAppTokenException
import com.gotenna.sdk.interfaces.GTErrorListener
import com.gotenna.sdk.messages.GTBaseMessageData
import com.gotenna.sdk.messages.GTGroupCreationMessageData
import com.gotenna.sdk.messages.GTMessageData
import com.gotenna.sdk.messages.GTTextOnlyMessageData
import com.gotenna.sdk.responses.GTResponse
import com.gotenna.sdk.types.GTDataTypes
import com.gotenna.sdk.user.UserDataStore

/**
 * Created by aaronblondeau on 8/30/17.
 */

class GoTennaModule(reactContext: ReactApplicationContext?) : ReactContextBaseJavaModule(reactContext), GTConnectionManager.GTConnectionListener, LifecycleEventListener, GTCommandCenter.GTMessageListener {

    private var willRememberGotenna = false
    private var meshDevice = true

    private val SCAN_TIMEOUT = 25000 // 25 seconds
    private val BLUETOOTH_START_SCAN_DELAY = 500

    val handler = Handler(Looper.getMainLooper())
    var configured = false

    var bluetoothAdapterManager: BluetoothAdapterManager? = null
    var gtConnectionManager: GTConnectionManager? = null
    var userDataStore: UserDataStore? = null

    init {

        context = reactContext
        context?.addLifecycleEventListener(this)

    }

    companion object {
        val REQUEST_ENABLE_BT = 1003
        var context: ReactApplicationContext? = null

        fun emit(event:String) {
            context?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)?.emit(event, null)
        }

        fun emitBluetoothEnabled() {
            emit("pairBluetoothEnabled")
        }

        fun emitBluetoothDisabled() {
            emit("pairBluetoothDisabled")
        }

        fun emitRetryPair() {
            emit("pairShouldRetry")
        }

        fun emitConnected() {
            emit("connected")
        }

        fun emitDisconnected() {
            emit("disconnected")
        }

        fun emitScanTimedOut() {
            emit("scanTimedOut")
        }

        fun emitPairScanStop() {
            emit("pairScanStop")
        }

        fun emitPairScanStart() {
            emit("pairScanStart")
        }

        fun emitConfigured() {
            emit("configured")
        }
    }

    override fun getName(): String {
        return "goTenna"
    }

    @ReactMethod
    fun configure(apiKey: String, promise: Promise) {
        try {
            //Log.d("GoTennaModule", "~~ Configuring with key "+apiKey)
            GoTenna.setApplicationToken(context?.getApplicationContext(), apiKey)

            bluetoothAdapterManager = BluetoothAdapterManager.getInstance()
            gtConnectionManager = GTConnectionManager.getInstance()
            userDataStore = UserDataStore.getInstance()

            gtConnectionManager?.addGtConnectionListener(this)

            /*
            GTCommandCenter.getInstance().sendSetGeoRegion(Place.NORTH_AMERICA, GTCommand.GTCommandResponseListener { response ->
                Log.d("GoTennaModule", "~~ Success sending geo region");
            }, GTErrorListener {
                Log.e("GoTennaModule", "~~ Failed to set geo region!")
            })
            */

            val bluetoothStateChangeReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val action = intent.action

                    if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)

                        when (state) {
                            BluetoothAdapter.STATE_OFF -> gtConnectionManager?.disconnect()

                            BluetoothAdapter.STATE_ON -> {
                                // We need to delay the starting of the scan because even though we got the
                                // STATE_ON notification, the Bluetooth Adapter still needs some time
                                // to actually be ready to use.
                                val handler = Handler(Looper.getMainLooper())
                                handler.postDelayed({ startBluetoothPairingIfPossible() }, BLUETOOTH_START_SCAN_DELAY.toLong())
                            }
                        }
                    }
                }
            }

            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            context?.registerReceiver(bluetoothStateChangeReceiver, filter)

            GTCommandCenter.getInstance().setMessageListener(this)

            configured = true
            emitConfigured()
            promise.resolve(true);
        } catch (e: GTInvalidAppTokenException) {
            Log.w("GoTennaModule", e)
            promise.reject("Failed to set application token", e)
        }
    }

    @ReactMethod
    fun getState(promise: Promise) {
        val data = WritableNativeMap()
        val currentUser = userDataStore?.currentUser
        data.putBoolean("paired", gtConnectionManager?.isConnected ?: false)
        data.putInt("gid", currentUser?.gid?.toInt() ?: 0)
        data.putString("gid_name", currentUser?.name ?: "")
        promise.resolve(data)
    }

    @ReactMethod
    fun startPairingScan(rememberDevice: Boolean, isMeshDevice: Boolean, promise: Promise) {
        if(!configured) {
            return promise.reject("not_configured", "You must call configure (and wait till it completes) with an application token before using this module");
        }
        willRememberGotenna = rememberDevice;
        meshDevice = isMeshDevice;
        startBluetoothPairingIfPossible()
        promise.resolve(true)
    }

    @ReactMethod
    fun sendOneToOneMessage(gid: Integer, text: String, promise: Promise) {
        val currentUser = userDataStore?.currentUser

        if(currentUser == null) {
            return promise.reject("send_one_to_one_error", "GID is not set!")
        }

        if(text == "") {
            return promise.reject("send_one_to_one_error", "Text cannot be empty!")
        }

        if((gtConnectionManager != null) && (!gtConnectionManager!!.isConnected)) {
            return promise.reject("send_one_to_one_error", "Device not connected!")
        }

        val msg : GTTextOnlyMessageData = GTTextOnlyMessageData(text)

        GTCommandCenter.getInstance().sendMessage(msg.serializeToBytes(), gid.toLong(),GTCommand.GTCommandResponseListener { response ->

            Log.d("GoTennaModule", "~~ Success sending one to one message!");
            promise.resolve(true)

        }, GTErrorListener {

            Log.e("GoTennaModule", "~~ Failed to send one to one message : "+it.toString())
            promise.reject("send_one_to_one_error", it.toString());
        }, false)

    }

    @ReactMethod
    fun getSystemInfo(promise: Promise) {
        if(!configured) {
            return promise.reject("not_configured", "You must call configure (and wait till it completes) with an application token before using this module");
        }
        GTCommandCenter.getInstance().sendGetSystemInfo(GTCommandCenter.GTSystemInfoResponseListener { systemInfoResponseData ->
            // This is where you could retrieve info such at the goTenna's battery level and current firmware version
            // Toast.makeText(reactApplicationContext, systemInfoResponseData.toString(), Toast.LENGTH_LONG).show()

            val data = WritableNativeMap()
            data.putDouble("battery", systemInfoResponseData.batteryLevel.toDouble())
            data.putString("serial", systemInfoResponseData.goTennaSerialNumber)
            data.putString("text", systemInfoResponseData.toString())

            promise.resolve(data)
        }, object : GTErrorListener {
            override fun onError(error: GTError) {
                Log.w(javaClass.simpleName, error.toString())
                promise.reject("info_error", error.toString());
            }
        })
    }

    @ReactMethod
    fun setGID(gid: Integer, name: String, promise: Promise) {
        GTCommandCenter.getInstance().setGoTennaGID(gid.toLong(), name, GTCommand.GTCommandResponseListener { response ->

            Log.d("GoTennaModule", "~~ Success setting GID "+gid+", "+name);
            promise.resolve(true);

        }, GTErrorListener {

            Log.e("GoTennaModule", "Failed to set GID to "+gid+", "+name+"!")
            promise.reject("echo_error", it.toString());
        })

    }

    @ReactMethod
    fun disconnect(promise: Promise) {

        gtConnectionManager?.disconnect()
        promise.resolve(true);

    }

    @ReactMethod
    fun echo(promise: Promise) {

        // Send an echo command to the goTenna to flash the LED light
        GTCommandCenter.getInstance().sendEchoCommand(GTCommand.GTCommandResponseListener { response ->

            when (response.responseCode) {
                GTDataTypes.GTCommandResponseCode.POSITIVE -> promise.resolve("postitive")
                GTDataTypes.GTCommandResponseCode.NEGATIVE -> promise.resolve("negative")
                GTDataTypes.GTCommandResponseCode.ERROR -> promise.resolve("error")
            }

        }, GTErrorListener {
            promise.reject("echo_error", it.toString());
        })
    }

    protected fun startBluetoothPairingIfPossible() {
        val status = bluetoothAdapterManager?.getBluetoothStatus()

        when (status) {
            BluetoothAdapterManager.BluetoothStatus.SUPPORTED_AND_ENABLED -> {

                emitBluetoothEnabled()

                Log.d("GoTennaModule", "~~ Bluetooth supported and enabled, checking location permission.")

                if (hasLocationPermission()) {

                    Log.d("GoTennaModule", "~~ We do have location permission.")

                    if (!willRememberGotenna) {
                        // If we clear the last connected goTenna address,
                        // then the connection manager will look for any goTenna and try to connect to it.
                        // If we do not clear the address, it will specifically look for that last goTenna
                        // that it remembers being connected to.
                        gtConnectionManager?.clearConnectedGotennaAddress()
                    }

                    if(meshDevice) {
                        Log.d("GoTennaModule", "~~ scan and connect MESH.")
                        gtConnectionManager?.scanAndConnect(GTConnectionManager.GTDeviceType.MESH)
                    }
                    else {
                        Log.d("GoTennaModule", "~~ scan and connect V1.")
                        gtConnectionManager?.scanAndConnect(GTConnectionManager.GTDeviceType.V1)
                    }

                    handler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT.toLong())
                } else {
                    Log.d("GoTennaModule", "~~ We do NOT have location permission.")

                    context?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)?.emit("pairScanLocationPermissionNeeded", null)
                }
            }

            BluetoothAdapterManager.BluetoothStatus.SUPPORTED_NOT_ENABLED -> {
                val ctx = context
                ctx?.let {
                    BluetoothAdapterManager.showRequestBluetoothPermissionDialog(ctx.currentActivity, REQUEST_ENABLE_BT)
                }
            }

            BluetoothAdapterManager.BluetoothStatus.NOT_SUPPORTED -> {
                //val event = WritableNativeMap()
                //event.putString("message", "")

                context?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)?.emit("pairScanBluetoothNotAvaialble", null)
            }
        }
    }

    fun hasLocationPermission(): Boolean {
        val ctx = context
        ctx?.let {
            return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        return false
    }

    private val scanTimeoutRunnable = Runnable {
        stopScanning()
        emitScanTimedOut()
        emitPairScanStop()
    }

    private fun stopScanning() {
        handler.removeCallbacks(scanTimeoutRunnable)
        gtConnectionManager?.disconnect()

        if (!willRememberGotenna) {
            gtConnectionManager?.clearConnectedGotennaAddress()
        }
    }

    override fun onConnectionStateUpdated(gtConnectionState: GTConnectionManager.GTConnectionState) {

        Log.d("GoTennaModule", "~~ onConnectionStateUpdated")

        when (gtConnectionState) {
            GTConnectionManager.GTConnectionState.CONNECTED -> {
                Log.d("GoTennaModule", "~~ GTConnectonState CONNECTED")
                handler.removeCallbacks(scanTimeoutRunnable)
                emitConnected()
                emitPairScanStop()
            }
            GTConnectionManager.GTConnectionState.SCANNING -> {
                Log.d("GoTennaModule", "~~ GTConnectonState SCANNING")
                emitPairScanStart()
            }
            GTConnectionManager.GTConnectionState.DISCONNECTED -> {
                Log.d("GoTennaModule", "~~ GTConnectonState DISCONNECTED")
                emitPairScanStop()
                emitDisconnected()
            }
        }
    }

    override fun onHostDestroy() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onHostPause() {
        Log.d("GoTennaModule", "~~ onHostPause")
        gtConnectionManager?.removeGtConnectionListener(this)
    }

    override fun onHostResume() {
        Log.d("GoTennaModule", "~~ onHostResume")
        gtConnectionManager?.addGtConnectionListener(this)
    }

    override fun onIncomingMessage(gtBaseMessageData: GTBaseMessageData?) {
        if (gtBaseMessageData is GTTextOnlyMessageData) {
            val gtTextOnlyMessageData = gtBaseMessageData
            Log.d("GoTennaModule", "~~ Message was receivied "+gtTextOnlyMessageData.text);
        }
        else if(gtBaseMessageData is GTGroupCreationMessageData) {
            // TODO
        }
    }

    override fun onIncomingMessage(messageData: GTMessageData?) {
        // COMMENT FROM SAMPLE APP:
        // We do not send any custom formatted messages in this app,
        // but if you wanted to send out messages with your own format, this is where
        // you would receive those messages.
    }

}
