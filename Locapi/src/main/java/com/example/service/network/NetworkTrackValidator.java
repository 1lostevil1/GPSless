package com.example.service.network;

import com.example.network.dto.GpsData;
import com.example.network.dto.NetworkSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class NetworkTrackValidator {

    private final long maxAllowedTimeGapMs;
    private final double maxAllowedSpeedKmh;
    private final double maxCoordJumpMeters;

    public NetworkTrackValidator(
            @Value("${validator.max-time-gap-ms:300000}") long maxAllowedTimeGapMs,
            @Value("${validator.max-speed-kmh:300.0}") double maxAllowedSpeedKmh,
            @Value("${validator.max-coord-jump-meters:500.0}") double maxCoordJumpMeters) {
        this.maxAllowedTimeGapMs = maxAllowedTimeGapMs;
        this.maxAllowedSpeedKmh = maxAllowedSpeedKmh;
        this.maxCoordJumpMeters = maxCoordJumpMeters;
    }

    public boolean isValid(List<NetworkSnapshot> track) {
        if (track == null || track.size() < 2) {
            return true;
        }

        for (int i = 1; i < track.size(); i++) {
            NetworkSnapshot prev = track.get(i - 1);
            NetworkSnapshot curr = track.get(i);

            if (!isValidSegment(prev, curr)) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidSegment(NetworkSnapshot prev, NetworkSnapshot curr) {
        // 1. Проверка порядка времени
        if (curr.getSnapshotTime().isBefore(prev.getSnapshotTime()) ||
                curr.getSnapshotTime().equals(prev.getSnapshotTime())) {
            return false;
        }

        // 2. Проверка временного разрыва
        long timeGapMs = Duration.between(prev.getSnapshotTime(), curr.getSnapshotTime()).toMillis();
        if (timeGapMs > maxAllowedTimeGapMs) {
            return false;
        }

        // 3. Расчёт расстояния и скорости
        GpsData prevGps = prev.getLocation();
        GpsData currGps = curr.getLocation();

        double distanceMeters = calculateDistance(prevGps, currGps);
        double speedKmh = (distanceMeters * 3.6) / (timeGapMs / 1000.0);

        // 4. Проверка на резкий скачок
        if (distanceMeters > maxCoordJumpMeters && timeGapMs < 5000) {
            return false;
        }

        // 5. Проверка на превышение скорости
        return !(speedKmh > maxAllowedSpeedKmh);
    }

    private double calculateDistance(GpsData a, GpsData b) {
        final double R = 6371000; // Радиус Земли в метрах

        double lat1 = Math.toRadians(a.getLatitude());
        double lat2 = Math.toRadians(b.getLatitude());
        double deltaLat = Math.toRadians(b.getLatitude() - a.getLatitude());
        double deltaLon = Math.toRadians(b.getLongitude() - a.getLongitude());

        double hav = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(hav), Math.sqrt(1 - hav));

        return R * c;
    }
}