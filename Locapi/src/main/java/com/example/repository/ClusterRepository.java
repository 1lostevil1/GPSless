package com.example.repository;

import com.example.network.entity.ClusterEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClusterRepository {

    List<ClusterEntity> findByClusterKeyIn(List<String> clusterKeys);
}
