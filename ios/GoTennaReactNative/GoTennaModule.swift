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
class goTenna: RCTEventEmitter, GTPairingHandlerProtocol, BluetoothPairingProtocol {
  
  var connectionManager = BluetoothConnectionManager.shared()
  var pairingManager = GTPairingManager.shared()
  var commandCenter = GTCommandCenter.shared()
  
  var connected = false
  
  @objc open override func supportedEvents() -> [String] {
    return ["pairBluetoothEnabled", "pairBluetoothDisabled", "pairScanBluetoothNotAvaialble", "pairScanLocationPermissionNeeded", "pairShouldRetry", "scanTimedOut", "pairScanStop", "pairScanStart", "configured", "disconnected", "connected"]
  }
  
  @objc func configure(_ apiKey: NSString, resolve:RCTPromiseResolveBlock, reject:RCTPromiseRejectBlock) -> Void {
    print("~~ Will configure with apiKey \(apiKey)")
    GoTenna.setApplicationToken(apiKey as String!)
    self.sendEvent(withName: "configured", body: [:])
    print("~~ Did configure with apiKey \(apiKey)")
    resolve([:])
  }
  
  @objc func startPairingScan(_ rememberDevice: ObjCBool, isMeshDevice: ObjCBool, resolve:RCTPromiseResolveBlock, reject:RCTPromiseRejectBlock) -> Void {
    print("~~ Would startPairingScan with rememberDevice \(rememberDevice) and isMeshDevice \(isMeshDevice)")
    
    connectionManager?.pairingDelegate = self
    if(isMeshDevice).boolValue {
      print("~~ scanning for mesh")
      connectionManager?.setDevice(GTDeviceType.mesh)
    }
    else {
      print("~~ scanning for V1")
      connectionManager?.setDevice(GTDeviceType.goTenna)
    }
    
    pairingManager?.pairingHandler = self
    pairingManager?.shouldReconnect = true
    pairingManager?.initiateScanningConnect()
    
    self.sendEvent(withName: "pairScanStart", body: [:])
    
    let when = DispatchTime.now() + 25 // change 2 to desired number of seconds
    DispatchQueue.main.asyncAfter(deadline: when) {
      if(!self.connected) {
        print("~~ Not connected after timeout!")
        self.pairingManager?.stopScanningConnect()
        self.sendEvent(withName: "scanTimedOut", body: [:])
        self.sendEvent(withName: "pairScanStop", body: [:])
      }
    }
    
  }
  
  @objc func getSystemInfo(_ resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) -> Void {
    print("~~ Would getSystemInfo")
    
    commandCenter?.sendGetSystemInfo(onSuccess: {(data) in
      
      resolve([
          "battery": data?.batteryLevel,
          "serial": data?.goTennaSerialNumber,
          "text": data?.asString()
      ])
      
    }) {(error) in
      
      reject("info_error", error?.localizedDescription, error)
      
    }
    
  }
  
  @objc func disconnect(_ resolve:RCTPromiseResolveBlock, reject:RCTPromiseRejectBlock) -> Void {
    print("~~ Would disconnect")
    pairingManager?.shouldReconnect = false
    pairingManager?.initiateDisconnect()
    
    // Disconnected event doesn't seem to appear in handler on iOS, so send it here.
    self.sendEvent(withName: "disconnected", body: [:])
  }
  
  
  @objc func echo(_ resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) -> Void {
    print("~~ Would echo")
    
    //commandCenter?.sendEchoCommand(<#T##onResponse: ((GTResponse?) -> Void)!##((GTResponse?) -> Void)!##(GTResponse?) -> Void#>, onError: <#T##((Error?) -> Void)!##((Error?) -> Void)!##(Error?) -> Void#>)
    
    commandCenter?.sendEchoCommand({(response) in
    
      if(response?.responseCode == GTResponsePositive) {
        resolve("positive")
      }
      else if(response?.responseCode == GTResponseNegative) {
        resolve("negative")
      }
      else {
        resolve("error")
      }
      
    }) {(error) in
      
      reject("echo_error", error?.localizedDescription, error)
      
    }
    
  }
  
  // GTPairingHandlerProtocol
  
  func update(_ state: GTConnectionState) {
    
    print("~~ update to GTConnectionState")
    
    switch state {
      case Disconnected :
        print("~~ Disconnected")
        self.sendEvent(withName: "pairScanStop", body: [:])
        self.sendEvent(withName: "disconnected", body: [:])
        connected = false
      
      case Connecting :
        print("~~ Connecting")
        self.sendEvent(withName: "pairScanStart", body: [:])
      
      case Connected :
        print("~~ Connected")
        self.sendEvent(withName: "pairScanStop", body: [:])
        self.sendEvent(withName: "connected", body: [:])
        connected = true
      
    default :
      
        print("~~ Got an unknown GTConnectionState")

    }
  }
  
  // BluetoothPairingProtocol
  
  func bluetoothPoweredOn() {
    print("~~ bluetoothPoweredOn")
  }
  
  func didConnectToPeripheral() {
    print("~~ didConnectToPeripheral")
  }
  
  func canNotConnectToPeripheral() {
    print("~~ canNotConnectToPeripheral")
    // TODO - custom iOS only event for this
  }
  
  @available(iOS 10.0, *)
  func bluetoothConnectionNotAvailable(_ state: CBManagerState) {
    print("~~ bluetoothConnectionNotAvailable")
    self.sendEvent(withName: "pairBluetoothDisabled", body: [:])
  }
  
  func nonUserDisconnectionOccurred() {
    print("~~ nonUserDisconnectionOccurred")
    self.sendEvent(withName: "disconnected", body: [:])
  }

}
