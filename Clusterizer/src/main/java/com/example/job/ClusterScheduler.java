package com.example.job;

import com.example.entity.NetworkSnapshotEntity;
import com.example.entity.WifiNetworkEntity;
import com.example.repository.NetworkRepository;
import com.example.repository.WifiNetworkRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClusterScheduler {

    private final WifiNetworkRepository wifiRepository;
    private final CellularNetworkRepository cellularRepository;
    private final BluetoothDeviceRepository bluetoothRepository;
    private final int batchSize;

    public ClusterScheduler(NetworkRepository networkRepository, WifiNetworkRepository wifiRepository, @Value("clusterJob.batchSize") int batchSize) {
        this.wifiRepository = wifiRepository;
        this.networkRepository = networkRepository;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedRate = 10000)
    @Transactional
    private void job() {
        List<WifiNetworkEntity> wifiNetworkEntities = networkRepository.findBatchNative(NetworkSnapshotEntity.Status.READY, batchSize);

    }
}
