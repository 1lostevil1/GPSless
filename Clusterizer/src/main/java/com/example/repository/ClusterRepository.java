package com.example.repository;

import com.example.entity.ClusterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClusterRepository extends JpaRepository<ClusterEntity, Long> {

    public ClusterEntity findByIdentity(String identity);
}
