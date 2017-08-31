import {observable, action, intercept, autorunAsync} from 'mobx';
import GoTenna from './modules/GoTenna';
import {
    AsyncStorage,
    Platform
  } from 'react-native';
import { DeviceEventEmitter } from 'react-native';
import config from './config';
import { PermissionsAndroid } from 'react-native';

class ApplicationState {
    @observable remember_paired_device = false;
    @observable scanning = false;

    @observable bluetooth_available = true;
    @observable bluetooth_enabled = true;

    @observable gotenna_configured = false;
    @observable pairing_timed_out = false;
    @observable paired = false;

    remember = async (value, key) => {
        if(value) {
            try {
                await AsyncStorage.setItem('@ApplicationState:'+key, value+"");
                console.log("~~ remembered "+key+" as "+value);
            } catch (error) {
                console.error(error.message);
            }
        }
    }

    @action getSystemInfo() {
        GoTenna.getSystemInfo()
        .then(() => {
            console.log("~~ getSystemInfo promise then")
        })
        .catch((error) => {
            alert("ERROR : "+error);
        });
    }

    @action scanForGoTennas() {
        this.pairing_timed_out = false;
        GoTenna.startPairingScan(this.remember_paired_device)
        .then(() => {
            // Nothing to do here - state is updated by events
        })
        .catch((error) => {
            alert("ERROR : "+error);
        });

    }

}
const state = new ApplicationState()

GoTenna.configure(config.goTennaApplicationKey)
.then(() => {
    console.log("~~ goTenna has been configured!")
    state.gotenna_configured = true;
})
.catch((error) => {
    console.error(error);
});

// Remember "remember_paired_device" state
autorunAsync(() => {
    state.remember(state.remember_paired_device, 'remember_paired_device');
}, 500);

// Restore remembered state
loadInitialState = async () => {
    
    properties = [
        {
            "key": "remember_paired_device",
            "postprocess": (value) => {return value == "true"}
        },
    ];

    for(var property of properties) {
        try {
            const value = await AsyncStorage.getItem('@ApplicationState:'+property.key);
            if (value){
                if(typeof property.postprocess === "function") {
                    state[property.key] = property.postprocess(value);
                }
                else {
                    state[property.key] = value;
                }
                //console.log("~~ loaded "+property.key+" as "+state[property.key]);
            }
        } catch (error) {
            console.error(error.message);
        }
    }

}
loadInitialState();

// Listen to events from GoTenna native module
DeviceEventEmitter.addListener('pairScanStart', function(e) {
    console.log("~~ event pairScanStart");
    state.scanning = true;
});

DeviceEventEmitter.addListener('pairScanStop', function(e) {
    console.log("~~ event pairScanStop");
    state.scanning = false;
});

DeviceEventEmitter.addListener('pairScanBluetoothNotAvaialble', function(e) {
    console.log("~~ event pairScanError");
    state.bluetooth_available = false;
});

DeviceEventEmitter.addListener('pairBluetoothEnabled', function(e) {
    console.log("~~ event pairBluetoothEnabled");
    state.bluetooth_enabled = true;
});

DeviceEventEmitter.addListener('pairShouldRetry', function(e) {
    console.log("~~ event pairShouldRetry");
    state.scanForGoTennas()
});

DeviceEventEmitter.addListener('pairBluetoothDisabled', function(e) {
    console.log("~~ event pairBluetoothDisabled");
    state.bluetooth_enabled = false;
});

DeviceEventEmitter.addListener('scanTimedOut', function(e) {
    console.log("~~ event scanTimedOut");
    state.pairing_timed_out = true;
});

DeviceEventEmitter.addListener('pairSuccess', function(e) {
    console.log("~~ event pairSuccess");
    state.paired = true;
});

DeviceEventEmitter.addListener('pairScanLocationPermissionNeeded', function(e) {
    console.log("~~ event pairScanLocationPermissionNeeded");
    if(Platform.OS === 'ios') {
        //TODO
    }
    else if(Platform.OS === 'android') {
        requestLocationPermissionForAndroid()
    }
});

async function requestLocationPermissionForAndroid() {
    console.log("~~ requestLocationPermissionForAndroid")
    try {
      const granted = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
        {
          'title': 'Location Permission',
          'message': 'GoTennaREactNative needs to access your location so it can find nearby devices.'
        }
      )
      if (granted === PermissionsAndroid.RESULTS.GRANTED) {
        console.log("~~ location permission granted")
        // Retry scan
        state.scanForGoTennas()
      } else {
        console.log("~~ location permission denied")
      }
    } catch (err) {
      console.warn(err)
    }
  }

export default state