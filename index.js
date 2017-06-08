'use strict';

import { NativeModules, DeviceEventEmitter } from 'react-native';

let _registeredToEvents = false;
let _listeners = [];

let _registerToEvents = () => {
    if(!_registeredToEvents){
        NativeModules.ReactNativeNFC.getStartUpNfcData(_notifyListeners);
        DeviceEventEmitter.addListener('__NFC_DISCOVERED', _notifyListeners);
        _registeredToEvents = true;
    }
};

let _notifyListeners = (data) => {
    if(data){
        for(let i in _listeners){
            _listeners[i](data);
        }
    }
};

const NFC = {};

NFC.addListener = (callback) => {
    _listeners.push(callback);
    _registerToEvents();
};

NFC.removeListeners = () => {
    _listeners = [];
    _registeredToEvents = false;
    DeviceEventEmitter.removeAllListeners('__NFC_DISCOVERED')
};

export default NFC;