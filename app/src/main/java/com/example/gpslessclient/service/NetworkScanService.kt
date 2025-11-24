package com.example.gpslessclient.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.example.gpslessclient.R
import com.example.gpslessclient.model.FullScanResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class NetworkScanService : Service() {
    private val binder = ScanBinder()
    private val isScanning = AtomicBoolean(false)
    private var scanJob: Job? = null
    private lateinit var scanCoordinator: NetworkScanCoordinator

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "network_scan_channel"
        private const val SCAN_INTERVAL = 5000L
    }

    inner class ScanBinder : Binder() {
        fun getService(): NetworkScanService = this@NetworkScanService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        scanCoordinator = NetworkScanCoordinator(this)
        createNotificationChannel()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(
        allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]
    )
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isScanning.get()) {
            startPeriodicScanning()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        stopPeriodicScanning()
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(
        allOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        ]
    )
    fun startPeriodicScanning() {
        if (isScanning.getAndSet(true)) {
            Log.i("NetworkScanService", "Scanning already in progress")
            return
        }

        Log.i("NetworkScanService", "Starting periodic scanning")
        updateNotification("Scanning networks...")

        scanJob = CoroutineScope(Dispatchers.IO).launch {
            while (isScanning.get()) {
                try {
                    val result = scanCoordinator.performScan()
                    onScanResult(result)
                    delay(SCAN_INTERVAL)
                } catch (e: SecurityException) {
                    Log.e("NetworkScanService", "Security exception - missing permissions", e)
                    stopPeriodicScanning()
                    break
                } catch (e: Exception) {
                    Log.e("NetworkScanService", "Error during scan", e)
                    delay(SCAN_INTERVAL)
                }
            }
        }
    }

    fun stopPeriodicScanning() {
        if (isScanning.getAndSet(false)) {
            Log.i("NetworkScanService", "Stopping periodic scanning")
            updateNotification("Scanning stopped")
        }
        scanJob?.cancel()
        scanJob = null
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(
        allOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        ]
    )
    fun performSingleScan() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i("NetworkScanService", "Performing single scan")
                updateNotification("Performing single scan...")
                val result = scanCoordinator.performScan()
                onScanResult(result)
                updateNotification("Single scan completed")
            } catch (e: SecurityException) {
                Log.e("NetworkScanService", "Security exception - missing permissions for single scan", e)
                updateNotification("Permission denied for scanning")
            } catch (e: Exception) {
                Log.e("NetworkScanService", "Error during single scan", e)
                updateNotification("Scan failed")
            }
        }
    }

    private fun onScanResult(result: FullScanResult) {
        Log.i("NetworkScanService",
            "Scan completed: \n" +
                    "Location: ${result.location}\n " +
                    "Cellular: ${result.cellularNetworks}\n " +
                    "WiFi: ${result.wifiNetworks}\n" +
                    "Bluetooth: ${result.bluetoothDevices}\n"
        )

        // Можно добавить broadcast для уведомления других компонентов
        sendScanResultBroadcast(result)
    }

    private fun sendScanResultBroadcast(result: FullScanResult) {
        val intent = Intent("com.example.gpslessclient.SCAN_RESULT").apply {
            putExtra("scan_result", result.toString())
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Network Scanning",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background network scanning service"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String = "Network Scanner"): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Monitoring network signals")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification("Network Scanner - $contentText")
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun isScanning(): Boolean = isScanning.get()

    fun getScanCoordinator(): NetworkScanCoordinator = scanCoordinator

    // Методы для управления сервисом из Activity
    fun stopServiceCompletely() {
        stopPeriodicScanning()
        stopForeground(true)
        stopSelf()
    }
}