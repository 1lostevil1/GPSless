package com.example.gpslessclient.model

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val rssi: Int,
    val deviceType: String
)
