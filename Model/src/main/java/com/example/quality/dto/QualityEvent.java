package com.example.quality.dto;


import java.time.LocalDateTime;

public record QualityEvent(double lat,
                           double lon,
                           double quality,
                           LocalDateTime createdAt) {
}
