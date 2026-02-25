package com.example;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class NetworkSnapshot {
    private GpsData location;
    private List<WifiNetwork> wifiNetworks;
    private List<CellularNetwork> cellularNetworks;
    private List<BluetoothDeviceInfo> bluetoothDevices;
}
