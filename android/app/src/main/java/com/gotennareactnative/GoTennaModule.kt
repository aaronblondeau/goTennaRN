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
import com.gotenna.sdk.bluetooth.BluetoothAdapterManager
import com.gotenna.sdk.bluetooth.GTConnectionManager
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.gotenna.sdk.GoTenna
import com.gotenna.sdk.commands.GTCommandCenter
import com.gotenna.sdk.commands.GTError
import com.gotenna.sdk.exceptions.GTInvalidAppTokenException
import com.gotenna.sdk.interfaces.GTErrorListener
import com.gotenna.sdk.responses.SystemInfoResponseData

/**
 * Created by aaronblondeau on 8/30/17.
 */

class GoTennaModule(reactContext: ReactApplicationContext?) : ReactContextBaseJavaModule(reactContext), GTConnectionManager.GTConnectionListener, LifecycleEventListener {

    private var willRememberGotenna = false

    private val SCAN_TIMEOUT = 25000 // 25 seconds
    private val BLUETOOTH_START_SCAN_DELAY = 500

    val handler = Handler(Looper.getMainLooper())
    var configured = false

    var bluetoothAdapterManager: BluetoothAdapterManager? = null
    var gtConnectionManager: GTConnectionManager? = null

    init {

        context = reactContext

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

        fun emitPairSuccess() {
            emit("pairSuccess")
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
            GoTenna.setApplicationToken(context?.getApplicationContext(), apiKey)

            bluetoothAdapterManager = BluetoothAdapterManager.getInstance()
            gtConnectionManager = GTConnectionManager.getInstance()

            gtConnectionManager?.addGtConnectionListener(this)

            configured = true
            emitConfigured()
            promise.resolve(true);
        } catch (e: GTInvalidAppTokenException) {
            Log.w("GoTennaModule", e)
            promise.reject("Failed to set application token", e)
        }
    }

    @ReactMethod
    fun startPairingScan(rememberDevice: Boolean, promise: Promise) {
        if(!configured) {
            return promise.reject("not_configured", "You must call configure (and wait till it completes) with an application token before using this module");
        }
        willRememberGotenna =  rememberDevice;
        startBluetoothPairingIfPossible()
        promise.resolve(true)
    }

    @ReactMethod
    fun getSystemInfo(promise: Promise) {
        if(!configured) {
            return promise.reject("not_configured", "You must call configure (and wait till it completes) with an application token before using this module");
        }
        GTCommandCenter.getInstance().sendGetSystemInfo(GTCommandCenter.GTSystemInfoResponseListener { systemInfoResponseData ->
            // This is where you could retrieve info such at the goTenna's battery level and current firmware version
            Toast.makeText(reactApplicationContext, systemInfoResponseData.toString(), Toast.LENGTH_LONG).show()
        }, object : GTErrorListener {
            override fun onError(error: GTError) {
                Log.w(javaClass.simpleName, error.toString())
            }
        })
        promise.resolve(true)
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

                    gtConnectionManager?.scanAndConnect()

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
                emitPairSuccess()
            }
            GTConnectionManager.GTConnectionState.SCANNING -> {
                Log.d("GoTennaModule", "~~ GTConnectonState SCANNING")
                emitPairScanStart()
            }
            GTConnectionManager.GTConnectionState.DISCONNECTED -> {
                Log.d("GoTennaModule", "~~ GTConnectonState DISCONNECTED")
                emitPairScanStop()
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

}
