package com.example.network.dto;

public record BluetoothDeviceInfo(
        String name,
        String address,
        String beaconId,
        int rssi
        ) {}
