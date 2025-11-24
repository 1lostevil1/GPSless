package com.example.gpslessclient.scanner

import android.Manifest
import android.content.Context
import android.net.wifi.WifiManager
import androidx.annotation.RequiresPermission
import com.example.gpslessclient.model.PermissionScope
import com.example.gpslessclient.model.WifiData
import com.example.gpslessclient.model.NetworkData

class WifiScanner(context: Context) : BaseNetworkScanner(context, PermissionScope.WIFI) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    override fun scan(): List<NetworkData> {
        if (!checkPermissions()) {
            logPermissionDenied()
            return emptyList()
        }

        return try {
            performWifiScan()
        } catch (e: SecurityException) {
            handleSecurityException(e, "WiFi scan")
            emptyList()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE])
    private fun performWifiScan(): List<NetworkData> {
        val networks = mutableListOf<WifiData>()

        if (!wifiManager.isWifiEnabled) {
            wifiManager.isWifiEnabled = true
            Thread.sleep(1000)
        }

        val success = wifiManager.startScan()
        if (success) {
            val results = wifiManager.scanResults
            results.forEach { scanResult ->
                networks.add(
                    WifiData(
                        ssid = scanResult.SSID.ifEmpty { "Hidden" },
                        bssid = scanResult.BSSID,
                        frequency = scanResult.frequency,
                        capabilities = scanResult.capabilities,
                        signalStrength = scanResult.level
                    )
                )
            }
        }

        return networks.distinctBy { it.id }
    }
}

