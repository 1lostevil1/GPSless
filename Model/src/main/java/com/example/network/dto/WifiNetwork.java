package com.example.network.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WifiNetwork {

    private String ssid;

    private String bssid;

    private int signalLevel;     // dBm

    private int frequency;       // MHz

    private String capabilities;

    private int channel;

    private boolean isSecure;
}
