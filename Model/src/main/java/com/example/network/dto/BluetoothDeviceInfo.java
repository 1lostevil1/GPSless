package com.example.network.dto;

public record BluetoothDeviceInfo(
        String name,
        String address,
        String UUID,
        String major,
        String minor,
        int rssi
        ) {}
