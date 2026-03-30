package com.example.gpslessclient.scanner

import android.content.Context

class ScannerManager private constructor(context: Context) {
    private val wifiScanner: WifiScanner
    private val cellularScanner: CellularScanner
    private val bluetoothScanner: BluetoothScanner

    init {
        val appContext = context.applicationContext
        wifiScanner = WifiScanner(appContext)
        cellularScanner = CellularScanner(appContext)
        bluetoothScanner = BluetoothScanner(appContext)
    }

    // Геттеры
    fun getWifiScanner(): WifiScanner = wifiScanner
    fun getCellularScanner(): CellularScanner = cellularScanner
    fun getBluetoothScanner(): BluetoothScanner = bluetoothScanner

    companion object {
        @Volatile
        private var instance: ScannerManager? = null

        fun getInstance(context: Context): ScannerManager {
            return instance ?: synchronized(this) {
                instance ?: ScannerManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}