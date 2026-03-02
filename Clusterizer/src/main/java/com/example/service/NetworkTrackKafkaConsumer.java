package com.example.consumer;

import com.example.dto.*;
import com.example.entity.*;
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

    private final WifiNetworkRepository wifiRepository;
    private final CellularNetworkRepository cellularRepository;
    private final BluetoothDeviceRepository bluetoothRepository;

    @KafkaListener(topics = "snapshots")
    @Transactional
    public void listen(@Payload List<NetworkSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }

        log.info("Received {} snapshots", snapshots.size());

        List<WifiNetworkEntity> wifiEntities = new ArrayList<>();
        List<CellularNetworkEntity> cellularEntities = new ArrayList<>();
        List<BluetoothDeviceEntity> bluetoothEntities = new ArrayList<>();

        for (NetworkSnapshot snapshot : snapshots) {
            // Wi-Fi сети
            if (snapshot.getWifiNetworks() != null) {
                snapshot.getWifiNetworks().stream()
                        .map(wifi -> WifiNetworkEntity.builder()
                                .wifiNetwork(wifi)
                                .gpsData(snapshot.getLocation())
                                .clusterId(wifi.getBssid())
                                .status(Status.READY)
                                .build())
                        .forEach(wifiEntities::add);
            }
            // Сотовая сеть
            if (snapshot.getCellularNetwork() != null) {
                cellularEntities.add(CellularNetworkEntity.builder()
                        .cellularNetwork(snapshot.getCellularNetwork())
                        .gpsData(snapshot.getLocation())
                        .snapshotId(snapshotId)
                        .build());
            }
            // BLE маячки
            if (snapshot.getBluetoothDevices() != null) {
                snapshot.getBluetoothDevices().stream()
                        .filter(BluetoothDeviceInfo::isBeacon)
                        .map(bt -> BluetoothDeviceEntity.builder()
                                .bluetoothDevice(bt)
                                .gpsData(snapshot.getLocation())
                                .snapshotId(snapshotId)
                                .build())
                        .forEach(bluetoothEntities::add);
            }
        }

        // Сохраняем всё пачками
        if (!wifiEntities.isEmpty()) {
            wifiRepository.saveAll(wifiEntities);
            log.info("Saved {} Wi-Fi networks", wifiEntities.size());
        }

        if (!cellularEntities.isEmpty()) {
            cellularRepository.saveAll(cellularEntities);
            log.info("Saved {} cellular networks", cellularEntities.size());
        }

        if (!bluetoothEntities.isEmpty()) {
            bluetoothRepository.saveAll(bluetoothEntities);
            log.info("Saved {} Bluetooth devices", bluetoothEntities.size());
        }
    }
}