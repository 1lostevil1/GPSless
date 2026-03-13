package com.example.network.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "network")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NetworkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String clusterKey;

    private double latitude;

    private double longitude;

    private String geohash;

    private String name;

    @Column(name = "signal_strength")
    private int signalStrength;

    @JdbcTypeCode(SqlTypes.ENUM)
    private NetworkType type;

    @JdbcTypeCode(SqlTypes.ENUM)
    private Status status;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        geohash = type.computeGeohash(latitude, longitude);
    }



}