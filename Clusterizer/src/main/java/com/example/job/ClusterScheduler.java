package com.example.job;

import com.example.network.entity.ClusterEntity;
import com.example.network.entity.NetworkEntity;
import com.example.network.entity.Status;
import com.example.repository.ClusterRepository;
import com.example.repository.NetworkRepository;
import com.example.service.ClusterAssignmentService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ClusterScheduler {

    private final NetworkRepository networkRepository;
    private final ClusterRepository clusterRepository;
    private final ClusterAssignmentService clusterAssignmentService;
    private final int batchSize;

    public ClusterScheduler(NetworkRepository networkRepository,
                            ClusterRepository clusterRepository,
                            ClusterAssignmentService clusterAssignmentService,
                            @Value("${clusterJob.batchSize}") int batchSize) {
        this.networkRepository = networkRepository;
        this.clusterRepository = clusterRepository;
        this.clusterAssignmentService = clusterAssignmentService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedRate = 10000)
    @Transactional
    public void job() {
        List<NetworkEntity> networkEntities = networkRepository.findBatchNative(Status.READY.name(), batchSize);
        if (networkEntities.isEmpty()) return;

        Map<String, List<NetworkEntity>> networksByKey = networkEntities.stream()
                .collect(Collectors.groupingBy(NetworkEntity::getClusterKey));

        log.info("Processing {} networks for keysЖ {}", networkEntities.size(), networksByKey);

        networksByKey.forEach((clusterKey, networks) -> {
            // Загружаем все существующие кластеры для этого ключа с блокировкой
            List<ClusterEntity> existingClusters = clusterRepository.findByClusterKeyWithLock(clusterKey);
            clusterAssignmentService.assignNetworks(clusterKey, networks, existingClusters);
        });

        List<Long> networkIds = networkEntities.stream()
                .map(NetworkEntity::getId)
                .toList();
        networkRepository.updateStatus(networkIds, Status.DONE);

        log.info("Job completed");
    }
}