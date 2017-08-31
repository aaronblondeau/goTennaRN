package com.gotennareactnative;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.facebook.react.ReactActivity;

public class MainActivity extends ReactActivity {

    /**
     * Returns the name of the main component registered from JavaScript.
     * This is used to schedule rendering of the component.
     */
    @Override
    protected String getMainComponentName() {
        return "GoTennaReactNative";
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {

        Log.d("MainActivity", "~~ onActivityResult");

        // Callback for the alert asking the user to turn on their Bluetooth
        if (requestCode == GoTennaModule.Companion.getREQUEST_ENABLE_BT())
        {
            if (resultCode == Activity.RESULT_OK)
            {
                GoTennaModule.Companion.emitBluetoothEnabled();
                GoTennaModule.Companion.emitRetryPair();
            }
            else
            {
                GoTennaModule.Companion.emitBluetoothDisabled();
            }
        }
    }

}
