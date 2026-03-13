package com.example.repository;

import com.example.network.entity.ClusterEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClusterRepository extends JpaRepository<ClusterEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ClusterEntity c WHERE c.clusterKey = :clusterKey")
    List<ClusterEntity> findByClusterKeyWithLock(@Param("clusterKey") String clusterKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ClusterEntity c WHERE c.clusterKey = :clusterKey AND c.geohash = :geohash")
    Optional<ClusterEntity> findByClusterKeyAndGeohashWithLock(
            @Param("clusterKey") String clusterKey,
            @Param("geohash") String geohash
    );
}