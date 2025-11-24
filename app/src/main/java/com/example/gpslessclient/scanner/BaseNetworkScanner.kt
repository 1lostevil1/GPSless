package com.example.gpslessclient.scanner

import android.content.Context
import android.util.Log
import com.example.gpslessclient.manager.PermissionManager
import com.example.gpslessclient.model.NetworkData
import com.example.gpslessclient.model.PermissionScope

abstract class BaseNetworkScanner(
    protected val context: Context,
    private val scope: PermissionScope
) {
    protected val permissionManager = PermissionManager(context)

    protected fun checkPermissions(): Boolean {
        return permissionManager.hasPermissionsFor(scope)
    }

    protected fun logPermissionDenied() {
        Log.w(javaClass.simpleName, "Permissions denied for scope: $scope")
    }

    protected fun handleSecurityException(e: SecurityException, operation: String) {
        Log.e(javaClass.simpleName, "SecurityException during $operation", e)
    }

    abstract fun scan(): List<NetworkData>
}