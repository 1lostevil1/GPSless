package com.example.controller;

import com.example.network.dto.ClusterDto;
import com.example.network.dto.NetworkSnapshot;
import com.example.network.entity.ClusterEntity;
import com.example.network.exception.LocationNotFoundException;
import com.example.service.network.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class ChunkCacheController {

    private final LocationService locationService;

    @PostMapping("/chunk")
    public ResponseEntity<?> getChunk(@RequestBody NetworkSnapshot snapshot) {
        try {
            // 1. Определяем местоположение пользователя
            LocationService.Point userLocation = locationService.determineLocation(snapshot);

            // 2. Ищем кластеры в квадрате 500x500 м вокруг
            List<ClusterEntity> clusters = locationService.findClustersInSquare(userLocation, 500);

            // 3. Преобразуем в DTO для ответа
            List<ClusterDto> result = clusters.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (LocationNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing location");
        }
    }

    private ClusterDto toDto(ClusterEntity entity) {
        return new ClusterDto(
                entity.getId(),
                entity.getName(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getRadius()
        );
    }
}
