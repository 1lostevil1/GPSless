package com.example.service;

import com.example.NetworkSnapshot;
import com.example.NetworkSnapshotEntity;
import com.example.repository.NetworkRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NetworkTrackKafkaConsumer {

    private final NetworkRepository networkRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NetworkTrackKafkaConsumer(NetworkRepository networkRepository) {
        this.networkRepository = networkRepository;
    }

    @KafkaListener(topics = "snapshots")
    public void listen(List<NetworkSnapshot> track) throws JsonProcessingException {
        networkRepository.save(NetworkSnapshotEntity.builder().snapshots(objectMapper.writeValueAsString(track)).build());
    }
}
