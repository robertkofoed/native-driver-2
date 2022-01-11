package com.iboxendriverapp;

import android.bluetooth.BluetoothDevice;

import java.sql.Time;
import java.util.ArrayList;
import android.util.Log;


public class QlocxBluetoothDevices {
    private ArrayList<QlocxDevice> devices;
    private Logger logger;

    QlocxBluetoothDevices(Logger logger) {
        this.logger = logger;
        this.devices = new ArrayList<QlocxDevice>();
    }

    public void clearDevices() {
        this.devices = new ArrayList<QlocxDevice>();
    }

    public void disconnectedAllDevices() {
        for(QlocxDevice device : this.devices) {
            logger.log("QlocxBluetoothDevices", "Closing " + device.toString());

            if (device.gatt != null) {
                device.gatt.disconnect();
                device.gatt.close();
            }
        }
    }

    public QlocxDevice AddDevice(BluetoothDevice device) {       
        QlocxDevice newDevice = new QlocxDevice(device, this.logger);
        this.devices.add(newDevice);

        logger.log("QlocxBluetoothDevices", "Adding " + newDevice.toString() + " to memory");

        return newDevice;
    }

    public QlocxDevice GetDevice(BluetoothDevice device) {
        for(QlocxDevice d : this.devices) {
            if(d.bluetoothDevice.getName().equals(device.getName())) {
                return d;
            }
        }

        return null;
    }

    public QlocxDevice GetDeviceByName(String peripheralName) {
        for(QlocxDevice d : this.devices) {
            if(d.bluetoothDevice.getName().equals(peripheralName)) {
                return d;
            }
        }

        return null;
    }

    public String toString() {
        return "Devices size: " + this.devices.size();
    }
}