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

    private var updateListener: OnLocationUpdateListener? = null

    interface OnLocationUpdateListener {
        fun onLocationUpdated(gpsData: GpsData)
    }

    fun setOnLocationUpdateListener(listener: OnLocationUpdateListener?) {
        updateListener = listener
    }

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val oldLocation = lastLocation
            lastLocation = location
            lastUpdateTime = System.currentTimeMillis()

            // Проверяем, что координаты действительно изменились
            val isDifferent = oldLocation == null ||
                    (Math.abs(oldLocation.latitude - location.latitude) > 0.000001 ||
                            Math.abs(oldLocation.longitude - location.longitude) > 0.000001)

            if (isDifferent) {
                Log.d("LocationManager", "GPS новое местоположение: provider=${location.provider}, acc=${location.accuracy}, lat=${location.latitude}, lon=${location.longitude}")
                updateListener?.onLocationUpdated(location.toGpsData())
            } else {
                Log.d("LocationManager", "GPS обновление без изменения координат")
            }

            pendingContinuation?.let {
                if (it.isActive) {
                    pendingContinuation = null
                    it.resume(location.toGpsData())
                }
            }
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            Log.d("LocationManager", "Status changed: provider=$provider, status=$status")
        }

        override fun onProviderEnabled(provider: String) {
            Log.d("LocationManager", "Provider enabled: $provider")
        }

        override fun onProviderDisabled(provider: String) {
            Log.d("LocationManager", "Provider disabled: $provider")
        }
    }

    fun startListening() {
        if (isListening) {
            Log.d("LocationManager", "Already listening")
            return
        }

        if (!hasLocationPermission()) {
            Log.e("LocationManager", "No location permission")
            return
        }

        if (!isGpsEnabled()) {
            Log.e("LocationManager", "GPS is disabled")
            return
        }

        try {
            // Запрашиваем обновления GPS с минимальными интервалами
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,      // минимальное время между обновлениями (1 сек)
                1f,         // минимальное расстояние (1 метр) - чтобы получать даже небольшие перемещения
                listener,
                handler.looper
            )
            isListening = true
            Log.d("LocationManager", "GPS listening started (minDistance=1m)")

            // Не используем getLastKnownLocation, чтобы не показывать старые координаты
            // Ждем реального GPS фикса
        } catch (e: SecurityException) {
            Log.e("LocationManager", "Security exception: ${e.message}")
        } catch (e: Exception) {
            Log.e("LocationManager", "Error starting GPS: ${e.message}")
        }
    }

    fun stopListening() {
        if (!isListening) return
        locationManager.removeUpdates(listener)
        isListening = false
        pendingContinuation?.cancel()
        pendingContinuation = null
        Log.d("LocationManager", "GPS listening stopped")
    }

    suspend fun getLocation(
        maxAge: Long? = null,
        minAccuracy: Float? = null,
        timeout: Long = 10_000
    ): GpsData? {
        if (!isListening) {
            startListening()
        }

        val last = lastLocation
        val now = System.currentTimeMillis()

        // Всегда ждем новый GPS фикс, не используем кэш
        // Если последнее обновление было больше 3 секунд назад - ждем новое
        if (last != null && (now - lastUpdateTime) < 3000) {
            Log.d("LocationManager", "Using recent location: age=${now - lastUpdateTime}ms")
            return last.toGpsData()
        }

        Log.d("LocationManager", "Waiting for new GPS fix...")
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

    private fun Location.toGpsData(): GpsData = GpsData(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        speed = speed,
        bearing = bearing,
        altitude = altitude
    )

    fun isGpsEnabled(): Boolean {
        val enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        Log.d("LocationManager", "GPS enabled: $enabled")
        return enabled
    }

    fun hasLocationPermission(): Boolean {
        val has = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        Log.d("LocationManager", "Location permission: $has")
        return has
    }

    fun getStatus(): String {
        val gpsOk = isGpsEnabled()
        val permOk = hasLocationPermission()
        val hasLastLocation = lastLocation != null
        val lastLocationAge = if (hasLastLocation) System.currentTimeMillis() - lastUpdateTime else 0
        return "GPS: ${if (gpsOk) "ВКЛ" else "ВЫКЛ"}, Разрешения: ${if (permOk) "ЕСТЬ" else "НЕТ"}, Последняя позиция: ${if (hasLastLocation) "${lastLocationAge}мс назад" else "нет"}"
    }

    fun forceUpdate() {
        lastLocation = null
        lastUpdateTime = 0
        Log.d("LocationManager", "Force reset location cache")
    }

    fun release() {
        stopListening()
        handlerThread.quitSafely()
    }
}