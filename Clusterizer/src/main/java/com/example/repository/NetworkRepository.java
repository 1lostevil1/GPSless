package com.example.repository;

import com.example.NetworkSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NetworkRepository extends JpaRepository<NetworkSnapshotEntity, Long> {
}
