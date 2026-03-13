package com.example.service;

import com.example.network.dto.CellularNetwork;
import com.example.network.dto.NetworkSnapshot;
import com.example.network.entity.NetworkEntity;
import com.example.network.entity.NetworkType;
import com.example.network.entity.Status;
import com.example.network.util.ClusterKeyStrategy;
import com.example.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NetworkTrackKafkaConsumer {

    private final NetworkRepository networkRepository;
    private final ClusterKeyStrategy clusterKeyStrategy;

    @KafkaListener(topics = "snapshots")
    @Transactional
    public void listen(@Payload List<NetworkSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }

        log.info("Received {} snapshots", snapshots.size());

        List<NetworkEntity> networkEntities = new ArrayList<>();

        for (NetworkSnapshot snapshot : snapshots) {
            // Wi-Fi сети
            if (snapshot.getWifiNetworks() != null) {
                snapshot.getWifiNetworks().stream()
                        .map(wifi -> NetworkEntity.builder()
                                .clusterKey(clusterKeyStrategy.generate(wifi))
                                .latitude(snapshot.getLocation().getLatitude())
                                .longitude(snapshot.getLocation().getLongitude())
                                .name(wifi.getSsid())
                                .signalStrength(wifi.getSignalLevel())
                                .status(Status.READY)
                                .type(NetworkType.WIFI)
                                .build())
                        .forEach(networkEntities::add);
            }
            // Сотовая сеть
            CellularNetwork cellularNetwork = snapshot.getCellularNetwork();
            if (cellularNetwork != null) {
                networkEntities.add(NetworkEntity.builder()
                        .clusterKey(clusterKeyStrategy.generate(snapshot.getCellularNetwork()))
                        .latitude(snapshot.getLocation().getLatitude())
                        .longitude(snapshot.getLocation().getLongitude())
                        .name(cellularNetwork.mcc() + cellularNetwork.mnc())
                        .signalStrength(cellularNetwork.signalStrength())
                        .status(Status.READY)
                        .type(NetworkType.CELLULAR)
                        .build());
            }
            // BLE маячки
            if (snapshot.getBluetoothDevices() != null) {
                snapshot.getBluetoothDevices().stream()
                        .map(bt -> NetworkEntity.builder()
                                .clusterKey(clusterKeyStrategy.generate(bt))
                                .latitude(snapshot.getLocation().getLatitude())
                                .longitude(snapshot.getLocation().getLongitude())
                                .name(bt.name())
                                .signalStrength(bt.rssi())
                                .status(Status.READY)
                                .type(NetworkType.BLUETOOTH)
                                .build())
                        .forEach(networkEntities::add);
            }
        }
        if (!networkEntities.isEmpty()) {
            networkRepository.saveAll(networkEntities);
            log.info("Saved {} network entities", networkEntities.size());
        }
    }
}