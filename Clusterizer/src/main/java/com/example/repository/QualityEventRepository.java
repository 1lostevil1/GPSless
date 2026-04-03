package com.example.repository;

import com.example.network.entity.QualityEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface QualityEventRepository extends JpaRepository<QualityEventEntity, Long> {

    List<QualityEventEntity> findAllByGeohashAndCreatedAtAfter(String geohash, LocalDateTime dateTime);
}
