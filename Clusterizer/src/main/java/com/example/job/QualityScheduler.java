package com.example.job;

import com.example.network.entity.QualityEntity;
import com.example.network.entity.QualityEventEntity;
import com.example.repository.QualityEventRepository;
import com.example.repository.QualityRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class QualityScheduler {

    private final QualityEventRepository qualityEventRepository;
    private final QualityRepository qualityRepository;
    private final int batchSize;

    public QualityScheduler(QualityEventRepository qualityEventRepository, QualityRepository qualityRepository, @Value("${qualityJob.batchSize}") int batchSize) {
        this.qualityEventRepository = qualityEventRepository;
        this.qualityRepository = qualityRepository;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedRate = 10000)
    @Transactional
    public void job() {

        List<QualityEntity> qualityEntities = qualityRepository.findDirtyBatch(batchSize);

        for(QualityEntity qualityEntity : qualityEntities) {

            List<QualityEventEntity> events = qualityEventRepository.findAllByGeohashAndCreatedAtAfter(qualityEntity.getGeohash(),LocalDateTime.now().minusWeeks(1));

            double quality = events.stream()
                    .mapToDouble(QualityEventEntity::getQuality)
                    .average()
                    .orElse(0.0);

            qualityEntity.setQuality(quality);
            qualityEntity.setDirty(false);
            qualityRepository.save(qualityEntity);
        }


        log.info("Job completed");
    }
}
