package com.example.entity;

import com.example.dto.GpsData;
import com.example.dto.WifiNetwork;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "wifi_networks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WifiNetworkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private WifiNetwork wifiNetwork;

    @Embedded
    private GpsData gpsData;

    @Column(name = "cluster_id", nullable = false)
    private String clusterId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @JdbcTypeCode(SqlTypes.ENUM)
    private Status status;
}