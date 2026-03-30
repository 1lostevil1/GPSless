package com.example.controller;

import com.example.network.dto.ClusterDto;
import com.example.network.dto.NetworkSnapshot;
import com.example.network.entity.ClusterEntity;
import com.example.repository.ClusterRepository;
import com.example.service.network.NetworkTrackKafkaProducer;
import com.example.service.network.NetworkTrackValidator;
import com.example.user.entity.Role;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api")
@Slf4j
public class NetworkTrackController {

    private final NetworkTrackValidator validator;
    private final NetworkTrackKafkaProducer producer;

    private final ClusterRepository repository;

    @PostMapping("/track/save")
    public boolean saveTrack(@RequestBody List<NetworkSnapshot> track) {
        log.info("SAVE TRACK: received track size={}", track.size());

        if(validator.validateTrack(track)) {
            log.info("TRACK VALIDATION SUCCESS: size={}, sending to Kafka", track.size());
            producer.send(track, Role.ROLE_USER);
            log.info("TRACK SENT SUCCESSFULLY: size={}", track.size());
            return true;
        }

        log.warn("TRACK VALIDATION FAILED: track size={} failed validation", track.size());
        return false;
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/admin/track/save")
    public void saveSnapshot(@RequestBody NetworkSnapshot snapshot) {
        log.info("ADMIN SAVE SNAPSHOT: snapshot details={}", snapshot);
        log.info("ADMIN SNAPSHOT VALIDATION: sending to Kafka with admin role");
        producer.send(List.of(snapshot), Role.ROLE_ADMIN);
        log.info("ADMIN SNAPSHOT SENT SUCCESSFULLY: snapshot={}", snapshot);
    }

    @GetMapping("/clusters")
    public List<ClusterDto> getAllClusters() {
        List<ClusterEntity> clusters = repository.findAll();
        log.info("GET CLUSTERS: returning {}", clusters.size());

        return clusters.stream()
                .map(NetworkTrackController::toDto)
                .toList();
    }

    private static ClusterDto toDto(ClusterEntity c) {
        return new ClusterDto(
                c.getId(),
                c.getClusterKey(),
                c.getName(),
                c.getType(),
                c.getLatitude(),
                c.getLongitude(),
                c.getRadius(),
                c.getSignalCount(),
                c.getGeohash(),
                c.getUpdatedAt()
        );
    }
}