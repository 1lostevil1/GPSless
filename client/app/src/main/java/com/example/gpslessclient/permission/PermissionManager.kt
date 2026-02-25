package com.example.gpslessclient.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionManager {

    private const val PERMISSION_REQUEST_CODE = 100

    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        // Разрешения для местоположения
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        // Разрешения для сотовых сетей
        permissions.add(Manifest.permission.READ_PHONE_STATE)

        // Разрешения для Bluetooth
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // Для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions.toTypedArray()
    }

    fun hasAllPermissions(context: Context): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermissions(activity: Activity) {
        val permissionsToRequest = getRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) !=
                    PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                permissionsToRequest,
                PERMISSION_REQUEST_CODE
            )
        }
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        onAllGranted: () -> Unit,
        onDenied: (List<String>) -> Unit
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermissions = mutableListOf<String>()

            permissions.forEachIndexed { index, permission ->
                if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permission)
                }
            }

            if (deniedPermissions.isEmpty()) {
                onAllGranted()
            } else {
                onDenied(deniedPermissions)
            }
        }
    }
}