package com.example.gpslessclient.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.gpslessclient.model.GpsData
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class LocationManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handlerThread = HandlerThread("LocationThread").apply { start() }
    private val handler = Handler(handlerThread.looper)

    @Volatile
    private var lastLocation: Location? = null

    @Volatile
    private var lastUpdateTime: Long = 0

    private var isListening = false
    private var pendingContinuation: CancellableContinuation<GpsData?>? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            lastUpdateTime = System.currentTimeMillis()
            Log.d("LocationManager", "Location update: acc=${location.accuracy}")

            // Если кто-то ждёт следующего обновления — будим его
            pendingContinuation?.let {
                if (it.isActive) {
                    pendingContinuation = null
                    it.resume(location.toGpsData())
                }
            }
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    fun startListening() {
        if (isListening) return
        if (!hasLocationPermission() || !isGpsEnabled()) return

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,      // обновления раз в секунду
                0f,
                listener,
                handler.looper
            )
            isListening = true
            Log.d("LocationManager", "Listening started")
        } catch (e: SecurityException) {
            Log.e("LocationManager", "Security exception", e)
        }
    }

    fun stopListening() {
        if (!isListening) return
        locationManager.removeUpdates(listener)
        isListening = false
        pendingContinuation?.cancel()
        pendingContinuation = null
        Log.d("LocationManager", "Listening stopped")
    }

    /**
     * Получить местоположение в виде GpsData.
     * @param maxAge максимальный возраст последнего значения (мс). Если null – не учитывается.
     * @param minAccuracy минимальная точность (метры). Если null – не учитывается.
     * @param timeout таймаут ожидания нового обновления (мс), если последнее не подходит.
     */
    suspend fun getLocation(
        maxAge: Long? = 5000,
        minAccuracy: Float? = 50.0f,
        timeout: Long = 10_000
    ): GpsData? {
        // Если слушатель не запущен, запускаем (но лучше вызывать startListening явно)
        if (!isListening) {
            startListening()
        }

        // Проверяем последнее значение
        val last = lastLocation
        val now = System.currentTimeMillis()
        if (last != null) {
            val age = now - lastUpdateTime
            val accuracyOk = minAccuracy == null || last.accuracy <= minAccuracy
            val freshOk = maxAge == null || age <= maxAge
            if (accuracyOk && freshOk) {
                return last.toGpsData()
            }
        }

        // Если не подходит – ждём следующего обновления
        return withTimeoutOrNull(timeout) {
            suspendCancellableCoroutine { cont ->
                pendingContinuation?.cancel()
                pendingContinuation = cont
                cont.invokeOnCancellation {
                    if (pendingContinuation == cont) pendingContinuation = null
                }
            }
        }
    }

    // Преобразование Location в GpsData
    private fun Location.toGpsData(): GpsData = GpsData(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        speed = speed,
        bearing = bearing,
        altitude = altitude
    )

    fun isGpsEnabled(): Boolean = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun getStatus(): String {
        val gpsOk = isGpsEnabled()
        val permOk = hasLocationPermission()
        return "GPS: ${if (gpsOk) "ВКЛ" else "ВЫКЛ"}, Разрешения: ${if (permOk) "ЕСТЬ" else "НЕТ"}"
    }

    fun release() {
        stopListening()
        handlerThread.quitSafely()
    }
}