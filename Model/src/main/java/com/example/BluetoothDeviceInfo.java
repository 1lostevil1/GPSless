package com.example;

public record BluetoothDeviceInfo(
        String name,
        String address,
        int rssi,
        String deviceType
        ) {}
