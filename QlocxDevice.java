package com.iboxendriverapp;

import android.bluetooth.*;

import java.security.Key;
import java.sql.Time;
import java.util.ArrayList;
import android.util.Log;
import java.util.UUID;


public class QlocxDevice {
    private static UUID qlocxService = UUID.fromString("83130001-b1bb-48f5-88d9-7f27e1f16bfc");
    private static UUID qlocxCharWrite = UUID.fromString("83130002-b1bb-48f5-88d9-7f27e1f16bfc");
    private static UUID qlocxCharRead = UUID.fromString("83130003-b1bb-48f5-88d9-7f27e1f16bfc");
    private static UUID qlocxNotify = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private long millis;
    public BluetoothDevice bluetoothDevice;
    public byte[] sessionKey;
    private boolean isConnected;
    private SessionEncrypt sessionEncrypt;
    public BluetoothGatt gatt;
    public byte[] devicePublicKey;
    private byte[] asymmetricPublicKey;
    public byte[] payload;
    public byte[] deviceMessage;
    private byte[] deviceResponseMessage;
    public BluetoothGattCharacteristic writeCharacteristic;
    public BluetoothGattCharacteristic notifyCharacteristic;
    private BluetoothGattDescriptor gattDescriptor;
    public boolean isAwaitingResponse;
    public QlocxInterface.SequenceCallback sequenceCallback;

    private Logger logger;

    enum GATT_STATES { 
        IDLE,
        CONNECTED,
        DISCONNECTED,
        OWN_PUBLIC_KEY_SENT,
        SESSION_KEY_SET,
     }

    public GATT_STATES currentState;

    private long MILLIS_TIMEOUT = 10000;

    QlocxDevice(BluetoothDevice bluetoothDevice, Logger logger) {
        this.millis = System.currentTimeMillis();
        this.bluetoothDevice = bluetoothDevice;
        this.isConnected = false;
        this.logger = logger;
        this.sessionEncrypt = new SessionEncrypt();
    }

    private void log(String string) {
        logger.log("QlocxDevice", string+"");
    }

    public void resetMillis() {
        this.millis = System.currentTimeMillis();
    }

    public long getMillisDifference() {
        return System.currentTimeMillis() - this.millis;
    }

    public void setDeviceMessage(byte[] deviceMessage) {
        this.deviceMessage = deviceMessage;
        if(this.sessionKey != null) setPayload(this.deviceMessage);
    }

    public byte[] setDeviceResponse(byte[] data) {
        this.setPayload(null);
        
        this.deviceResponseMessage = this.sessionEncrypt.decryptMessage(this.sessionKey, data);
        return this.deviceResponseMessage;
    }

    public void setSessionKey(byte[] data) {
        log("sessionKey data: " + QlocxHelpers.bytesToHex(data));
        log("devicePublicKey: " + QlocxHelpers.bytesToHex(this.devicePublicKey));

        this.sessionKey = this.sessionEncrypt.generateSessionKey(data, this.devicePublicKey);
        this.currentState = GATT_STATES.SESSION_KEY_SET;
        setPayload(this.deviceMessage);

        log("CURRENT STATE: " + this.currentState);
    }

    public void setSequenceCallback(QlocxInterface.SequenceCallback callback) {
        this.sequenceCallback = callback;
    }

    public byte[] getSessionKey() {
        return this.sessionKey;
    }

    public void setDevicePublicKey(byte[] publicKey) {
        log("Got device public key: " + QlocxHelpers.bytesToHex(publicKey));
        this.devicePublicKey = publicKey;
    }

    public void transmitData(byte[] data) {
        this.writeCharacteristic.setValue(data);
        this.gatt.writeCharacteristic(this.writeCharacteristic);
    }

    public byte[] getPayload() {
        log("CURRENT STATE: " + this.currentState);
        switch(this.currentState) {
            case CONNECTED: return this.sessionEncrypt.getPublicKey();

            case SESSION_KEY_SET: return this.payload;

            case OWN_PUBLIC_KEY_SENT:
            case DISCONNECTED:
            case IDLE: break;
        }

        return null;
    }

    public byte[] setPayload(byte[] payload) {
        this.payload = payload;

        if(payload == null) return null;

        log("Payload to be transmitted: " + QlocxHelpers.bytesToHex(payload));

        if(this.sessionKey != null) {
            log("Encrypting payload ...");
            this.payload = sessionEncrypt.encryptMessage(this.sessionKey, payload);
        }

        return this.payload;
    }

    public void setGatt(BluetoothGatt gatt) {
        if(gatt != null) {
            this.gatt = gatt;
        }
    }

    public void disconnectGatt() {
        this.gatt.disconnect();
        this.gatt.close();
    }

    public void setIsConnected(boolean status) {
        if(status == true) {
            this.currentState = GATT_STATES.CONNECTED;
        } else {
            this.currentState = GATT_STATES.DISCONNECTED;
        }

        this.sessionKey = null;

        this.isConnected = status;
        log("CURRENT STATE: " + this.currentState);
    }

    public boolean isConnected() {
        return this.isConnected;
    }

    public void setServicesDiscovered(BluetoothGatt gatt) {
        BluetoothGattService service = gatt.getService(qlocxService);

        this.writeCharacteristic = service.getCharacteristic(qlocxCharWrite);
        BluetoothGattCharacteristic notifyCharac = service.getCharacteristic(qlocxCharRead);
        gatt.setCharacteristicNotification(notifyCharac, true);
        BluetoothGattDescriptor descriptor = notifyCharac.getDescriptor(qlocxNotify);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }


    public boolean hasExpired() {
        long currentMillis = System.currentTimeMillis();

        return currentMillis - this.millis > MILLIS_TIMEOUT;
    }

    public String toString() {
        return this.bluetoothDevice.getName();
    }
}