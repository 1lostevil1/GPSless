package com.example.service;

import com.example.NetworkSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NetworkTrackKafkaProducer {

    private final KafkaTemplate<String, List<NetworkSnapshot>> producer;

    private  String topic = "snapshots";

    public NetworkTrackKafkaProducer(KafkaTemplate<String, List<NetworkSnapshot>> producer) {
        this.producer = producer;
    }


    public void send(List<NetworkSnapshot> track) {
        producer.send(topic, track);
    }
}
