package com.iboxendriverapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Handler;
import android.util.Log;
import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import io.sentry.Breadcrumb;

import com.facebook.react.bridge.Promise;
import com.iboxendriverapp.QlocxDevice.GATT_STATES;

public class QlocxInterface {
    private BluetoothAdapter btAdapter;
    private BluetoothManager btManager;
    private BluetoothGatt mGatt;
    private BluetoothGattCharacteristic mCharacteristic;
    private ArrayList<ScanFilter> filterList;
    private ScanSettings settings;
    private BluetoothLeScanner scanner;
    private BluetoothDevice mDevice;
    private ByteArrayOutputStream strm;
    private String mPayload;
    private int pos;
    private Boolean hasSentResponse;
    private SessionEncrypt SE;
    private String deviceName;
    private Callback actionCallback;
    private SequenceCallback mSequenceCallback;
    private Boolean deviceFound = false;
    private Boolean isRunning = false;
    private String action;
    private int mScanTime;
    private final QlocxHelpers qlocxHelpers;
    private com.facebook.react.bridge.Promise rPromise;
    private String peripheralName = null;
    private ConnectedCallback connectedCallback;
    private boolean isConnected = false;
    private boolean hasExchangedKeys = false;
    private QlocxBluetoothDevices qlocxBTDevices;
    private SDKErrorsCallback mSDKErrorsCallback;
    private Logger mLogger;
    private ArrayList<BluetoothDevice> scannedDevices;


    //BLUETOOTH GATT

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            QlocxDevice qlocxDevice = qlocxBTDevices.GetDevice(gatt.getDevice());

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                
                qlocxDevice.setIsConnected(true);
                qlocxDevice.setGatt(gatt);

                gatt.discoverServices();

                // try {
                //     Thread.sleep(1000);
                // } catch(Exception e) {}
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                qlocxDevice.setIsConnected(false);
                mLogger.log("BLE", "Disconnected from: " + qlocxDevice.toString());

                if (qlocxDevice.sessionKey == null) {
                    mLogger.log("BLE", "Något gick fel.... avanslöts");
                    mSDKErrorsCallback.onError("Premature disconnect");
                    if(connectedCallback != null) {
                        connectedCallback.result(false);
                    }
                }
                qlocxDevice.disconnectGatt();
            }
        }


        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();

            try {
                strm.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (data.length != 0) return;

            QlocxDevice qlocxDevice = qlocxBTDevices.GetDevice(gatt.getDevice());

            receivedData(qlocxDevice, strm.toByteArray());
        }

        @Override
        public void onDescriptorWrite(final BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            QlocxDevice qlocxDevice = qlocxBTDevices.GetDevice(gatt.getDevice());

            mLogger.log("BLE", "Wrote descriptor for " + qlocxDevice.toString());

            if (connectedCallback != null) {
                connectedCallback.result(true);
            }

            if (qlocxDevice.deviceMessage != null) {
                sendArraySliced(qlocxDevice);
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            QlocxDevice qlocxDevice = qlocxBTDevices.GetDevice(gatt.getDevice());
            
            mLogger.log("BLE", "Discovered services for " + qlocxDevice.toString());

            qlocxDevice.setServicesDiscovered(gatt);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            QlocxDevice qlocxDevice = qlocxBTDevices.GetDevice(gatt.getDevice());
            if (qlocxDevice.isAwaitingResponse) return;

            sendArraySliced(qlocxDevice);
        }
    };
    private ScanCallback peripheralScannerCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result.getDevice().getName() != null) {
                mLogger.log("BLE", result.getDevice().getName() + " RSSI:" + result.getRssi());
                
                if (result.getDevice().getName().equals(deviceName)) {
                    scanner.stopScan(peripheralScannerCallback);
                    
                    deviceFound = true;
                    
                    connectDeviceGatt(qlocxBTDevices.GetDevice(result.getDevice()));
                    return;
                }
            }
        }
    };

    private void connectDeviceGatt(QlocxDevice qlocxDevice) {
        reset();

        // if(qlocxDevice.isConnected()) {
        //     mLogger.log("BLE", "Already connected to " + qlocxDevice.toString());
        //     qlocxDevice.gatt.discoverServices();
        // } else {
            mLogger.log("BLE", "Connecting to device gatt " + qlocxDevice.bluetoothDevice.getName());
            qlocxDevice.bluetoothDevice.connectGatt(null, false, mGattCallback);
        // }
    }


    // INTERFACE CLASS

    public QlocxInterface(BluetoothAdapter btAdapter, BluetoothManager btManager, Logger logger) {
        this.btAdapter = btAdapter;
        this.btManager = btManager;
        this.filterList = new ArrayList<>();
        this.settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
        this.scanner = btAdapter.getBluetoothLeScanner();
        this.qlocxHelpers = new QlocxHelpers();
        this.qlocxBTDevices = new QlocxBluetoothDevices(logger);
        this.mLogger = logger;
        this.scannedDevices = new ArrayList();

        release();
    }

    // API

    public void subscribeForErrors(SDKErrorsCallback callback) {
        this.mSDKErrorsCallback = callback;
    }

    public boolean isConnected(String peripheralName) {
        for(BluetoothDevice b : btManager.getConnectedDevices(BluetoothProfile.GATT)) {
            mLogger.log("BLE", "connectad till: " + b.getName()+"");
            if(b.getName().length() == 8 && b.getName().equals(peripheralName)) {
              return true;
            }
        }

        return false;
    }

    private boolean isConnected() {
        for(BluetoothDevice d : btManager.getConnectedDevices(BluetoothProfile.GATT)){
            mLogger.log("BLE", "connected device " + d.getName());
        }
        return btManager.getConnectedDevices(BluetoothProfile.GATT).size() > 0;
    }

    public void establishConnection(String peripheralName, ConnectedCallback cb) {
        this.connectedCallback = cb;
        mLogger.log("BLE", "Attempting establish connection to " + peripheralName);
        this.mScanTime = 1500;

        QlocxDevice qlocxDevice = qlocxBTDevices.GetDeviceByName(peripheralName);
        
        if(qlocxDevice != null) {
            mLogger.log("BLE", "Device already saved in memory, " + qlocxDevice.toString());
            if(qlocxDevice.isConnected()) {
                cb.result(true);
                return;
            }
            
            connectDeviceGatt(qlocxDevice);
            return;
        } else {
            mLogger.log("BLE", "Could not find peripheralName in memory: " + peripheralName);
        }

        boolean didFindPeripheral = false;

        GetPeripherals(new PeripheralScanCallback() {
            @Override
            public void result(ArrayList<String> deviceNames) {
                if(!didFindPeripheral) {
                    cb.result(false);
                    mSDKErrorsCallback.onError("Cannot find peripheral " + peripheralName + ", try again");
                }
            }

            @Override
            public void deviceResult(ArrayList<BluetoothDevice> devices) {
                for(BluetoothDevice device : devices) {
                    mLogger.log("BLE", device.getName() + " "+ peripheralName);
                    if(device.getName() != null && device.getName().equals(peripheralName)){
                        connectDeviceGatt(qlocxBTDevices.AddDevice(device));
                    }
                }
            }
        });
    }

    public void sendSequencePayload(String payload, int scanTime, com.facebook.react.bridge.Promise promise, final int attempt, SequenceCallback callback) {
        String[] parts = payload.split("\\.");
        deviceName = parts[0].substring(0, 8);
        
        mLogger.log("BLE", "PAYLOAD: " + payload);
        
        this.rPromise = promise;
        
        isRunning = true;
        
        mScanTime = scanTime;
        
        QlocxDevice qlocxDevice = qlocxBTDevices.GetDeviceByName(deviceName);
        if(qlocxDevice == null) {
            mLogger.log("BLE", "Could not find peripheral! " + deviceName + " attempts: "+ String.valueOf(attempt));
            
            if (attempt < 1) {
                mLogger.log("BLE", "Attempting to scan for it");

                establishConnection(deviceName, new ConnectedCallback() {
                    @Override
                    public void result(boolean isConnected) {
                        if(isConnected) {
                            sendSequencePayload(payload, scanTime, promise, attempt+1, callback);
                        }
                    }
                });
            }
            return;
        }

        qlocxDevice.setSequenceCallback(callback);
        qlocxDevice.setDevicePublicKey(qlocxHelpers.hexStringToByteArray(parts[0]));
        qlocxDevice.setDeviceMessage(qlocxHelpers.hexStringToByteArray(parts[1]));

        if (qlocxDevice.isConnected()) {
            sendArraySliced(qlocxDevice);
        } else {
            // SHOULD ALREADY BE CONNECTED BUT ...
            mLogger.log("BLE sendSequencePayload", "Connecting to " + deviceName + " isConnected: " + String.valueOf(qlocxDevice.isConnected()));
            connect(qlocxDevice);
        }
    }

    public void GetPeripherals(PeripheralScanCallback callback) {
        ArrayList<String> deviceNames = new ArrayList();
        this.scannedDevices = new ArrayList();
        qlocxBTDevices.clearDevices();

                ScanCallback GetPeripheralsScanCallback = new ScanCallback() {
                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        String deviceName = result.getDevice().getName();

                        mLogger.log("BLE", "Scanned peripheral: " + deviceName);
                        
                        if(deviceName != null) {
                            if(deviceName.length() == 8) {
                                if(!deviceNames.contains(deviceName)) {
                                    mLogger.log("BLE", "Found Qlocx device " + deviceName + ", signal strength: " + result.getRssi());
                                    qlocxBTDevices.AddDevice(result.getDevice());
                                    scannedDevices.add(result.getDevice());
                                    deviceNames.add(deviceName);
                                }
                            }
                        }
                    }
                };
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        scanner.stopScan(GetPeripheralsScanCallback);
        
                        callback.result(deviceNames);
                        callback.deviceResult(scannedDevices);
        
                    }
                }, 2000);
                
                mLogger.log("BLE", "Starting peripheral scan");
                scanner.startScan(filterList, settings, GetPeripheralsScanCallback);
    }

    public void cancel() {
        if (scanner != null) scanner.stopScan(peripheralScannerCallback);

        release();
    }


    // PRIVATE METHODS

    private void connect(QlocxDevice qlocxDevice) {
        if(qlocxDevice != null){
            qlocxDevice.bluetoothDevice.connectGatt(null, false, mGattCallback);
            return;
        } 

        mLogger.log("BLE", this.qlocxBTDevices.toString());

        scanner.startScan(filterList, settings, peripheralScannerCallback);
        mLogger.log("BLE", "Scannar i ms: " + mScanTime);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                scanner.stopScan(peripheralScannerCallback);
                if (!deviceFound) {
                    if (mSequenceCallback != null) {
                        mSequenceCallback.error(new com.iboxendriverapp.QlocxException("Peripheral not found"), rPromise);
                    }

                    release();
                }
            }
        }, this.mScanTime);
    }

    private void release() {
        mLogger.log("BLE", "Avslutar bluetooth-anslutning");
        mSequenceCallback = null;
        isRunning = false;
        mPayload = null;
        isConnected = false;
        deviceFound = false;
        hasExchangedKeys = false;
        this.peripheralName = null;
        this.connectedCallback = null;

        qlocxBTDevices.disconnectedAllDevices();
    }

    private void reset() {
        SE = null;
        pos = 0;
        strm = new ByteArrayOutputStream();
        mDevice = null;
        deviceFound = false;
        hasSentResponse = false;
        isRunning = false;
    }

    private void receivedData(QlocxDevice qlocxDevice, byte[] data) {
        mLogger.log("BLE", "Tagit emot data från device " + qlocxDevice.toString());

        if (qlocxDevice.getSessionKey() == null) {
            mLogger.log("BLE", "Creating session key for " + qlocxDevice.toString());
            qlocxDevice.setSessionKey(data);

            sendArraySliced(qlocxDevice);
        } else {
            mLogger.log("BLE", "Det var en respons från kortet, storlek: " + data.length);
            if (data.length == 0) return;

            mLogger.log("BLE", "Kortet säger att den är klar");

            byte[] response = qlocxDevice.setDeviceResponse(data);

            mLogger.log("BLE", "Skickar sequence callback");
            qlocxDevice.sequenceCallback.result(response, rPromise);
        }
    }

    private void sendArraySliced(QlocxDevice qlocxDevice) {
        byte[] payload = qlocxDevice.getPayload();

        strm = new ByteArrayOutputStream();

        int size = 20;
        if (payload == null) {
            mLogger.log("BLE", "payload empty, returning");
            return;
        }

        if (pos > payload.length) {
            mLogger.log("BLE", "Skickat färdigt, skickar en tom frame och väntar på svar");
            pos = 0;
            byte[] e = {};

            if (qlocxDevice.currentState == GATT_STATES.CONNECTED) {
                qlocxDevice.currentState = GATT_STATES.OWN_PUBLIC_KEY_SENT;
            }

            qlocxDevice.isAwaitingResponse = true;
            qlocxDevice.transmitData(e);
            return;
        }

        
        byte[] partArr = Arrays.copyOfRange(payload, pos, pos + size > payload.length ? payload.length : pos + 20);
        pos += size;
        
        
        mLogger.log("BLE", "Transmitting " + partArr.length + " bytes");
        
        qlocxDevice.isAwaitingResponse = false;
        qlocxDevice.transmitData(partArr);
    }

    // CALLBACK INTERFACES

    public interface SequenceCallback {
        void result(byte[] result, com.facebook.react.bridge.Promise promise);

        void error(com.iboxendriverapp.QlocxException exception, com.facebook.react.bridge.Promise promise);
    }

    public interface Callback {
        void openResult(byte[] result);

        void senseResult(byte[] payload, boolean isOpen);

        void sendPayloadResult(byte[] result);

        void error(com.iboxendriverapp.QlocxException e, com.facebook.react.bridge.Promise promise);
    }

    public interface PeripheralScanCallback {
        void result(ArrayList<String> deviceNames);
        void deviceResult(ArrayList<BluetoothDevice> devices);
    }

    public interface ConnectedCallback {
        void result(boolean isConnected);
    }

    public interface SDKErrorsCallback {
        void onError(String message);
    }
}
