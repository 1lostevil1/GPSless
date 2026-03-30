package com.example.service.network;

import com.example.network.dto.GpsData;
import com.example.network.dto.NetworkSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
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

    /**
     * Валидация трека
     * @return true если трек валиден, false если нет
     */
    public boolean validateTrack(List<NetworkSnapshot> track) {
        if (track == null || track.isEmpty()) {
            return false;
        }

        // Проверяем что все снапшоты имеют GPS
        for (NetworkSnapshot snapshot : track) {
            if (snapshot.getLocation() == null) {
                log.debug("Снапшот без GPS");
                return false;
            }
        }

        // Если всего один снапшот - он валиден (админ, фон)
        if (track.size() == 1) {
            return true;
        }

        // Проверяем все сегменты между снапшотами
        for (int i = 1; i < track.size(); i++) {
            if (!isValidSegment(track.get(i - 1), track.get(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Проверка одного сегмента
     */
    private boolean isValidSegment(NetworkSnapshot prev, NetworkSnapshot curr) {
        long timeGapMs = Math.abs(Duration.between(prev.getSnapshotTime(), curr.getSnapshotTime()).toMillis());

        // Проверка временного разрыва
        if (timeGapMs > maxAllowedTimeGapMs) {
            log.debug("Превышен временной разрыв: {} ms", timeGapMs);
            return false;
        }

        // Расчет расстояния
        double distance = calculateDistance(prev.getLocation(), curr.getLocation());

        // Проверка скачка координат
        if (distance > maxCoordJumpMeters) {
            log.debug("Превышен скачок координат: {} м", distance);
            return false;
        }

        // Проверка скорости
        double timeGapHours = timeGapMs / 1000.0 / 3600.0;
        double speedKmh = distance / 1000.0 / timeGapHours;

        if (speedKmh > maxAllowedSpeedKmh) {
            log.debug("Превышена скорость: {} км/ч", speedKmh);
            return false;
        }

        return true;
    }

    /**
     * Расчет расстояния по формуле гаверсинусов
     */
    private double calculateDistance(GpsData a, GpsData b) {
        double lat1 = Math.toRadians(a.getLatitude());
        double lat2 = Math.toRadians(b.getLatitude());
        double deltaLat = Math.toRadians(b.getLatitude() - a.getLatitude());
        double deltaLon = Math.toRadians(b.getLongitude() - a.getLongitude());

        double hav = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        return 6371000 * 2 * Math.atan2(Math.sqrt(hav), Math.sqrt(1 - hav));
    }
}