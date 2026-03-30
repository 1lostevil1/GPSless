package com.example.service;

import com.example.network.entity.ClusterEntity;
import com.example.network.entity.NetworkEntity;
import com.example.network.entity.Status;
import com.example.repository.NetworkRepository;
import com.example.user.entity.Role;
import com.github.davidmoten.geo.LatLong;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ClusterMathService {

    private final NetworkRepository networkRepository;

    public ClusterMathService(NetworkRepository networkRepository) {
        this.networkRepository = networkRepository;
    }

    @Value
    private static class Stats {
        double meanLat, meanLon, sigmaLat, sigmaLon;
    }

    /**
     * Обновляет поля кластера на основе новых сетей (и старых из БД)
     */
    public void enrichCluster(ClusterEntity cluster, List<NetworkEntity> newNetworks) {
        List<NetworkEntity> allNetworks = loadAllNetworks(cluster.getClusterKey(), cluster.getGeohash(), newNetworks);
        if (allNetworks.isEmpty()) return;

        Stats stats = calculateStats(allNetworks);
        List<NetworkEntity> goodNetworks = filterAndMarkOutliers(allNetworks, stats);

        if (goodNetworks.isEmpty()) {
            log.warn("No good networks for cluster {} with geohash {}",
                    cluster.getClusterKey(), cluster.getGeohash());
            return;
        }

        updateClusterFromNetworks(cluster, goodNetworks);
    }

    private List<NetworkEntity> loadAllNetworks(String clusterKey, String geohash,
                                                List<NetworkEntity> newNetworks) {
        List<NetworkEntity> old = networkRepository
                .findAllByClusterKeyAndGeohashAndStatus(clusterKey, geohash, Status.DONE);
        List<NetworkEntity> all = new ArrayList<>(old);
        all.addAll(newNetworks);
        return all;
    }

    private Stats calculateStats(List<NetworkEntity> networks) {
        double meanLat = networks.stream()
                .mapToDouble(NetworkEntity::getLatitude)
                .average()
                .orElse(0);

        double meanLon = networks.stream()
                .mapToDouble(NetworkEntity::getLongitude)
                .average()
                .orElse(0);

        double sigmaLat = Math.sqrt(networks.stream()
                .mapToDouble(n -> Math.pow(n.getLatitude() - meanLat, 2))
                .average()
                .orElse(0));

        double sigmaLon = Math.sqrt(networks.stream()
                .mapToDouble(n -> Math.pow(n.getLongitude() - meanLon, 2))
                .average()
                .orElse(0));

        return new Stats(meanLat, meanLon, sigmaLat, sigmaLon);
    }

    private List<NetworkEntity> filterAndMarkOutliers(List<NetworkEntity> networks, Stats stats) {
        List<NetworkEntity> good = networks.stream()
                .filter(n -> isWithinSigma(n, stats))
                .collect(Collectors.toList());

        List<Long> badIds = networks.stream()
                .filter(n -> !isWithinSigma(n, stats))
                .map(NetworkEntity::getId)
                .collect(Collectors.toList());

        if (!badIds.isEmpty()) {
            networkRepository.updateStatus(badIds, Status.TRASHED);
            log.info("Marked {} networks as TRASHED", badIds.size());
        }

        return good;
    }

    private boolean isWithinSigma(NetworkEntity n, Stats stats) {
        return (stats.sigmaLat == 0 || Math.abs(n.getLatitude() - stats.meanLat) <= 3 * stats.sigmaLat) &&
                (stats.sigmaLon == 0 || Math.abs(n.getLongitude() - stats.meanLon) <= 3 * stats.sigmaLon);
    }

    private void updateClusterFromNetworks(ClusterEntity cluster, List<NetworkEntity> networks) {

        int signalCount = networks.stream()
                .mapToInt(n -> n.getCreatedBy().equals(Role.ROLE_ADMIN) ? 1000 : 1)
                .sum();

        LatLong center = calculateCenter(networks);
        double radius = calculateRadius(networks, center);

        cluster.setLatitude(center.getLat());
        cluster.setLongitude(center.getLon());
        cluster.setRadius(radius);
        cluster.setUpdatedAt(LocalDateTime.now());
        cluster.setSignalCount(signalCount);

        log.debug("Cluster updated: center=({}, {}), radius={}m, signalCount={}",
                center.getLat(), center.getLon(), radius, signalCount);
    }

    /**
     * Вычисляет центр группы сетей (среднее арифметическое)
     */
    public LatLong calculateCenter(List<NetworkEntity> networks) {
        if (networks.isEmpty()) return new LatLong(0, 0);

        double sumLat = networks.stream()
                .mapToDouble(NetworkEntity::getLatitude)
                .sum();
        double sumLon = networks.stream()
                .mapToDouble(NetworkEntity::getLongitude)
                .sum();

        return new LatLong(sumLat / networks.size(), sumLon / networks.size());
    }

    /**
     * Вычисляет радиус группы сетей в метрах (максимальное расстояние от центра)
     * Использует формулу гаверсинусов для точного расчета
     */
    public double calculateRadius(List<NetworkEntity> networks, LatLong center) {
        return networks.stream()
                .mapToDouble(n -> haversineDistance(
                        center.getLat(), center.getLon(),
                        n.getLatitude(), n.getLongitude()))
                .max()
                .orElse(0);
    }

    /**
     * Расстояние по формуле гаверсинусов (для больших расстояний, точно в метрах)
     * @param lat1 широта точки 1 (градусы)
     * @param lon1 долгота точки 1 (градусы)
     * @param lat2 широта точки 2 (градусы)
     * @param lon2 долгота точки 2 (градусы)
     * @return расстояние в метрах
     */
    public double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000; // Радиус Земли в метрах

        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}