package com.example.service;

import com.example.network.entity.ClusterEntity;
import com.example.network.entity.NetworkEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClusterAssignmentService {

    private final ClusterProcessorService processor;

    /**
     * Распределяет новые сети между существующими кластерами одного ключа.
     * Группировка идёт по geohash сетей.
     *
     * @param clusterKey        идентификатор сети
     * @param newNetworks       новые сети для этого ключа
     * @param existingClusters  все существующие кластеры для этого ключа (уже заблокированы)
     */
    public void assignNetworks(String clusterKey,
                               List<NetworkEntity> newNetworks,
                               List<ClusterEntity> existingClusters) {

        // Группируем новые сети по geohash
        Map<String, List<NetworkEntity>> networksByGeohash = newNetworks.stream()
                .collect(Collectors.groupingBy(NetworkEntity::getGeohash));

        // Создаём мапу существующих кластеров для быстрого доступа
        Map<String, ClusterEntity> clusterMap = existingClusters.stream()
                .collect(Collectors.toMap(ClusterEntity::getGeohash, c -> c));

        for (Map.Entry<String, List<NetworkEntity>> entry : networksByGeohash.entrySet()) {
            String geohash = entry.getKey();
            List<NetworkEntity> networks = entry.getValue();

            ClusterEntity existingCluster = clusterMap.get(geohash);

            if (existingCluster != null) {
                // Кластер с таким geohash уже существует - обогащаем
                log.debug("Assign {} nets to existing cluster with geohash {}",
                        networks.size(), geohash);
                processor.enrichCluster(existingCluster, networks);
            } else {
                // Новый geohash для этого clusterKey - создаём кластер
                log.debug("Creating new cluster for geohash {} with {} nets",
                        geohash, networks.size());
                processor.processNewCluster(clusterKey, geohash, networks);
            }
        }
    }
}