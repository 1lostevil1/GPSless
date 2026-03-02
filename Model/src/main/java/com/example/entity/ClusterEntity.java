package com.example.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cluster")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClusterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String identity;

}
