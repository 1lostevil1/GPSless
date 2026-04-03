package com.example.consumer;

import com.example.network.entity.QualityEventEntity;
import com.example.quality.dto.QualityEvent;
import com.example.repository.QualityEventRepository;
import com.example.repository.QualityRepository;
import com.github.davidmoten.geo.GeoHash;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class QualityKafkaConsumer {

    private final QualityEventRepository qualityEventRepository;
    private final QualityRepository qualityRepository;

    @KafkaListener(topics = "quality")
    @Transactional
    public void listen(@Payload QualityEvent event) {

        log.info("Received quality event: {}", event);
        String geohash = GeoHash.encodeHash(event.lat(),event.lon(), 7);

        QualityEventEntity entity = QualityEventEntity.builder()
                .geohash(geohash)
                .quality(event.quality())
                .build();
    qualityEventRepository.save(entity);
    qualityRepository.upsertDirty(geohash);



    }

}
