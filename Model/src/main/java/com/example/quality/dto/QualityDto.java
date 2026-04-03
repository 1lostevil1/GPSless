package com.example.quality.dto;

import java.time.LocalDateTime;

public record QualityDto(
        Long id,
        String geohash,
        long newSignalCount,
        long oldSignalCount,
        double quality,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
