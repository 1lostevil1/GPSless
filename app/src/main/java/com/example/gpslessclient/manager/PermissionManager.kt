package com.example.gpslessclient.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.gpslessclient.model.PermissionScope
import kotlin.to

class PermissionManager(private val context: Context) {

    companion object {
        private val SCOPE_PERMISSIONS = mapOf(
            PermissionScope.CELLULAR to arrayOf(
                Manifest.permission.READ_PHONE_STATE
            ),
            PermissionScope.WIFI to arrayOf(
                Manifest.permission.ACCESS_WIFI_STATE
            ),
            PermissionScope.BLUETOOTH to arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                emptyArray()
            },
            PermissionScope.LOCATION to arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    fun hasPermissionsFor(scope: PermissionScope): Boolean {
        return SCOPE_PERMISSIONS[scope]?.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        } ?: false
    }

    fun getMissingPermissionsFor(scope: PermissionScope): Array<String> {
        return SCOPE_PERMISSIONS[scope]?.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }?.toTypedArray() ?: emptyArray()
    }

    fun hasAnyNetworkPermissions(): Boolean {
        return hasPermissionsFor(PermissionScope.CELLULAR) ||
                hasPermissionsFor(PermissionScope.WIFI) ||
                hasPermissionsFor(PermissionScope.BLUETOOTH)
    }
}
