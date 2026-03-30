package com.example.gpslessclient.model

data class UserLocation(
    val lat: Double,
    val lon: Double,
    val timestamp: Long = System.currentTimeMillis()
)