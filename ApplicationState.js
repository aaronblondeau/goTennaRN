import {observable, action, intercept, autorunAsync} from 'mobx';
import GoTenna from './modules/GoTenna';
import {
    AsyncStorage,
    Platform
  } from 'react-native';
import { NativeEventEmitter, DeviceEventEmitter } from 'react-native';
import config from './config';
import { PermissionsAndroid } from 'react-native';

class ApplicationState {
    @observable remember_paired_device = false;
    @observable is_mesh_device = false;
    @observable scanning = false;

    @observable bluetooth_available = true;
    @observable bluetooth_enabled = true;

    @observable gotenna_configured = false;
    @observable pairing_timed_out = false;
    @observable paired = false;

    @observable setting_gid = false;
    @observable gid_set = false;
    @observable gid = 0;
    @observable gid_name = "";

    @observable one_to_one_recipient_gid = 0;
    @observable one_to_one_message = "";

    remember = async (value, key) => {
        try {
            await AsyncStorage.setItem('@ApplicationState:'+key, value+"");
            console.log("~~ remembered "+key+" as "+value);
        } catch (error) {
            console.error(error.message);
        }
    }

    @action getSystemInfo() {
        GoTenna.getSystemInfo()
        .then((info) => {
            alert("System Info "+JSON.stringify(info))
        })
        .catch((error) => {
            alert("ERROR : "+error);
        });
    }

    @action scanForGoTennas() {
        this.pairing_timed_out = false;
        GoTenna.startPairingScan(this.remember_paired_device, this.is_mesh_device)
        .then(() => {
            // Nothing to do here - state is updated by events
        })
        .catch((error) => {
            alert("ERROR : "+error);
        });

    }

    @action setGID() {
        this.setting_gid = false;
        this.gid_set = false;
        if(this.gid && this.gid_name) {
            GoTenna.setGID(this.gid, this.gid_name)
            .then(() => {
                this.setting_gid = false;
                this.gid_set = true;
            })
            .catch((error) => {
                this.setting_gid = false;
                this.gid_set = false;
                alert("ERROR : "+error);
            });
        }
    }

    @action disconnect() {
        GoTenna.disconnect().then(() => {
            this.paired = false;
        })
        .catch((err) => {
            console.error(err);
        });
    }

    @action echo() {
        GoTenna.echo().then((result) => {
            alert(result);
        })
        .catch((err) => {
            console.error(err);
        });
    }

    @action sendOneToOne() {
        GoTenna.sendOneToOneMessage(this.one_to_one_recipient_gid, this.one_to_one_message).then(() => {
            console.log("~~ sendOneToOne resolved")
        })
        .catch((err) => {
            console.error(err);
        });
    }

    @action getState() {
        GoTenna.getState().then((state) => {
            console.log("~~ got state "+JSON.stringify(state));
            this.gid = state.gid;
            this.gid_name = state.gid_name;
            this.paired = state.paired;
        })
        .catch((err) => {
            console.error(err);
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
    console.error("Failed to configure GoTennaSDK!", error);
});

// Remember "remember_paired_device" state
autorunAsync(() => {
    state.remember(state.remember_paired_device, 'remember_paired_device');
}, 500);

// Remember "is_mesh_device" state
autorunAsync(() => {
    state.remember(state.is_mesh_device, 'is_mesh_device');
}, 500);

// Remember gid and gid_name state
autorunAsync(() => {
    state.remember(state.gid, 'gid');
    state.remember(state.gid_name, 'gid_name');
}, 500);

// Remember last one_to_one_recipient_gid
autorunAsync(() => {
    state.remember(state.one_to_one_recipient_gid, 'one_to_one_recipient_gid');
}, 500);

// Restore remembered state
loadInitialState = async () => {
    
    properties = [
        {
            "key": "remember_paired_device",
            "postprocess": (value) => {return value == "true"}
        },
        {
            "key": "is_mesh_device",
            "postprocess": (value) => {return value == "true"}
        },
        {
            "key": "gid",
            "postprocess": (value) => {
                var result = parseInt(value)
                if(Number.isNaN(result)) {
                    result = 1234;
                }
                return result
            }
        },
        {
            "key": "gid_name",
        },
        {
            "key": "one_to_one_recipient_gid",
        },
    ];

    for(var property of properties) {
        try {
            const value = await AsyncStorage.getItem('@ApplicationState:'+property.key);
            console.log("~~ inflated "+property.key+" as "+value)
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

    // Get updated info from native side too!
    state.getState();

}
loadInitialState();

// Works on Android, but not iOS
// const goTennaEventEmitter = DeviceEventEmitter;
const goTennaEventEmitter = new NativeEventEmitter(GoTenna);


// Listen to events from GoTenna native module
goTennaEventEmitter.addListener('pairScanStart', function(e) {
    console.log("~~ event pairScanStart");
    state.scanning = true;
});

goTennaEventEmitter.addListener('pairScanStop', function(e) {
    console.log("~~ event pairScanStop");
    state.scanning = false;
});

goTennaEventEmitter.addListener('pairScanBluetoothNotAvaialble', function(e) {
    console.log("~~ event pairScanError");
    state.bluetooth_available = false;
});

goTennaEventEmitter.addListener('pairBluetoothEnabled', function(e) {
    console.log("~~ event pairBluetoothEnabled");
    state.bluetooth_enabled = true;
});

goTennaEventEmitter.addListener('pairShouldRetry', function(e) {
    console.log("~~ event pairShouldRetry");
    state.scanForGoTennas()
});

goTennaEventEmitter.addListener('pairBluetoothDisabled', function(e) {
    console.log("~~ event pairBluetoothDisabled");
    state.bluetooth_enabled = false;
});

goTennaEventEmitter.addListener('scanTimedOut', function(e) {
    console.log("~~ event scanTimedOut");
    state.pairing_timed_out = true;
    state.scanning = false;
});

goTennaEventEmitter.addListener('connected', function(e) {
    console.log("~~ event connected");
    state.paired = true;
    state.scanning = false;
});

goTennaEventEmitter.addListener('disconnected', function(e) {
    console.log("~~ event disconnected");
    state.paired = false;
    state.scanning = false;
});

goTennaEventEmitter.addListener('pairScanLocationPermissionNeeded', function(e) {
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