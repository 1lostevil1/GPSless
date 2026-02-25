package com.example.service;

import com.example.NetworkSnapshot;
import com.example.NetworkSnapshotEntity;
import com.example.repository.NetworkRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NetworkTrackKafkaConsumer {

    private final NetworkRepository networkRepository;

    public NetworkTrackKafkaConsumer(NetworkRepository networkRepository) {
        this.networkRepository = networkRepository;
    }

    @KafkaListener(topics = "snapshots")
    public void listen(@Payload List<NetworkSnapshot> track) {
        networkRepository.saveAll(track.stream()
                .map(x-> NetworkSnapshotEntity.builder()
                        .snapshot(x)
                        .status(NetworkSnapshotEntity.Status.READY)
                .build()).toList());
    }
}
