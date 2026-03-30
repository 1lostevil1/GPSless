package com.example.service.network;

import com.example.network.dto.NetworkSnapshot;
import com.example.network.dto.TrackDTO;
import com.example.user.entity.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NetworkTrackKafkaProducer {

    private final KafkaTemplate<String, TrackDTO> producer;

    private  final String topic;

    public NetworkTrackKafkaProducer(KafkaTemplate<String, TrackDTO> producer, @Value("${topic}") String topic) {
        this.producer = producer;
        this.topic = topic;
    }


    public void send(List<NetworkSnapshot> track, Role role) {
        producer.send(topic, new TrackDTO(track, role));
    }
}
