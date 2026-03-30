package com.example.network.util;

import com.example.network.dto.BluetoothDeviceInfo;
import com.example.network.dto.CellularNetwork;
import com.example.network.dto.WifiNetwork;
import com.example.network.entity.NetworkType;
import org.springframework.stereotype.Component;

@Component
public class ClusterKeyStrategy {

    public String generate(WifiNetwork wifi) {
        return NetworkType.WIFI.name() + wifi.getBssid() + wifi.getSsid();
    }

    public String generate(CellularNetwork cell) {
        return NetworkType.CELLULAR.name() + cell.mcc() + cell.mnc() + cell.locationAreaCode() + cell.cellId();
    }

    public String generate(BluetoothDeviceInfo bt) {
        return NetworkType.BLUETOOTH.name() + bt.beaconId();
    }
}