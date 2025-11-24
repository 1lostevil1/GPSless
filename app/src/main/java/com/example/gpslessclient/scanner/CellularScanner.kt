package com.example.gpslessclient.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.example.gpslessclient.model.CellularData
import com.example.gpslessclient.model.PermissionScope
import com.example.gpslessclient.model.NetworkData

class CellularScanner(context: Context) : BaseNetworkScanner(context, PermissionScope.CELLULAR) {
    @SuppressLint("ServiceCast")
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    @RequiresApi(Build.VERSION_CODES.P)
    override fun scan(): List<NetworkData> {
        if (!checkPermissions()) {
            logPermissionDenied()
            return emptyList()
        }

        return try {
            performCellularScan()
        } catch (e: SecurityException) {
            handleSecurityException(e, "cellular scan")
            emptyList()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE])
    private fun performCellularScan(): List<NetworkData> {
        val networks = mutableListOf<CellularData>()

        val networkOperator = telephonyManager.networkOperator
        val mcc = networkOperator.takeIf { it.length >= 3 }?.substring(0, 3)?.toIntOrNull()
        val mnc = networkOperator.takeIf { it.length > 3 }?.substring(3)?.toIntOrNull()

        val networkType = getNetworkType()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cellInfo = telephonyManager.requestCellInfoUpdate()
            cellInfo?.forEach { cell ->
                networks.add(processCellInfo(cell, mcc, mnc, -1))
            }
        }
        return networks
    }

    private fun processCellInfo(cellInfo: CellInfo, mcc: Int?, mnc: Int?, fallbackSignal: Int): CellularData {
        return when (cellInfo) {
            is CellInfoLte -> {
                val cellIdentity = cellInfo.cellIdentity
                CellularData(
                    type = "LTE",
                    networkOperator = telephonyManager.networkOperatorName,
                    cellId = cellIdentity.ci,
                    locationAreaCode = cellIdentity.tac,
                    mobileCountryCode = cellIdentity.mcc,
                    mobileNetworkCode = cellIdentity.mnc,
                    signalStrength = cellInfo.cellSignalStrength.dbm
                )
            }
            is CellInfoWcdma -> {
                val cellIdentity = cellInfo.cellIdentity
                CellularData(
                    type = "WCDMA",
                    networkOperator = telephonyManager.networkOperatorName,
                    cellId = cellIdentity.cid,
                    locationAreaCode = cellIdentity.lac,
                    mobileCountryCode = cellIdentity.mcc,
                    mobileNetworkCode = cellIdentity.mnc,
                    signalStrength = cellInfo.cellSignalStrength.dbm
                )
            }
            is CellInfoGsm -> {
                val cellIdentity = cellInfo.cellIdentity
                CellularData(
                    type = "GSM",
                    networkOperator = telephonyManager.networkOperatorName,
                    cellId = cellIdentity.cid,
                    locationAreaCode = cellIdentity.lac,
                    mobileCountryCode = cellIdentity.mcc,
                    mobileNetworkCode = cellIdentity.mnc,
                    signalStrength = cellInfo.cellSignalStrength.dbm
                )
            }
            else -> createBasicCellularData(mcc, mnc, fallbackSignal)
        }
    }

    private fun createBasicCellularData(mcc: Int?, mnc: Int?, signalStrength: Int, networkType: String = "UNKNOWN"): CellularData {
        return CellularData(
            type = networkType,
            networkOperator = telephonyManager.networkOperatorName,
            cellId = null,
            locationAreaCode = null,
            mobileCountryCode = mcc,
            mobileNetworkCode = mnc,
            signalStrength = signalStrength
        )
    }


    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private fun getNetworkType(): String {
        return when (telephonyManager.networkType) {
            TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            else -> "UNKNOWN"
        }
    }
}