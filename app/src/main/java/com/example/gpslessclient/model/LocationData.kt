package com.example.gpslessclient.model

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double?,
    val provider: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val signalStrength: Int = -1
) : NetworkData() {
    // Уникальный ID для локации на основе координат и времени
    override val id: String = "loc_${latitude}_${longitude}_${timestamp}"

    // Геохеш для удобного группирования близких точек
    val geoHash: String get() = generateGeoHash(latitude, longitude)

    private fun generateGeoHash(lat: Double, lon: Double): String {
        return "gh_${"%.6f".format(lat)}_${"%.6f".format(lon)}"
    }
}

