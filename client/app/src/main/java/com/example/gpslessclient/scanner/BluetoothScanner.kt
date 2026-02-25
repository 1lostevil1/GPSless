package com.example.gpslessclient.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.gpslessclient.model.BluetoothDeviceInfo

class BluetoothScanner(private val context: Context) {
    private val adapter = BluetoothAdapter.getDefaultAdapter()
    private var bleScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null // ✅ Сохраняем callback
    private val foundDevices = mutableMapOf<String, BluetoothDeviceInfo>()

    private var isScanning = false

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("MissingPermission")
    fun scan(scanDuration: Long = 10000): List<BluetoothDeviceInfo> {
        // Проверяем поддержку BLE
        if (adapter == null || !isBleSupported()) {
            return emptyList()
        }

        // Проверяем включен ли Bluetooth
        if (!adapter.isEnabled) {
            return emptyList()
        }

        // Проверяем разрешения
        if (!hasBlePermissions()) {
            return emptyList()
        }

        // Останавливаем предыдущее сканирование
        stopScan()

        // Очищаем результаты
        foundDevices.clear()

        // Получаем BLE сканер
        bleScanner = adapter.bluetoothLeScanner
        if (bleScanner == null) {
            return emptyList()
        }

        // Создаем callback
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val deviceName = result.scanRecord?.deviceName ?: device.name ?: "Unknown"

                foundDevices[device.address] = BluetoothDeviceInfo(
                    name = deviceName,
                    address = device.address,
                    rssi = result.rssi,
                    deviceType = getDeviceType(device.type)
                )
            }

            // ✅ Необязательно, но рекомендуется для обработки ошибок
            override fun onScanFailed(errorCode: Int) {
                stopScan()
            }
        }

        // Настраиваем сканирование
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Запускаем сканирование
        bleScanner?.startScan(null, scanSettings, scanCallback)
        isScanning = true

        // Ждем указанное время
        Thread.sleep(scanDuration)

        // Останавливаем сканирование
        stopScan()

        return foundDevices.values.toList()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (isScanning && bleScanner != null && scanCallback != null) {
            bleScanner?.stopScan(scanCallback) // ✅ Передаем конкретный callback
        }
        isScanning = false
        scanCallback = null
        bleScanner = null
    }

    private fun getDeviceType(type: Int): String {
        return when (type) {
            BluetoothDevice.DEVICE_TYPE_LE -> "BLE_DEVICE"
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "CLASSIC"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "DUAL"
            else -> "UNKNOWN"
        }
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