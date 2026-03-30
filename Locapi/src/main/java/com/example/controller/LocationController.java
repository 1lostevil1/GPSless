package com.example.controller;

import com.example.network.dto.ClusterDto;
import com.example.network.dto.NetworkSnapshot;
import com.example.network.entity.ClusterEntity;
import com.example.network.exception.LocationNotFoundException;
import com.example.service.network.LocationService;
import com.github.davidmoten.geo.LatLong;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @PostMapping("/current")
    public ResponseEntity<?> getLocation(@RequestBody NetworkSnapshot snapshot) {
        try {

            LatLong userLocation = locationService.determineLocation(snapshot);
            return ResponseEntity.ok(userLocation);
        } catch (LocationNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing location");
        }
    }

}
