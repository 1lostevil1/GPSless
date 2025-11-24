package com.example.gpslessclient.model

import java.util.Locale

data class BluetoothData(
    val name: String,
    val macAddress: String,
    val deviceType: String,
    val bluetoothClass: Int? = null,
    override val signalStrength: Int,
    override val timestamp: Long = System.currentTimeMillis()
) : NetworkData() {
    override val id: String = "bt_${macAddress.replace(":", "").lowercase(Locale.ROOT)}"

    val deviceClass: String = parseDeviceClass()

    private fun parseDeviceClass(): String {
        return when {
            name.contains("phone", ignoreCase = true) -> "PHONE"
            name.contains("watch", ignoreCase = true) -> "WATCH"
            name.contains("headset", ignoreCase = true) -> "HEADSET"
            name.contains("car", ignoreCase = true) -> "CAR"
            deviceType.contains("BLE", ignoreCase = true) -> "BLE_BEACON"
            else -> "UNKNOWN"
        }
    }
}