package com.example.gpslessclient.model


data class CellularNetwork(
    val networkType: String, // 2G, 3G, 4G, 5G
    val signalStrength: Int, // dBm или ASU
    val mcc: String?,
    val mnc: String?,
    val cellId: Int? = null,
    val locationAreaCode: Int? = null
)