package com.example.network.entity;

import com.github.davidmoten.geo.GeoHash;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NetworkType {
    WIFI (6),
    CELLULAR(4),
    BLUETOOTH(6);

    private final int geohashLength;

    public String computeGeohash(double latitude, double longitude) {
        return GeoHash.encodeHash(latitude, longitude, geohashLength);
    }
}
