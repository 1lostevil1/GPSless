package com.example.gpslessclient.model

data class CellularNetwork(
    val networkOperator: String,
    val signalStrength: Int, // dBm
    val cellId: Int?,
    val locationAreaCode: Int?,
    val mobileCountryCode: Int?,
    val mobileNetworkCode: Int?
)