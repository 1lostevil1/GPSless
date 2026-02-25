package com.example.job;

import com.example.NetworkSnapshotEntity;
import com.example.repository.NetworkRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClusterScheduler {

    private final NetworkRepository networkRepository;
    private final int batchSize;

    public ClusterScheduler(NetworkRepository networkRepository, @Value("clusterJob.batchSize") int batchSize) {
        this.networkRepository = networkRepository;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedRate = 10000)
    @Transactional
    private void job() {
        List<NetworkSnapshotEntity> snapshots = networkRepository.findBatchNative(NetworkSnapshotEntity.Status.READY, batchSize);

    }
}
