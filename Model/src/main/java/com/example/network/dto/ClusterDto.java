package com.example.network.dto;

import com.example.network.entity.NetworkType;

import java.time.LocalDateTime;

public record ClusterDto(
        Long id,
        String clusterKey,
        String name,
        NetworkType type,
        double lat,
        double lon,
        double radiusMeters,
        int signalCount,
        String geohash,
        LocalDateTime updatedAt
) {
}