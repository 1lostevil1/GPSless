package com.example.network.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class NetworkSnapshot {
    private LocalDateTime snapshotTime;
    private GpsData location;
    private CellularNetwork cellularNetwork;
    private List<WifiNetwork> wifiNetworks;
    private List<BluetoothDeviceInfo> bluetoothDevices;
}
