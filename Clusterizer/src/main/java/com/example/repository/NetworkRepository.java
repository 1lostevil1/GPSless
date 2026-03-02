package com.example.repository;

import com.example.entity.NetworkSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NetworkRepository extends JpaRepository<NetworkSnapshotEntity, Long> {

    @Query(value = """
                    SELECT * FROM snapshot
                    WHERE status = :status 
                    ORDER BY id 
                    LIMIT :limit 
                    FOR UPDATE SKIP LOCKED
                    """, nativeQuery = true)
    List<NetworkSnapshotEntity> findBatchNative (@Param("status") NetworkSnapshotEntity.Status status, @Param("limit") int limit);
}
