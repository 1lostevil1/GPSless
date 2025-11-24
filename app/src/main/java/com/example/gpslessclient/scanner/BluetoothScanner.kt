package com.example.gpslessclient.scanner

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import androidx.annotation.RequiresPermission
import com.example.gpslessclient.model.BluetoothData
import com.example.gpslessclient.model.PermissionScope
import com.example.gpslessclient.model.NetworkData

class BluetoothScanner(context: Context) : BaseNetworkScanner(context, PermissionScope.BLUETOOTH) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val foundDevices = mutableListOf<BluetoothData>()
    private var scanCallback: ScanCallback? = null

    override fun scan(): List<NetworkData> {
        if (!checkPermissions()) {
            logPermissionDenied()
            return emptyList()
        }

        foundDevices.clear()

        return try {
            performBluetoothScan()
        } catch (e: SecurityException) {
            handleSecurityException(e, "Bluetooth scan")
            emptyList()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    private fun performBluetoothScan(): List<NetworkData> {
        if (bluetoothAdapter?.isEnabled == false) {
            return emptyList()
        }

        scanPairedDevices()

        startBLEScan()
        Thread.sleep(3000)
        stopBLEScan()

        return foundDevices.distinctBy { it.id }.toList()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun scanPairedDevices() {
        val pairedDevices = bluetoothAdapter?.bondedDevices
        pairedDevices?.forEach { device ->
            foundDevices.add(
                BluetoothData(
                    name = device.name ?: "Unknown",
                    macAddress = device.address,
                    deviceType = device.type.toString(),
                    bluetoothClass = device.bluetoothClass?.majorDeviceClass,
                    signalStrength = -1
                )
            )
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startBLEScan() {
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        scanCallback = object : ScanCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                foundDevices.add(
                    BluetoothData(
                        name = result.device.name ?: "Unknown",
                        macAddress = result.device.address,
                        deviceType = "BLE",
                        signalStrength = result.rssi
                    )
                )
            }
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun stopBLEScan() {
        scanCallback?.let {
            bluetoothLeScanner?.stopScan(it)
        }
    }
}