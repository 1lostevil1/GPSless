package com.example.gpslessclient.model

sealed class NetworkData {
    abstract val id: String
    abstract val timestamp: Long
    abstract val signalStrength: Int
}