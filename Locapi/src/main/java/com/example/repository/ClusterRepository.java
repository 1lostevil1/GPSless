package com.example.repository;

import com.example.network.entity.ClusterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClusterRepository extends JpaRepository<ClusterEntity, Long> {

    List<ClusterEntity> findByClusterKeyIn(List<String> clusterKeys);

    List<ClusterEntity> findByGeohashStartingWith(String prefix);
}
