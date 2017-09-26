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
  
  let MIN_TLV = 2
  
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
    commandCenter?.onIncomingMessage = {(response) in

      let tlvSections : [TLVSection] = TLVSection.tlvSections(from: response?.commandData)
      if(tlvSections.count < self.MIN_TLV) {
        // Bad message
        return
      }
      
      let messageData : GTBaseMessageData = GTBaseMessageData.init(incoming: tlvSections, withSenderGID: response?.senderGID)
      
      switch messageData.messageType {
      case kMessageTypeTextOnly:
        print("~~ Got a text message")
        let msg = GTTextOnlyMessageData.init(fromOrderedData: tlvSections, withSenderGID: response?.senderGID)
        let gidType : GTGIDType = GIDManager.gidType(forGID: msg?.addresseeGID)
        switch gidType {
        case ShoutGID:
          print("~~ Text message is a shout : \(msg?.text)")
        case OneToOneGID:
          print("~~ Text message is a one to one message : \(msg?.text)")
        case GroupGID:
          print("~~ Text message is a group message : \(msg?.text)")
        default:
          print("~~ unknown GTGIDType!")
        }
        
      case kMessageTypeSetGroupGID:
        print("~~ Got a group message")
        GTGroupCreationMessageData.init(fromOrderedData: tlvSections, withSenderGID: response?.senderGID)
        
      case kMessageTypeFirmwarePublicKeyResponse:
        print("~~ Firmware public key response")
        let msg = GTPublicKeyFirmwareResponseMessageData.init(fromOrderedData: tlvSections, withSenderGID: response?.senderGID)
        let keyManager = PublicKeyManager.shared()
        keyManager?.addPublicKey(withGID: msg!.senderGID, publicKeyData: msg?.publicKey, userHasMyPublicKey: true)
        GTDecryptionErrorManager.shared().attemptToDecryptMessagesAgain()
        
      case kMessageTypeUserPublicKeyResponse:
        print("~~ Public key response")
        let msg = GTPublicKeyUserResponseMessageData.init(fromOrderedData: tlvSections, withSenderGID: response?.senderGID)
        let keyManager = PublicKeyManager.shared()
        keyManager?.addPublicKey(withGID: msg?.senderGID, publicKeyData: msg?.publicKey)
        
      case kMessageTypePublicKeyRequest:
        print("~~ Public key request")
        let msg = GTPublicKeyRequestMessageData.init(fromOrderedData: tlvSections, withSenderGID: response?.senderGID)
        self.commandCenter?.sendPublicKeyResponse(toGID: msg?.senderGID)
        GTDecryptionErrorManager.shared().attemptToDecryptMessagesAgain()
        
      case kMessageTypeMeshPublicKeyRequest:
        print("~~ Mesh public key request")
        let msg = GTMeshPublicKeyRequestMessageData.init(fromOrderedData: tlvSections, withSenderGID: response?.senderGID)
        let keyManager = PublicKeyManager.shared()
        keyManager?.addPublicKey(withGID: msg?.senderGID, publicKeyData: msg?.publicKey)
        self.commandCenter?.sendMyMeshPublicKey(toGID: msg?.senderGID)
        GTDecryptionErrorManager.shared().attemptToDecryptMessagesAgain()
        
      case kMessageTypeMeshPublicKeyResponse:
        print("~~ Mesh public key response")
        let msg = GTMeshPublicKeyResponseMessageData.init(fromOrderedData: tlvSections, withSenderGID: response?.senderGID)
        self.resendMessagesFromPreKeyExchange(msg: msg!)
        GTDecryptionErrorManager.shared().attemptToDecryptMessagesAgain()
        
      default:
        print("~~ Uknown message type")
      }
    }
  }
  
  func resendMessagesFromPreKeyExchange(msg: GTMeshPublicKeyResponseMessageData) {
    let senderGID = msg.senderGID
    let publicKey = msg.publicKey
    let keyManager = PublicKeyManager.shared()
    
    keyManager?.addPublicKey(withGID: senderGID, publicKeyData: publicKey)
    keyManager?.setPublicKeyStateWithGID(senderGID, userHasMyPublicKey: true)
    
    if(commandCenter?.hasPostKeyExchangeMeshMessagesToResend())! {
      return
    }
    
    var messagesToSend : [GTSendCommand] = [GTSendCommand]()
    
    let messagesA = commandCenter?.meshMessageToResendList
    if let countA = messagesA?.count {
      for i in 0..<countA {
        if let sendMessageCommand : GTSendCommand = messagesA?[i] as? GTSendCommand {
          if(sendMessageCommand.recipientGID == senderGID) {
            sendMessageCommand.invalidateTimeout()
            sendMessageCommand.responseReceived = true
            messagesToSend.append(sendMessageCommand)
          }
        }
      }
    }
    
    commandCenter?.meshMessageToResendList.removeObjects(in: messagesToSend)
    
    for sendMessageCommand : GTSendCommand in messagesToSend {
      commandCenter?.sendMessage(sendMessageCommand.outgoingData, toGID: sendMessageCommand.recipientGID, fromGID: sendMessageCommand.senderGID, onResponse: { (res) in
        sendMessageCommand.processResponse(res)
      }, onError: sendMessageCommand.onError)
    }
    
    let removedCommands : [GTSendCommand] = (commandCenter?.removedKeyExchangeMessagesRetrieval(withGID: senderGID))!
    
    for sendMessageCommand in removedCommands {
      commandCenter?.sendMessage(sendMessageCommand.outgoingData, toGID: sendMessageCommand.recipientGID, fromGID: sendMessageCommand.senderGID, onResponse: { (res) in
        sendMessageCommand.processResponse(res)
      }, onError: sendMessageCommand.onError)
    }
    
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
  
  @objc func sendOneToOneMessage(_ gid: NSNumber, text: NSString, resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) {
    
    let fromGID = UserDataStore.shared().currentUser().gId
    
    do {
      let messageData : GTTextOnlyMessageData = try GTTextOnlyMessageData.init(outgoingWithText: text as String!)
      
      commandCenter?.sendMessage(messageData.serializeToBytes(), toGID: gid, fromGID: fromGID, onResponse: { (res) in
        print("~~ sendOneToOneMessage - got response : \(res?.responsePositive())")
        resolve([:])
      }, onError: { (error) in
        print("~~ sendOneToOneMessage - got error : \(error.debugDescription)")
        reject("send_one_to_one_error", error?.localizedDescription, error)
      })
      
    }
    catch {
      return reject("send_one_to_one_error", error.localizedDescription, error)
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
  
  @objc func setGID(_ gid: NSNumber, name: NSString, resolve:RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) {
    var do_resolve = true;
    commandCenter?.setgoTennaGID(gid, withUsername: name as String!) {(error) in
      reject("echo_error", error?.localizedDescription, error)
      print("~~ Failed to set GID to \(gid), \(name)!")
      do_resolve = false;
    }
    if(do_resolve) {
      print("~~ Success setting GID \(gid), \(name)")
      resolve([:])
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
