package com.example.network.util;

import com.example.network.dto.BluetoothDeviceInfo;
import com.example.network.dto.CellularNetwork;
import com.example.network.dto.WifiNetwork;
import org.springframework.stereotype.Component;

@Component
public class ClusterKeyStrategy {

    public String generate(WifiNetwork wifi) {
        return wifi.getBssid() + wifi.getSsid();
    }

    public String generate(CellularNetwork cell) {
        return cell.mcc() + cell.mnc() + cell.locationAreaCode() + cell.cellId();
    }

    public String generate(BluetoothDeviceInfo bt) {
        return bt.UUID() + bt.major() + bt.minor();
    }
}