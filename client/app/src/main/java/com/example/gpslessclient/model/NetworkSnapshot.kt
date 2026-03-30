package com.example.gpslessclient.model


data class NetworkSnapshot(
    val snapshotTime: String?,
    val location: GpsData?,
    val wifiNetworks: List<WifiNetwork> = emptyList(),
    val cellularNetwork: CellularNetwork?,
    val bluetoothDevices: List<BluetoothDeviceInfo> = emptyList()
)