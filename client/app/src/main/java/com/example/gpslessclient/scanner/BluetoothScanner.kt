package com.example.gpslessclient.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.gpslessclient.model.BluetoothDeviceInfo

class BluetoothScanner(private val context: Context) {
    private val adapter = BluetoothAdapter.getDefaultAdapter()
    private var bleScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private val foundDevices = mutableMapOf<String, BluetoothDeviceInfo>()
    private var isScanning = false

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("MissingPermission")
    fun scan(scanDuration: Long = 10000): List<BluetoothDeviceInfo> {
        // Проверки (без изменений)
        if (adapter == null || !isBleSupported()) return emptyList()
        if (!adapter.isEnabled) return emptyList()
        if (!hasBlePermissions()) return emptyList()

        stopScan()
        foundDevices.clear()

        bleScanner = adapter.bluetoothLeScanner ?: return emptyList()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // Добавляем проверку на маячок
                if (isBeacon(result)) {
                    val device = result.device
                    val deviceName = result.scanRecord?.deviceName ?: device.name ?: "Unknown Beacon"
                    foundDevices[device.address] = BluetoothDeviceInfo(
                        name = deviceName,
                        address = device.address,
                        rssi = result.rssi,
                        deviceType = "BEACON" // или можно оставить getDeviceType(device.type), но тогда маячки не будут явно выделены
                    )
                }
                // Не-маячки игнорируем
            }

            override fun onScanFailed(errorCode: Int) {
                stopScan()
            }
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(null, scanSettings, scanCallback)
        isScanning = true

        Thread.sleep(scanDuration) // Блокировка – лучше заменить на корутины, но оставляем как есть

        stopScan()
        return foundDevices.values.toList()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (isScanning && bleScanner != null && scanCallback != null) {
            bleScanner?.stopScan(scanCallback)
        }
        isScanning = false
        scanCallback = null
        bleScanner = null
    }

    /**
     * Определяет, является ли устройство статичным маячком (iBeacon, Eddystone, AltBeacon и т.п.)
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun isBeacon(result: ScanResult): Boolean {
        val record = result.scanRecord ?: return false

        // 1. iBeacon (Apple)
        val appleData = record.getManufacturerSpecificData(0x004C)
        if (appleData != null && appleData.size >= 23) {
            if (appleData[0] == 0x02.toByte() && appleData[1] == 0x15.toByte()) {
                return true
            }
        }

        // 2. Eddystone (Google)
        val eddystoneUuid = ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")
        val eddystoneData = record.getServiceData(eddystoneUuid)
        if (eddystoneData != null && eddystoneData.isNotEmpty()) {
            return true
        }

        // 3. AltBeacon (Radius Networks)
        val altBeaconData = record.getManufacturerSpecificData(0x0118)
        if (altBeaconData != null && altBeaconData.size >= 24) {
            if (altBeaconData[0] == 0xBE.toByte() && altBeaconData[1] == 0xAC.toByte()) {
                return true
            }
        }

        // 4. Другие известные идентификаторы производителей маячков
        val knownBeaconManufacturers = listOf(
            0x004C, // Apple
            0x0118, // Radius Networks
            0x0059, // Nordic Semi
            0x0157, // Kontakt.io
            0x001D, // Estimote
            0x0133, // Bluecats
            0x0639  // Gimbal
        )
        for (manufacturerId in knownBeaconManufacturers) {
            val mData = record.getManufacturerSpecificData(manufacturerId)
            if (mData != null && mData.isNotEmpty()) {
                return true
            }
        }

        return false
    }

    private fun getDeviceType(type: Int): String = when (type) {
        BluetoothDevice.DEVICE_TYPE_LE -> "BLE_DEVICE"
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "CLASSIC"
        BluetoothDevice.DEVICE_TYPE_DUAL -> "DUAL"
        else -> "UNKNOWN"
    }

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isBleSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        } else {
            false
        }
    }
}