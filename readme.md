
## Using the GoTennaSDK (iOS)

In addition to framework install from here https://github.com/gotenna/PublicSDK/tree/master/ios-public-sdk
I had to add the path to the framework to "Framework Search Paths" in Build Settings

-- OR -- 

Be sure to check "Copy items if needed" when dropping the framework into the project.

Then, since this react native module was developed with Swift we have to jump through a few extra hoops:
1. Create a group called "GoTennaSDK"
2. Inside that group create a file (Empty) called module.modulemap
3. Place the following in the file and update the path:

module GoTennaSDK [system] {
  header "/Users/aaronblondeau/Code/Mine/GoTennaReactNative/ios/GoTennaSDK.framework/Headers/GoTennaSDK.h"
  export *
}

4. In Build Settings > Import Paths, add the directory where this modulemap file resides.