package com.example.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class WifiNetwork {

    @Column(name = "ssid", length = 255)
    private String ssid;

    @Column(name = "bssid", length = 17, nullable = false)
    private String bssid;

    @Column(name = "signal_level", nullable = false)
    private int signalLevel;     // dBm

    @Column(name = "frequency")
    private int frequency;       // MHz

    @Column(name = "capabilities", length = 500)
    private String capabilities;

    @Column(name = "channel")
    private int channel;

    @Column(name = "is_secure", nullable = false)
    private boolean isSecure;
}
