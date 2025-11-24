package com.example.gpslessclient.service

import android.Manifest
import androidx.annotation.RequiresPermission
import com.example.gpslessclient.model.BluetoothData
import com.example.gpslessclient.model.CellularData
import com.example.gpslessclient.model.FullScanResult
import com.example.gpslessclient.model.WifiData

class NetworkScanCoordinator(private val context: android.content.Context) {
    private val cellularScanner = com.example.gpslessclient.scanner.CellularScanner(context)
    private val wifiScanner = com.example.gpslessclient.scanner.WifiScanner(context)
    private val bluetoothScanner = com.example.gpslessclient.scanner.BluetoothScanner(context)
    private val locationScanner = com.example.gpslessclient.scanner.LocationScanner(context)


    data class ScanStats(
        val totalNetworks: Int,
        val cellularCount: Int,
        val wifiCount: Int,
        val bluetoothCount: Int,
        val newDevicesCount: Int
    )

    @RequiresPermission(allOf = [
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,])
    fun performScan(): FullScanResult {
        val timestamp = System.currentTimeMillis()

        val location = locationScanner.scan().firstOrNull();
        val cellularNetworks = cellularScanner.scan()
        val wifiNetworks = wifiScanner.scan()
        val bluetoothDevices = bluetoothScanner.scan();

        return FullScanResult(
            location = null,
            cellularNetworks = cellularNetworks as List<CellularData>,
            wifiNetworks = wifiNetworks as List<WifiData>,
            bluetoothDevices = bluetoothDevices as List<BluetoothData>,
            timestamp = timestamp
        )

    }



}