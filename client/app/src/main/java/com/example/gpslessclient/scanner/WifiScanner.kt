package com.example.gpslessclient.scanner

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.gpslessclient.model.WifiNetwork

class WifiScanner(private val context: Context) {

    companion object {
        private const val TAG = "WifiScanner"
    }

    private val wifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    fun scan(throwOnError: Boolean = false): List<WifiNetwork> {
        Log.d(TAG, "Сканирование Wi-Fi сетей...")

        try {
            if (!wifiManager.isWifiEnabled) {
                val error = "Wi-Fi выключен"
                if (throwOnError) throw IllegalStateException(error)
                Log.e(TAG, error)
                return emptyList()
            }

            val success = wifiManager.startScan()
            if (!success) {
                val error = "Не удалось запустить сканирование Wi-Fi"
                if (throwOnError) throw IllegalStateException(error)
                Log.e(TAG, error)
                return emptyList()
            }

            // Короткая пауза для получения результатов
            Thread.sleep(2000)

            val scanResults = wifiManager.scanResults ?: emptyList()
            Log.d(TAG, "Найдено Wi-Fi сетей: ${scanResults.size}")

            return scanResults.mapNotNull { scanResult ->
                try {
                    WifiNetwork(
                        ssid = scanResult.SSID.ifEmpty { "<hidden>" },
                        bssid = scanResult.BSSID ?: "unknown",
                        signalLevel = scanResult.level,
                        frequency = scanResult.frequency,
                        capabilities = scanResult.capabilities ?: "",
                        channel = calculateChannel(scanResult.frequency),
                        isSecure = scanResult.capabilities?.contains("WPA") == true ||
                                scanResult.capabilities?.contains("WEP") == true ||
                                scanResult.capabilities?.contains("EAP") == true
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Ошибка преобразования сети: ${e.message}")
                    null
                }
            }.sortedByDescending { it.signalLevel }

        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            val error = "Сканирование прервано"
            if (throwOnError) throw IllegalStateException(error)
            return emptyList()
        } catch (e: Exception) {
            val error = "Ошибка сканирования Wi-Fi: ${e.message}"
            if (throwOnError) throw IllegalStateException(error)
            Log.e(TAG, error)
            return emptyList()
        }
    }
    private fun calculateChannel(frequency: Int): Int {
        return when {
            frequency in 2412..2484 -> (frequency - 2412) / 5 + 1
            frequency in 5160..5885 -> (frequency - 5160) / 5 + 32
            else -> 0
        }
    }

    fun isWifiEnabled(): Boolean {
        return try {
            wifiManager.isWifiEnabled
        } catch (e: Exception) {
            false
        }
    }
}