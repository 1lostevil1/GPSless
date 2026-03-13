package com.example.service.network;

import com.example.network.dto.NetworkSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NetworkTrackKafkaProducer {

    private final KafkaTemplate<String, List<NetworkSnapshot>> producer;

    private  final String topic;

    public NetworkTrackKafkaProducer(KafkaTemplate<String, List<NetworkSnapshot>> producer, @Value("${topic}") String topic) {
        this.producer = producer;
        this.topic = topic;
    }


    public void send(List<NetworkSnapshot> track) {
        producer.send(topic, track);
    }
}
