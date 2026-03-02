package com.example.repository;

import com.example.entity.WifiNetworkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WifiNetworkRepository extends JpaRepository<WifiNetworkEntity, Long> {
}
