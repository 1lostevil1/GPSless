package com.example.networkscanner.services

import android.Manifest
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.example.gpslessclient.model.GpsData
import com.example.gpslessclient.model.NetworkSnapshot
import com.example.gpslessclient.scanner.LocationManager
import com.example.gpslessclient.scanner.ScannerManager
import kotlinx.coroutines.*
import java.time.LocalDateTime

class NetworkDataCollector(private val context: Context) {

    private val wifiScanner = ScannerManager.getInstance(context).getWifiScanner()
    private val cellularScanner = ScannerManager.getInstance(context).getCellularScanner()
    private val bluetoothScanner = ScannerManager.getInstance(context).getBluetoothScanner()
    private val gpsProvider = LocationManager(context)

    private var collectionJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Интервалы в миллисекундах
    private var activeIntervalMillis: Long = 30000 // 30 секунд
    private var backgroundIntervalMillis: Long = 30 * 60 * 1000 // 30 минут

    // Текущее состояние
    private var isInForeground = true
    private val handler = Handler(Looper.getMainLooper())
    private var appStateChecker: Runnable? = null

    private val _snapshots = mutableListOf<NetworkSnapshot>()
    val snapshots: List<NetworkSnapshot> get() = _snapshots

    var onNewSnapshot: ((NetworkSnapshot) -> Unit)? = null
    var onCollectionError: ((String) -> Unit)? = null
    var onAppStateChanged: ((isForeground: Boolean) -> Unit)? = null
    var onInfo: ((String) -> Unit)? = null  // Для информационных сообщений

    // Режимы работы
    private var useGps = true
    private var lastGpsLocation: GpsData? = null
    var onGpsUpdate: ((GpsData) -> Unit)? = null

    fun setScanIntervals(activeSeconds: Int, backgroundMinutes: Int) {
        activeIntervalMillis = (activeSeconds * 1000).toLong()
        backgroundIntervalMillis = (backgroundMinutes * 60 * 1000).toLong()

        if (isCollecting()) {
            stopCollecting()
            startCollecting()
        }
    }

    fun setUseGps(useGps: Boolean) {
        this.useGps = useGps
        if (isCollecting()) {
            stopCollecting()
            startCollecting()
        }
    }

    fun isCollecting(): Boolean = collectionJob?.isActive ?: false

    fun canStartCollection(): Boolean {
        return gpsProvider.isGpsEnabled() && gpsProvider.hasLocationPermission()
    }

    fun startCollecting(): Boolean {
        if (!canStartCollection()) {
            onCollectionError?.invoke("GPS выключен или нет разрешений")
            return false
        }

        if (isCollecting()) {
            return true
        }

        // Настраиваем GPS в зависимости от режима
        if (useGps) {
            // Режим GPS: подписываемся на частые обновления
            gpsProvider.setOnLocationUpdateListener(object : LocationManager.OnLocationUpdateListener {
                override fun onLocationUpdated(gpsData: GpsData) {
                    lastGpsLocation = gpsData
                    onGpsUpdate?.invoke(gpsData)
                }
            })
        } else {
            // Сетевой режим: отписываемся от частых обновлений
            gpsProvider.setOnLocationUpdateListener(null)
            lastGpsLocation = null
        }

        startAppStateMonitoring()
        gpsProvider.startListening()

        collectionJob = coroutineScope.launch {
            while (isActive) {
                try {
                    val currentInterval = if (isInForeground) {
                        activeIntervalMillis
                    } else {
                        backgroundIntervalMillis
                    }

                    collectSingleSnapshotParallel()
                    delay(currentInterval)

                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    onCollectionError?.invoke("Ошибка сбора: ${e.message}")
                    delay(minOf(activeIntervalMillis, backgroundIntervalMillis))
                }
            }
        }

        return true
    }

    fun stopCollecting() {
        collectionJob?.cancel()
        collectionJob = null
        stopAppStateMonitoring()
        gpsProvider.stopListening()
        if (useGps) {
            gpsProvider.setOnLocationUpdateListener(null)
        }
        lastGpsLocation = null
    }

    private fun startAppStateMonitoring() {
        stopAppStateMonitoring()

        appStateChecker = object : Runnable {
            override fun run() {
                val wasInForeground = isInForeground
                isInForeground = isAppInForeground()

                if (wasInForeground != isInForeground) {
                    onAppStateChanged?.invoke(isInForeground)
                    // Используем Log.d для информационных сообщений
                    val message = if (isInForeground)
                        "Приложение перешло в активный режим (интервал: ${activeIntervalMillis/1000} сек)"
                    else
                        "Приложение перешло в фоновый режим (интервал: ${backgroundIntervalMillis/60000} мин)"

                    Log.d("NetworkDataCollector", message)
                    onInfo?.invoke(message)
                }

                handler.postDelayed(this, 2000)
            }
        }

        handler.post(appStateChecker!!)
    }

    private fun stopAppStateMonitoring() {
        appStateChecker?.let {
            handler.removeCallbacks(it)
            appStateChecker = null
        }
    }

    private fun isAppInForeground(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            isAppInForegroundModern()
        } else {
            isAppInForegroundLegacy()
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun isAppInForegroundModern(): Boolean {
        val appProcessInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(appProcessInfo)
        return (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE)
    }

    @Suppress("DEPRECATION")
    private fun isAppInForegroundLegacy(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningTasks = activityManager.getRunningTasks(1)
        if (runningTasks.isNotEmpty()) {
            val topActivity = runningTasks[0].topActivity
            return topActivity?.packageName == context.packageName
        }
        return false
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private suspend fun collectSingleSnapshotParallel(): NetworkSnapshot = coroutineScope {
        val locationDeferred = async {
            if (useGps) {
                lastGpsLocation ?: gpsProvider.getLocation()
            } else {
                gpsProvider.getLocation()
            }
        }
        val wifiDeferred = async { wifiScanner.scan() }
        val cellularDeferred = async { cellularScanner.getNetworkInfo() }
        val bluetoothDeferred = async { bluetoothScanner.scan() }

        try {
            val location = locationDeferred.await()
            val wifiNetworks = wifiDeferred.await()
            val cellularNetwork = cellularDeferred.await()
            val bluetoothDevices = bluetoothDeferred.await()

            val snapshot = NetworkSnapshot(
                snapshotTime = LocalDateTime.now().toString(),
                location = location,
                wifiNetworks = wifiNetworks,
                cellularNetwork = cellularNetwork,
                bluetoothDevices = bluetoothDevices
            )

            _snapshots.add(snapshot)
            onNewSnapshot?.invoke(snapshot)
            
            val mode = if (isInForeground) "активный" else "фоновый"
            val interval = if (isInForeground)
                "${activeIntervalMillis/1000} сек"
            else
                "${backgroundIntervalMillis/60000} мин"

            val message = "Снимок создан (режим: $mode, интервал: $interval)"
            Log.d("NetworkDataCollector", message)
            onInfo?.invoke(message)

            snapshot
        } catch (e: Exception) {
            locationDeferred.cancel()
            wifiDeferred.cancel()
            cellularDeferred.cancel()
            bluetoothDeferred.cancel()
            throw e
        }
    }

    fun clearData() {
        _snapshots.clear()
    }

    fun getGpsStatus(): String {
        return gpsProvider.getStatus()
    }

    fun isGpsEnabled(): Boolean {
        return gpsProvider.isGpsEnabled()
    }

    fun hasLocationPermission(): Boolean {
        return gpsProvider.hasLocationPermission()
    }

    fun dispose() {
        stopCollecting()
        coroutineScope.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}