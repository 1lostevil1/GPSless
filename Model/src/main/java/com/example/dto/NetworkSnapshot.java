package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class NetworkSnapshot {
    private GpsData location;
    private CellularNetwork cellularNetwork;
    private List<WifiNetwork> wifiNetworks;
    private List<BluetoothDeviceInfo> bluetoothDevices;
}
