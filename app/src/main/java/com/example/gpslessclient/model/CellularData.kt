package com.example.gpslessclient.model

data class CellularData(
    val type: String,
    val networkOperator: String,
    val cellId: Int?,
    val locationAreaCode: Int?,
    val mobileCountryCode: Int?,
    val mobileNetworkCode: Int?,
    override val signalStrength: Int,
    override val timestamp: Long = System.currentTimeMillis()
) : NetworkData() {
    // Вычисляем id на основе полей, которые уникально идентифицируют клетку
    override val id: String = "cell_${mobileCountryCode ?: "null"}_${mobileNetworkCode ?: "null"}_${cellId ?: "null"}"
}