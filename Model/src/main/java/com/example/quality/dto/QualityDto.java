package com.example.quality.dto;

import java.time.LocalDateTime;

public record QualityDto(
        String geohash,
        double quality,
        LocalDateTime updatedAt
) {}
