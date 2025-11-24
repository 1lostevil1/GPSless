package com.example.gpslessclient.model

import androidx.compose.ui.text.toLowerCase
import java.util.Locale

data class WifiData(
    val ssid: String,
    val bssid: String,
    val frequency: Int,
    val capabilities: String,
    override val signalStrength: Int,
    override val timestamp: Long = System.currentTimeMillis()
) : NetworkData() {
    // BSSID уникален для Wi-Fi сети
    override val id: String = "wifi_${bssid.lowercase(Locale.ROOT)}"
}