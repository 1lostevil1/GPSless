package com.example.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class GpsData {

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "accuracy")
    private float accuracy;

    @Column(name = "speed")
    private float speed;

    @Column(name = "bearing")
    private float bearing;

    @Column(name = "altitude")
    private double altitude;
}