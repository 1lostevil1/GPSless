package com.example;

public record CellularNetwork(
        String networkType,    // 2G, 3G, 4G, 5G
        int signalStrength,    // dBm или ASU
        String mcc,
        String mnc,
        Integer cellId,
        Integer locationAreaCode
) {}
