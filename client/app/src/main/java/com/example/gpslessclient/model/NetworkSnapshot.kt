package com.example.gpslessclient.model

data class NetworkSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val location: GpsData? = null,
    val wifiNetworks: List<WifiNetwork> = emptyList(),
    val cellularNetwork: CellularNetwork?,
    val bluetoothDevices: List<BluetoothDeviceInfo> = emptyList()
)