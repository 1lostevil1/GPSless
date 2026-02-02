package com.example;

public record GpsData(
        double latitude,
        double longitude,
        float accuracy,
        float speed,
        float bearing,      // Направление движения (0 = север)
        double altitude,
        long timestamp
) {
}
