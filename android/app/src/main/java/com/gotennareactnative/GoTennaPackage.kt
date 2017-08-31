package com.gotennareactnative

import android.view.View
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ReactShadowNode
import com.facebook.react.uimanager.ViewManager
import java.util.*
import kotlin.collections.ArrayList

/**
 * Created by aaronblondeau on 8/30/17.
 */

class GoTennaPackage : ReactPackage {

    override fun createViewManagers(reactContext: ReactApplicationContext?): MutableList<ViewManager<View, ReactShadowNode>> {
        return Collections.emptyList();
    }

    override fun createNativeModules(reactContext: ReactApplicationContext?): MutableList<NativeModule> {
        var modules = ArrayList<NativeModule>()
        modules.add(GoTennaModule(reactContext))
        return modules;
    }

}