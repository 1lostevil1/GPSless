// NetworkDataCollector.kt
package com.example.networkscanner.services

import android.Manifest
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.example.gpslessclient.model.GpsData
import com.example.gpslessclient.model.NetworkSnapshot
import com.example.gpslessclient.scanner.BluetoothScanner
import com.example.gpslessclient.scanner.CellularScanner
import com.example.gpslessclient.scanner.LocationManager
import com.example.gpslessclient.scanner.WifiScanner
import kotlinx.coroutines.*

class NetworkDataCollector(private val context: Context) {

    private val wifiScanner = WifiScanner(context)
    private val cellularScanner = CellularScanner(context)
    private val bluetoothScanner = BluetoothScanner(context)
    private val gpsProvider = LocationManager(context)

    private var collectionJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Интервалы в миллисекундах
    private var activeIntervalMillis: Long = 30000 // 30 секунд
    private var backgroundIntervalMillis: Long = 30 * 60 * 1000 // 30 минут

    // Текущее состояние
    private var isInForeground = true // По умолчанию считаем, что приложение активно
    private val handler = Handler(Looper.getMainLooper())
    private var appStateChecker: Runnable? = null

    private val _snapshots = mutableListOf<NetworkSnapshot>()
    val snapshots: List<NetworkSnapshot> get() = _snapshots

    var onNewSnapshot: ((NetworkSnapshot) -> Unit)? = null
    var onCollectionError: ((String) -> Unit)? = null
    var onAppStateChanged: ((isForeground: Boolean) -> Unit)? = null

    fun setScanIntervals(activeSeconds: Int, backgroundMinutes: Int) {
        activeIntervalMillis = (activeSeconds * 1000).toLong()
        backgroundIntervalMillis = (backgroundMinutes * 60 * 1000).toLong()

        // Если сбор активен, перезапускаем с новым интервалом
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
            return true // Уже собираем
        }

        // Запускаем мониторинг состояния приложения
        startAppStateMonitoring()

        // Запускаем сбор данных
        gpsProvider.startListening()
        collectionJob = coroutineScope.launch {
            while (isActive) {
                try {
                    // Определяем текущий интервал
                    val currentInterval = if (isInForeground) {
                        activeIntervalMillis
                    } else {
                        backgroundIntervalMillis
                    }

                    // Собираем данные параллельно
                    collectSingleSnapshotParallel()

                    // Ждем указанный интервал
                    delay(currentInterval)

                } catch (e: CancellationException) {
                    // Корректная отмена
                    break
                } catch (e: Exception) {
                    onCollectionError?.invoke("Ошибка сбора: ${e.message}")

                    // Ждем минимальный интервал при ошибке
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
    }

    // Мониторинг состояния приложения
    private fun startAppStateMonitoring() {
        stopAppStateMonitoring()

        appStateChecker = object : Runnable {
            override fun run() {
                val wasInForeground = isInForeground
                isInForeground = isAppInForeground()

                // Уведомляем, если состояние изменилось
                if (wasInForeground != isInForeground) {
                    onAppStateChanged?.invoke(isInForeground)
                    onCollectionError?.invoke(
                        if (isInForeground)
                            "Приложение перешло в активный режим (интервал: ${activeIntervalMillis/1000} сек)"
                        else
                            "Приложение перешло в фоновый режим (интервал: ${backgroundIntervalMillis/60000} мин)"
                    )
                }

                // Проверяем каждые 2 секунды
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

    // Проверка, находится ли приложение на переднем плане
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

    // Альтернативный метод для Android Q и выше
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun isAppInForegroundUsageStats(): Boolean {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - 1000 * 10, // Последние 10 секунд
            currentTime
        )

        if (stats.isNotEmpty()) {
            val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
            val mostRecent = sortedStats[0]
            return mostRecent.packageName == context.packageName
        }

        return false
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    suspend fun collectSingleSnapshot(): NetworkSnapshot = coroutineScope {
        try {
            collectSingleSnapshotParallel()
        } catch (e: Exception) {
            onCollectionError?.invoke("Ошибка параллельного сбора: ${e.message}")
            throw e
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private suspend fun collectSingleSnapshotParallel(): NetworkSnapshot = coroutineScope {
        // Запускаем все сканирования параллельно
        val locationDeferred = async { gpsProvider.getLocation() }
        val wifiDeferred = async { wifiScanner.scan() }
        val cellularDeferred = async { cellularScanner.getNetworkInfo() }
        val bluetoothDeferred = async { bluetoothScanner.scan() }

        try {
            // Ждем завершения всех задач
            val location = locationDeferred.await()
            val wifiNetworks = wifiDeferred.await()
            val cellularNetwork = cellularDeferred.await()
            val bluetoothDevices = bluetoothDeferred.await()

            // Создаем снимок
            val snapshot = NetworkSnapshot(
                location = location,
                wifiNetworks = wifiNetworks,
                cellularNetwork = cellularNetwork,
                bluetoothDevices = bluetoothDevices
            )

            // Сохраняем
            _snapshots.add(snapshot)

            // Уведомляем подписчиков
            onNewSnapshot?.invoke(snapshot)

            // Логируем текущий режим
            val mode = if (isInForeground) "активный" else "фоновый"
            val interval = if (isInForeground)
                "${activeIntervalMillis/1000} сек"
            else
                "${backgroundIntervalMillis/60000} мин"
            onCollectionError?.invoke("Снимок создан (режим: $mode, интервал: $interval)")

            snapshot
        } catch (e: Exception) {
            // Если есть ошибки, отменяем все остальные задачи
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

    var onError: ((String) -> Unit)? = null
}