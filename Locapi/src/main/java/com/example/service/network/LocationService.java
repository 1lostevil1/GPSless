package com.example.service.network;

import com.example.network.dto.BluetoothDeviceInfo;
import com.example.network.dto.CellularNetwork;
import com.example.network.dto.NetworkSnapshot;
import com.example.network.dto.WifiNetwork;
import com.example.network.entity.ClusterEntity;
import com.example.network.entity.NetworkType;
import com.example.network.exception.LocationNotFoundException;
import com.example.network.util.ClusterKeyStrategy;
import com.example.repository.ClusterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final ClusterRepository clusterRepository;
    private final ClusterKeyStrategy keyStrategy;

    // Порог сигнала (слабые игнорируем)
    private static final int SIGNAL_THRESHOLD = -150;

    // Веса для отбора лучшего кластера (70% размер, 30% свежесть)
    private static final double SIZE_WEIGHT = 0.7;
    private static final double FRESHNESS_WEIGHT = 0.3;

    public Point determineLocation(NetworkSnapshot snapshot) {
        Set<String> keys = collectClusterKeys(snapshot);
        if (keys.isEmpty()) {
            throw new LocationNotFoundException("No networks in snapshot");
        }

        // Загружаем все кластеры по найденным ключам
        List<ClusterEntity> allClusters = clusterRepository.findByClusterKeyIn(new ArrayList<>(keys));

        // Группируем по ключу
        Map<String, List<ClusterEntity>> clustersByKey = allClusters.stream()
                .collect(Collectors.groupingBy(ClusterEntity::getClusterKey));

        // Для каждого ключа выбираем лучший кластер (по размеру и свежести)
        Map<String, ClusterEntity> bestClusterByKey = new HashMap<>();
        for (Map.Entry<String, List<ClusterEntity>> entry : clustersByKey.entrySet()) {
            entry.getValue().stream()
                    .max(Comparator.comparingDouble(this::computeClusterScore))
                    .ifPresent(cluster -> bestClusterByKey.put(entry.getKey(), cluster));
        }

        // Собираем взвешенные точки от каждого типа сети
        List<WeightedPoint> weightedPoints = new ArrayList<>();

        // Wi-Fi
        if (snapshot.getWifiNetworks() != null) {
            for (WifiNetwork wifi : snapshot.getWifiNetworks()) {
                if (wifi.getSignalLevel() < SIGNAL_THRESHOLD) continue;
                String key = keyStrategy.generate(wifi);
                ClusterEntity cluster = bestClusterByKey.get(key);
                if (cluster != null) {
                    double weight = computeGeometricWeight(wifi.getSignalLevel(), cluster, NetworkType.WIFI);
                    weightedPoints.add(new WeightedPoint(cluster.getLatitude(), cluster.getLongitude(), weight));
                }
            }
        }

        // Сотовая
        if (snapshot.getCellularNetwork() != null) {
            CellularNetwork cell = snapshot.getCellularNetwork();
            if (cell.signalStrength() >= SIGNAL_THRESHOLD) {
                String key = keyStrategy.generate(cell);
                ClusterEntity cluster = bestClusterByKey.get(key);
                if (cluster != null) {
                    double weight = computeGeometricWeight(cell.signalStrength(), cluster, NetworkType.CELLULAR);
                    weightedPoints.add(new WeightedPoint(cluster.getLatitude(), cluster.getLongitude(), weight));
                }
            }
        }

        // Bluetooth
        if (snapshot.getBluetoothDevices() != null) {
            for (BluetoothDeviceInfo bt : snapshot.getBluetoothDevices()) {
                if (bt.rssi() < SIGNAL_THRESHOLD) continue;
                String key = keyStrategy.generate(bt);
                ClusterEntity cluster = bestClusterByKey.get(key);
                if (cluster != null) {
                    double weight = computeGeometricWeight(bt.rssi(), cluster, NetworkType.BLUETOOTH);
                    weightedPoints.add(new WeightedPoint(cluster.getLatitude(), cluster.getLongitude(), weight));
                }
            }
        }

        if (weightedPoints.isEmpty()) {
            throw new LocationNotFoundException("No known clusters with sufficient signal strength");
        }

        // Взвешенное среднее (точка равновесия)
        double totalWeight = weightedPoints.stream().mapToDouble(WeightedPoint::weight).sum();
        double avgLat = weightedPoints.stream().mapToDouble(p -> p.lat * p.weight).sum() / totalWeight;
        double avgLon = weightedPoints.stream().mapToDouble(p -> p.lon * p.weight).sum() / totalWeight;

        return new Point(avgLat, avgLon);
    }

    /**
     * Оценка качества кластера (для выбора лучшего по ключу)
     * Комбинация размера (log5) и свежести (экспоненциальное убывание)
     */
    private double computeClusterScore(ClusterEntity cluster) {
        // Размер: log5(n+1)/3 (от 0 до 1, насыщение при 124)
        double sizeFactor = Math.min(1.0, Math.log(cluster.getSignalCount() + 1) / Math.log(5) / 3.0);

        // Свежесть: экспоненциальное убывание с периодом 30 дней
        long ageHours = Duration.between(cluster.getUpdatedAt(), LocalDateTime.now()).toHours();
        double freshnessFactor = Math.exp(-ageHours / (24.0 * 30));

        return SIZE_WEIGHT * sizeFactor + FRESHNESS_WEIGHT * freshnessFactor;
    }

    /**
     * Геометрический вес точки – определяется силой сигнала (близостью к кластеру)
     * и дополнительными факторами (радиус кластера, тип сети)
     */
    private double computeGeometricWeight(int signalStrength, ClusterEntity cluster, NetworkType type) {
        // Нормализация сигнала dBm (-120..-30 -> 0..1)
        int minSignal = -150;
        int maxSignal = -10;
        int clamped = Math.max(signalStrength, minSignal);
        double signalFactor = (double) (clamped - minSignal) / (maxSignal - minSignal);

        // Типовой множитель (эмпирический)
        double typeFactor = switch (type) {
            case WIFI -> 1.0;
            case CELLULAR -> 0.3;    // соты менее точны
            case BLUETOOTH -> 1.5;    // маячки очень точны вблизи
            default -> 0.5;
        };

        return signalFactor * typeFactor;
    }

    /**
     * Собирает все clusterKey из снапшота
     */
    private Set<String> collectClusterKeys(NetworkSnapshot snapshot) {
        Set<String> keys = new HashSet<>();

        if (snapshot.getWifiNetworks() != null) {
            snapshot.getWifiNetworks().stream()
                    .map(keyStrategy::generate)
                    .filter(Objects::nonNull)
                    .forEach(keys::add);
        }

        if (snapshot.getCellularNetwork() != null) {
            keys.add(keyStrategy.generate(snapshot.getCellularNetwork()));
        }

        if (snapshot.getBluetoothDevices() != null) {
            snapshot.getBluetoothDevices().stream()
                    .map(keyStrategy::generate)
                    .forEach(keys::add);
        }

        return keys;
    }

    private record WeightedPoint(double lat, double lon, double weight) {}

    public record Point(double lat, double lon) {}
}