package com.example.gpslessclient.model

data class BluetoothDeviceInfo(
    val beaconId: String,   // уникальный идентификатор маяка (из payload)
    val name: String?,
    val address: String,    // можно хранить, но не считать уникальным
    val rssi: Int
)
