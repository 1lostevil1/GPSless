package com.example.gpslessclient.model

data class GpsData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float, // Направление движения (0 = север)
    val altitude: Double,
)