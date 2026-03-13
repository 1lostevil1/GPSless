package com.example.service;

import com.example.network.entity.ClusterEntity;
import com.example.network.entity.NetworkEntity;
import com.example.network.entity.Status;
import com.example.repository.NetworkRepository;
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
        LatLong center = calculateCenter(networks);
        double radius = calculateRadius(networks, center);

        cluster.setLatitude(center.getLat());
        cluster.setLongitude(center.getLon());
        cluster.setRadius(radius);
        cluster.setUpdatedAt(LocalDateTime.now());
        cluster.setSignalCount(networks.size());
    }

    /**
     * Вычисляет центр группы сетей
     */
    public LatLong calculateCenter(List<NetworkEntity> networks) {
        if (networks.isEmpty()) return new LatLong(0,0);

        double sumLat = networks.stream()
                .mapToDouble(NetworkEntity::getLatitude)
                .sum();
        double sumLon = networks.stream()
                .mapToDouble(NetworkEntity::getLongitude)
                .sum();

        return new LatLong(sumLat / networks.size(), sumLon / networks.size());
    }

    /**
     * Вычисляет радиус группы сетей (макс расстояние от центра)
     */
    public double calculateRadius(List<NetworkEntity> networks, LatLong center) {
        return networks.stream()
                .mapToDouble(n -> distance(center.getLat(), center.getLon(), n.getLatitude(), n.getLongitude()))
                .max()
                .orElse(0);
    }


    /**
     * Евклидово расстояние между двумя точками (для малых расстояний)
     */
    public double distance(double lat1, double lon1, double lat2, double lon2) {
        double dx = lat1 - lat2;
        double dy = lon1 - lon2;
        return Math.sqrt(dx * dx + dy * dy);
    }
}