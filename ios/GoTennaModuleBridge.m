//
//  GoTennaModuleBridge.m
//  GoTennaReactNative
//
//  Created by Aaron Blondeau on 9/16/17.
//  Copyright © 2017 Facebook. All rights reserved.
//

#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(goTenna, NSObject)

//RCT_EXPORT_MODULE(goTenna);
RCT_EXTERN_METHOD(configure:(NSString)apiKey resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(startPairingScan:(BOOL)rememberDevice isMeshDevice:(BOOL)isMeshDevice resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(getSystemInfo:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(disconnect:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(echo:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(setGID:(nonnull NSNumber)gid name:(NSString)name resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)


@end
