package com.example.repository;

import com.example.network.entity.QualityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QualityRepository extends JpaRepository<QualityEntity, Long> {

    @Query(value = """
        SELECT * FROM quality
        WHERE is_dirty = true
        ORDER BY id
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<QualityEntity> findDirtyBatch(@Param("limit") int limit);

    Optional<QualityEntity> findByGeohash(String geohash);

    @Modifying
    @Query(value = """
        INSERT INTO quality (geohash, is_dirty, quality, new_signal_count, old_signal_count, created_at, updated_at)
        VALUES (:geohash, true, 0, 0, 0, now(), now())
        ON CONFLICT (geohash)
        DO UPDATE SET is_dirty = true, updated_at = now()
        """, nativeQuery = true)
    int upsertDirty(@Param("geohash") String geohash);
}