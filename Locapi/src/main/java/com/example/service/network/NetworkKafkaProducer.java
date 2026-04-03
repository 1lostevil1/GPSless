package com.example.service.network;

import com.example.network.dto.NetworkSnapshot;
import com.example.network.dto.TrackDTO;
import com.example.quality.dto.QualityEvent;
import com.example.user.entity.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NetworkKafkaProducer {

    private final KafkaTemplate<String, TrackDTO> trackProducer;
    private final KafkaTemplate<String, QualityEvent> qualityProducer;

    private  final String trackTopic;
    private final String qualityTopic;

    public NetworkKafkaProducer(KafkaTemplate<String, TrackDTO> trackProducer, KafkaTemplate<String, QualityEvent> qualityProducer, @Value("${topic.snapshots}") String trackTopic, @Value("${topic.quality}") String qualityTopic) {
        this.trackProducer = trackProducer;
        this.qualityProducer = qualityProducer;
        this.trackTopic = trackTopic;
        this.qualityTopic = qualityTopic;
    }


    public void sendTrack(List<NetworkSnapshot> track, Role role) {
        trackProducer.send(trackTopic, new TrackDTO(track, role));
    }

    public void sendQuality(QualityEvent qualityEvent) {
        qualityProducer.send(qualityTopic, qualityEvent);
    }
}
