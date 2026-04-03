package com.example.repository;

import com.example.network.entity.QualityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QualityRepository extends JpaRepository<QualityEntity,Long> {

    public Optional<QualityEntity> findByGeohash(String geohash);
}
