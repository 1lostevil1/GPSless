package com.example.service.network;

import com.example.network.dto.BluetoothDeviceInfo;
import com.example.network.dto.CellularNetwork;
import com.example.network.dto.NetworkSnapshot;
import com.example.network.dto.WifiNetwork;
import com.example.network.entity.ClusterEntity;
import com.example.network.entity.NetworkType;
import com.example.network.exception.LocationNotFoundException;
import com.example.network.util.ClusterKeyStrategy;
import com.example.quality.dto.QualityEvent;
import com.example.repository.ClusterRepository;
import com.github.davidmoten.geo.LatLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {


    private final NetworkKafkaProducer networkKafkaProducer;
    private final ClusterRepository clusterRepository;
    private final ClusterKeyStrategy keyStrategy;

    // Порог сигнала (слабые игнорируем)
    private static final int SIGNAL_THRESHOLD = -150;

    // Веса для отбора лучшего кластера (70% размер, 30% свежесть)
    private static final double SIZE_WEIGHT = 0.7;
    private static final double FRESHNESS_WEIGHT = 0.3;


    public LatLong determineLocation(NetworkSnapshot snapshot) {
        Set<String> keys = collectClusterKeys(snapshot);
        if (keys.isEmpty()) {
            throw new LocationNotFoundException("No networks in snapshot");
        }

        // Загружаем все кластеры по найденным ключам
        List<ClusterEntity> allClusters = clusterRepository.findByClusterKeyIn(new ArrayList<>(keys));

        // Группируем по ключу
        Map<String, List<ClusterEntity>> clustersByKey = allClusters.stream()
                .collect(Collectors.groupingBy(ClusterEntity::getClusterKey));

        log.info("найденные кластеры: {}",clustersByKey.toString());

        // Для каждого ключа выбираем лучший кластер (по размеру и свежести)
        Map<String, ClusterEntity> bestClusterByKey = new HashMap<>();
        for (Map.Entry<String, List<ClusterEntity>> entry : clustersByKey.entrySet()) {
            entry.getValue().stream()
                    .max(Comparator.comparingDouble(this::computeClusterScore))
                    .ifPresent(cluster -> bestClusterByKey.put(entry.getKey(), cluster));
        }

        log.info("найденные лучшие кластеры: {}",bestClusterByKey);

        // Собираем взвешенные точки от каждого типа сети
        List<WeightedPoint> weightedPoints = new ArrayList<>();

        // Wi-Fi
        if (snapshot.getWifiNetworks() != null) {
            for (WifiNetwork wifi : snapshot.getWifiNetworks()) {
                if (wifi.getSignalLevel() < SIGNAL_THRESHOLD) continue;
                String key = keyStrategy.generate(wifi);
                ClusterEntity cluster = bestClusterByKey.get(key);
                if (cluster != null) {
                    double weight = computeGeometricWeight(wifi.getSignalLevel(), NetworkType.WIFI);
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
                    double weight = computeGeometricWeight(cell.signalStrength(), NetworkType.CELLULAR);
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
                    double weight = computeGeometricWeight(bt.rssi(), NetworkType.BLUETOOTH);
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

        sendQualityEvent(avgLat, avgLon, snapshot, bestClusterByKey.values().stream().toList());

        return new LatLong(avgLat, avgLon);
    }


    /**
     * Оценка качества кластера (для выбора лучшего по ключу)
     */
    private double computeClusterScore(ClusterEntity cluster) {
        // Размер: логарифмическая шкала с насыщением при 100 000 сигналов
        // log10(100000) = 5, поэтому делим на 5
        double sizeFactor = Math.min(1.0, Math.log10(cluster.getSignalCount() + 1) / 5.0);

        long ageHours = Duration.between(cluster.getUpdatedAt(), LocalDateTime.now()).toHours();
        double freshnessFactor = Math.exp(-ageHours / (24.0 * 30));

        return SIZE_WEIGHT * sizeFactor + FRESHNESS_WEIGHT * freshnessFactor;
    }

    /**
     * Геометрический вес точки – определяется силой сигнала (близостью к кластеру)
     * и дополнительными факторами (радиус кластера, тип сети)
     */
    private double computeGeometricWeight(int signalStrength, NetworkType type) {
        // Нормализация сигнала dBm
        int minSignal = -150;
        int maxSignal = -10;
        int clamped = Math.max(signalStrength, minSignal);
        double signalFactor = (double) (clamped - minSignal) / (maxSignal - minSignal);

        // Типовой множитель (эмпирический)
        double typeFactor = switch (type) {
            case WIFI -> 1.0;
            case CELLULAR -> 0.3;    // соты менее точны
            case BLUETOOTH -> 1.5;    // маячки очень точны вблизи
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


    @Async
    public void sendQualityEvent(Double lat, Double lon, NetworkSnapshot snapshot, List<ClusterEntity> networks) {

        Set<String> existingKeys = networks.stream()
                .map(ClusterEntity::getClusterKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> snapshotKeys = collectClusterKeys(snapshot);

        long oldCount = snapshotKeys.stream().filter(existingKeys::contains).count();

        double quality = snapshotKeys.isEmpty() ? 0.0 : (double) oldCount / snapshotKeys.size();

        QualityEvent event = new  QualityEvent(lat,lon,quality,LocalDateTime.now());
        networkKafkaProducer.sendQuality(event);
    }

    private record WeightedPoint(double lat, double lon, double weight) {}
}