package com.example;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "network_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NetworkSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String snapshots;
}