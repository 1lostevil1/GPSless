package com.example.service;

import com.example.network.entity.ClusterEntity;
import com.example.network.entity.NetworkEntity;
import com.example.repository.ClusterRepository;
import com.github.davidmoten.geo.LatLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClusterProcessorService {

    private final ClusterMathService mathService;
    private final ClusterRepository clusterRepository;

    /**
     * Создаёт новый кластер для ключа с указанным geohash.
     * Если такой кластер уже существует (конкурентное создание), обогащает существующий.
     */
    @Transactional
    public void processNewCluster(String clusterKey, String geohash, List<NetworkEntity> networks) {
        if (networks == null || networks.isEmpty()) return;

        LatLong center = mathService.calculateCenter(networks);
        double radius = mathService.calculateRadius(networks, center);

        ClusterEntity newCluster = ClusterEntity.builder()
                .clusterKey(clusterKey)
                .geohash(geohash)
                .name(networks.getFirst().getName())
                .type(networks.getFirst().getType())
                .latitude(center.getLat())
                .longitude(center.getLon())
                .radius(radius)
                .signalCount(networks.size())
                .build();

        try {
            clusterRepository.save(newCluster);
            log.debug("Created new cluster id={} for key={} geohash={}",
                    newCluster.getId(), clusterKey, geohash);
        } catch (DataIntegrityViolationException e) {
            // Конфликт - другой инстанс создал такой же кластер
            log.debug("Cluster with key={} geohash={} already exists, updating", clusterKey, geohash);
            ClusterEntity existing = clusterRepository
                    .findByClusterKeyAndGeohashWithLock(clusterKey, geohash)
                    .orElseThrow(() -> new IllegalStateException("Cluster disappeared"));

            enrichCluster(existing, networks);
        }
    }

    /**
     * Обновляет существующий кластер новыми сетями.
     */
    @Transactional
    public void enrichCluster(ClusterEntity cluster, List<NetworkEntity> networks) {
        if (networks == null || networks.isEmpty()) {
            log.warn("No networks to enrich cluster {}", cluster.getId());
            return;
        }
        mathService.enrichCluster(cluster, networks);
        clusterRepository.save(cluster);
        log.debug("Enriched cluster {}", cluster.getId());
    }
}