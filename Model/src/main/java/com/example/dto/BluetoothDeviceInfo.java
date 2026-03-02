package com.example.dto;

public record BluetoothDeviceInfo(
        String name,
        String address,
        int rssi,
        String deviceType
        ) {}
