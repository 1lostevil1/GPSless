package com.example;

public record WifiNetwork(
        String ssid,
        String bssid,
        int signalLevel,     // dBm
        int frequency,       // MHz
        String capabilities,
        int channel,
        boolean isSecure
) {}
