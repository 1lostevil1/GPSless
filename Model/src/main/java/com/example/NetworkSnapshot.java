package com.example;

import java.util.List;

public record NetworkSnapshot(
        Long uuid,
        GpsData location,
        List<WifiNetwork> wifiNetworks,
        List<CellularNetwork> cellularNetworks,
        List<BluetoothDeviceInfo> bluetoothDevices
) {}
