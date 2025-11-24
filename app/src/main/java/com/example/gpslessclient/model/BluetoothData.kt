package com.example.gpslessclient.model

data class BluetoothBeacon(
    val name: String,
    val macAddress: String,
    val signalStrength: Int, // RSSI
    val deviceType: String
)