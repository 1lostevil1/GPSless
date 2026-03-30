// utils/AdminSnapshotCollector.kt
package com.example.gpslessclient.scanner

import android.content.Context
import android.util.Log
import com.example.gpslessclient.model.GpsData
import com.example.gpslessclient.model.NetworkSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.collections.emptyList

class AdminSnapshotCollector(private val context: Context) {

    private val wifiScanner = ScannerManager.getInstance(context).getWifiScanner()
    private val cellularScanner = ScannerManager.getInstance(context).getCellularScanner()
    private val bluetoothScanner = ScannerManager.getInstance(context).getBluetoothScanner()

    companion object {
        private const val TAG = "AdminSnapshotCollector"
    }

    /**
     * Собрать снапшот в указанных координатах
     * @param lat широта
     * @param lon долгота
     * @return NetworkSnapshot с собранными данными
     */
    suspend fun collectSnapshot(lat: Double, lon: Double): NetworkSnapshot = withContext(Dispatchers.IO) {
        Log.d(TAG, "Collecting admin snapshot at: $lat, $lon")

        // Запускаем все сканирования параллельно
        val wifiDeferred = async {
            try {
                wifiScanner.scan()
            } catch (e: Exception) {
                Log.e(TAG, "WiFi scan failed: ${e.message}")
                emptyList()
            }
        }

        val cellularDeferred = async {
            try {
                cellularScanner.getNetworkInfo()
            } catch (e: Exception) {
                Log.e(TAG, "Cellular scan failed: ${e.message}")
                null
            }
        }

        val bluetoothDeferred = async {
            try {
                bluetoothScanner.scan()
            } catch (e: Exception) {
                Log.e(TAG, "Bluetooth scan failed: ${e.message}")
                emptyList()
            }
        }

        val wifi = wifiDeferred.await()
        val cell = cellularDeferred.await()
        val ble = bluetoothDeferred.await()


        Log.d(TAG, "Admin snapshot collected: WiFi=${wifi}, Cellular=${cell != null}, Bluetooth=${ble}")

        // Создаем снапшот с указанными координатами
        NetworkSnapshot(
            snapshotTime = LocalDateTime.now().toString(),
            location = GpsData(
                latitude = lat,
                longitude = lon,
                accuracy = 0.0f,
                speed = 0.0f,
                bearing = 0.0f,
                altitude = 0.0
            ),
            wifiNetworks = wifi ,
            cellularNetwork = cell,
            bluetoothDevices = ble
        )
    }
}