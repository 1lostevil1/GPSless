package com.example.gpslessclient.scanner

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.gpslessclient.model.LocationData
import com.example.gpslessclient.model.PermissionScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.example.gpslessclient.model.NetworkData
import kotlinx.coroutines.tasks.await


class LocationScanner(context: Context) : BaseNetworkScanner(context, PermissionScope.LOCATION) {
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun scan(): List<NetworkData> {
        if (!checkPermissions()) {
            logPermissionDenied()
            return emptyList()
        }

        return performLocationScanSync()
    }

    // Асинхронная версия с callback
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun scanAsync(callback: (List<NetworkData>) -> Unit) {
        if (!checkPermissions()) {
            logPermissionDenied()
            callback(emptyList())
            return
        }

        performLocationScanAsync(callback)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun performLocationScanSync(): List<NetworkData> {
        return try {
            kotlinx.coroutines.runBlocking {
                val location = getLastKnownLocation()
                location?.let { listOf(it) } ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e("LocationScanner", "Error getting location", e)
            emptyList()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun performLocationScanAsync(callback: (List<NetworkData>) -> Unit) {
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    val locationData = location?.let {
                        LocationData(
                            latitude = it.latitude,
                            longitude = it.longitude,
                            accuracy = it.accuracy,
                            altitude = it.altitude,
                            provider = it.provider ?: "unknown"
                        )
                    }
                    callback(locationData?.let { listOf(it) } ?: emptyList())
                }
                .addOnFailureListener { exception ->
                    Log.e("LocationScanner", "Error getting last location", exception)
                    callback(emptyList())
                }
        } catch (e: SecurityException) {
            Log.e("LocationScanner", "Security exception getting location", e)
            callback(emptyList())
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private suspend fun getLastKnownLocation(): LocationData? {
        return try {
            val location = fusedLocationClient.lastLocation.await()
            location?.let {
                LocationData(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    accuracy = it.accuracy,
                    altitude = it.altitude,
                    provider = it.provider ?: "unknown"
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}