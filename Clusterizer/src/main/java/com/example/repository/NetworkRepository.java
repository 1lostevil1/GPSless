package com.example.repository;

import com.example.network.entity.NetworkEntity;
import com.example.network.entity.Status;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NetworkRepository extends JpaRepository<NetworkEntity, Long> {

    @Query(value = """
    SELECT * FROM network
    WHERE status = CAST(:status AS status_type)
    ORDER BY id
    LIMIT :limit
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    List<NetworkEntity> findBatchNative(@Param("status") String status, @Param("limit") int limit);

    @Modifying
    @Query("UPDATE NetworkEntity n SET n.status = :status WHERE n.id IN :ids")
    void updateStatus(@Param("ids") List<Long> ids, @Param("status") Status status);

    List<NetworkEntity> findAllByClusterKeyAndGeohashAndStatus(
            String clusterKey, String geohash, Status status);

    List<NetworkEntity> findAllByClusterKeyAndStatus(String clusterKey, Status status);
}