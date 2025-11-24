package com.example.gpslessclient.model

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val signalStrength: Int, // dBm
    val frequency: Int,
    val capabilities: String
)