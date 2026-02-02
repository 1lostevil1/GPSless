package com.example.service;

import com.example.GpsData;
import com.example.NetworkSnapshot;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class NetworkTrackValidator {

    // Пороговые значения (можно вынести в конфиг при необходимости)
    private static final long MAX_ALLOWED_TIME_GAP_MS = 30_000; // 30 сек
    private static final double MAX_ALLOWED_SPEED_KMH = 300.0;   // 300 км/ч
    private static final double MAX_COORD_JUMP_METERS = 500.0;   // 500 м

    /**
     * Проверяет трек на критические сбои.
     * @return true если трек валиден (нет разрывов/скачков), иначе false
     */
    public boolean isValid(List<NetworkSnapshot> track) {
        if (track == null || track.size() < 2) {
            return true; // Короткий трек считается валидным
        }

        for (int i = 1; i < track.size(); i++) {
            if (hasCriticalGap(track.get(i - 1), track.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Проверяет переход между двумя точками на наличие критических сбоев
     */
    private boolean hasCriticalGap(NetworkSnapshot prev, NetworkSnapshot curr) {
        GpsData prevGps = prev.location();
        GpsData currGps = curr.location();

        // 1. Проверка монотонности времени
        if (currGps.timestamp() <= prevGps.timestamp()) {
            return true; // Время не возрастает — критическая ошибка
        }

        // 2. Проверка временного разрыва
        long timeGapMs = currGps.timestamp() - prevGps.timestamp();
        if (timeGapMs > MAX_ALLOWED_TIME_GAP_MS) {
            return true; // Слишком большой разрыв во времени
        }

        // 3. Расчёт расстояния и скорости
        double distanceMeters = calculateDistanceMeters(prevGps, currGps);
        double speedKmh = (distanceMeters * 3.6) / (timeGapMs / 1000.0); // м/с → км/ч

        // 4. Проверка на резкий скачок координат
        if (distanceMeters > MAX_COORD_JUMP_METERS && timeGapMs < 5_000) {
            return true; // Слишком большой скачок за короткое время
        }

        // 5. Проверка на физически невозможную скорость
        if (speedKmh > MAX_ALLOWED_SPEED_KMH) {
            return true;
        }

        return false;
    }

    /**
     * Расчёт расстояния между точками по формуле Haversine (в метрах)
     */
    private double calculateDistanceMeters(GpsData a, GpsData b) {
        final double R = 6371000; // Радиус Земли в метрах

        double fi1 = Math.toRadians(a.latitude());
        double fi2 = Math.toRadians(b.latitude());
        double deltaFi = Math.toRadians(b.latitude() - a.latitude());
        double deltaLambda = Math.toRadians(b.longitude() - a.longitude());

        double haversine = Math.sin(deltaFi / 2) * Math.sin(deltaFi / 2) +
                Math.cos(fi1) * Math.cos(fi2) *
                        Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));

        return R * c;
    }
}