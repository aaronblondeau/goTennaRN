//
//  GoTennaModule.swift
//  GoTennaReactNative
//
//  Created by Aaron Blondeau on 9/16/17.
//  Copyright © 2017 Facebook. All rights reserved.
//

import Foundation
import GoTennaSDK

@objc(goTenna)
class goTenna: RCTEventEmitter {
  
  @objc open override func supportedEvents() -> [String] {
    return ["pairBluetoothEnabled", "pairBluetoothDisabled", "pairScanBluetoothNotAvaialble", "pairScanLocationPermissionNeeded", "pairShouldRetry", "pairSuccess", "scanTimedOut", "pairScanStop", "pairScanStart", "configured"]
  }
  
  @objc func configure(_ apiKey: NSString, resolve:RCTPromiseResolveBlock, reject:RCTPromiseRejectBlock) -> Void {
    print("~~ Will configure with apiKey \(apiKey)")
    GoTenna.setApplicationToken(apiKey as String!)
    self.sendEvent(withName: "configured", body: [:])
    print("~~ Did configure with apiKey \(apiKey)")
    resolve([:])
  }
  
  @objc func startPairingScan(_ rememberDevice: ObjCBool, isMeshDevice: ObjCBool, resolve:RCTPromiseResolveBlock, reject:RCTPromiseRejectBlock) -> Void {
    print("~~ Would startPairingScan with rememberDevice \(rememberDevice) and rememberDevice \(rememberDevice)")
    self.sendEvent(withName: "pairScanStart", body: [:])
  }
  
  @objc func getSystemInfo(_ resolve:RCTPromiseResolveBlock, reject:RCTPromiseRejectBlock) -> Void {
    print("~~ Would configure with getSystemInfo")
  }
  
  @objc func disconnect(_ resolve:RCTPromiseResolveBlock, reject:RCTPromiseRejectBlock) -> Void {
    print("~~ Would disconnect")
  }
  
  @objc func echo(_ resolve:RCTPromiseResolveBlock, reject:RCTPromiseRejectBlock) -> Void {
    print("~~ Would echo")
  }

}
