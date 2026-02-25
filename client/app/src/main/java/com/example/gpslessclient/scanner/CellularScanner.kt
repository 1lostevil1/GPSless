package com.example.gpslessclient.scanner

import android.Manifest
import android.content.Context
import android.os.Build
import android.telephony.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.example.gpslessclient.model.CellularNetwork

class CellularScanner(private val context: Context) {

    companion object {
        private const val TAG = "CellularScanner"
    }

    private val telephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    @RequiresApi(Build.VERSION_CODES.P)
    fun getNetworkInfo(): List<CellularNetwork>? {
        Log.d(TAG, "Получение информации о сотовой сети...")

        try {

            val networks = mutableListOf<CellularNetwork>()
            val executor = ContextCompat.getMainExecutor(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                telephonyManager.allCellInfo.forEach { cellInfo ->
                    val network: CellularNetwork? = when (cellInfo) {
                        is CellInfoLte -> {
                            CellularNetwork(
                                networkType = "LTE",
                                signalStrength = cellInfo.cellSignalStrength.dbm,
                                cellId = cellInfo.cellIdentity.ci,
                                mcc = cellInfo.cellIdentity.mccString,
                                mnc = cellInfo.cellIdentity.mncString,
                                locationAreaCode = cellInfo.cellIdentity.tac
                            )
                        }
                        is CellInfoGsm -> {
                            CellularNetwork(
                            networkType = "GSM",
                            signalStrength = cellInfo.cellSignalStrength.dbm,
                            cellId = cellInfo.cellIdentity.cid,
                            mcc = cellInfo.cellIdentity.mccString,
                            mnc = cellInfo.cellIdentity.mncString,
                                locationAreaCode = cellInfo.cellIdentity.lac
                            )
                        }
                        is CellInfoWcdma -> {
                            CellularNetwork(
                                networkType = "WCDMA",
                                signalStrength = cellInfo.cellSignalStrength.dbm,
                                cellId = cellInfo.cellIdentity.cid,
                                mcc = cellInfo.cellIdentity.mccString,
                                mnc = cellInfo.cellIdentity.mncString,
                                locationAreaCode = cellInfo.cellIdentity.lac
                            )
                        }
                        else -> null
                    }
                    network?.let { networks.add(it) }
                }
            }

            return networks

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения информации о сети: ${e.message}")
            return null
        }
    }

}