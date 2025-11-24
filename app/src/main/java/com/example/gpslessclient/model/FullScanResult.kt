package com.example.gpslessclient.model

data class FullScanResult(
    val location: LocationData?,
    val cellularNetworks: List<CellularData>,
    val wifiNetworks: List<WifiData>,
    val bluetoothDevices: List<BluetoothData>,
    val timestamp: Long = System.currentTimeMillis()
)
