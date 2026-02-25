package com.example.gpslessclient.model

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val signalLevel: Int, // dBm
    val frequency: Int, // MHz
    val capabilities: String,
    val channel: Int,
    val isSecure: Boolean
)