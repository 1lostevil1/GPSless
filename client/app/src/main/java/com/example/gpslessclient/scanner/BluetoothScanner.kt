package com.example.gpslessclient.scanner

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.gpslessclient.model.BluetoothDeviceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconConsumer
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.RangeNotifier
import org.altbeacon.beacon.Region

class BluetoothScanner(context: Context) {

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val beaconManager: BeaconManager = BeaconManager.getInstanceForApplication(appContext)

    // iBeacon layout (Apple manufacturer 0x004C => bytes 4c00, prefix 0x0215)
    private val IBEACON_LAYOUT =
        "m:0-1=4c00,m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"

    private val regionAll = Region("all-recognized-beacons", null, null, null)
    private val scanMutex = Mutex()

    @Volatile private var bound = false
    private var connectSignal: CompletableDeferred<Unit>? = null

    @Volatile private var ranging = false
    private var activeNotifier: RangeNotifier? = null

    private val consumer = object : BeaconConsumer {
        override fun getApplicationContext(): Context = appContext

        override fun unbindService(serviceConnection: ServiceConnection) {
            appContext.unbindService(serviceConnection)
        }

        override fun bindService(intent: Intent, serviceConnection: ServiceConnection, mode: Int): Boolean {
            return appContext.bindService(intent, serviceConnection, mode)
        }

        override fun onBeaconServiceConnect() {
            connectSignal?.complete(Unit)
        }
    }

    init {
        // Максимум “популярных” форматов со статичным/условно статичным ID:
        // - iBeacon (статичный)
        // - AltBeacon (статичный)
        // - Eddystone UID (статичный)
        // - Eddystone URL (условно статичный, если URL не меняется)
        // - URI Beacon (условно статичный, устаревший, но встречается)
        //
        // НЕ добавляем TLM (телеметрия) и EID (динамический rotating ID).
        beaconManager.beaconParsers.clear()
        beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout(IBEACON_LAYOUT))
        beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout(BeaconParser.ALTBEACON_LAYOUT))
        beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_UID_LAYOUT))
        beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_URL_LAYOUT))
        beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout(BeaconParser.URI_BEACON_LAYOUT))

        // Быстрый поиск
        beaconManager.foregroundScanPeriod = 1100L
        beaconManager.foregroundBetweenScanPeriod = 0L
    }


    suspend fun scan(scanDurationMs: Long = 10_000): List<BluetoothDeviceInfo> {
        return scanMutex.withLock {
            withContext(Dispatchers.Main) {
                prechecksOrThrow()
                ensureBound()

                internalStopRangingIfNeeded()

                val found = LinkedHashMap<String, BluetoothDeviceInfo>()

                val notifier = RangeNotifier { beacons, _ ->
                    for (b in beacons) {
                        val beaconId = buildStaticBeaconId(b) ?: continue
                        val info = BluetoothDeviceInfo(
                            beaconId = beaconId,
                            name = runCatching { b.bluetoothName }.getOrNull(),
                            address = runCatching { b.bluetoothAddress }.getOrNull() ?: "unknown",
                            rssi = b.rssi
                        )
                        synchronized(found) {
                            found[beaconId] = info
                        }
                    }
                }

                activeNotifier = notifier
                beaconManager.addRangeNotifier(notifier)
                beaconManager.startRangingBeaconsInRegion(regionAll)
                ranging = true

                try {
                    delay(scanDurationMs)
                } finally {
                    internalStopRangingIfNeeded()
                }

                synchronized(found) {
                    found.values.toList()
                }
            }
        }
    }

    fun stopScan() {
        runCatching { beaconManager.stopRangingBeaconsInRegion(regionAll) }
        activeNotifier?.let { runCatching { beaconManager.removeRangeNotifier(it) } }
        activeNotifier = null
        ranging = false
    }

    fun close() {
        stopScan()
        if (bound) {
            runCatching { beaconManager.unbind(consumer) }
            bound = false
        }
    }

    // -------------------- Internal --------------------

    private fun prechecksOrThrow() {
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            throw IllegalStateException("BLE is not supported on this device")
        }
        val a = adapter ?: throw IllegalStateException("BluetoothAdapter is null")
        if (!a.isEnabled) throw IllegalStateException("Bluetooth is disabled")
        if (!hasBlePermissions()) throw SecurityException("Missing BLE permissions")

        // Android 6–11: для BLE-скана часто нужен включенный Location toggle
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !isLocationEnabled()) {
            throw IllegalStateException("Location services are disabled (required for BLE scan on Android < 12)")
        }
    }

    private suspend fun ensureBound() {
        if (bound) return
        connectSignal = CompletableDeferred()
        beaconManager.bind(consumer)
        bound = true
        connectSignal?.await()
    }

    private fun internalStopRangingIfNeeded() {
        if (ranging) runCatching { beaconManager.stopRangingBeaconsInRegion(regionAll) }
        activeNotifier?.let { runCatching { beaconManager.removeRangeNotifier(it) } }
        activeNotifier = null
        ranging = false
    }

    /**
     * Возвращает стабильный ID для “статичных/условно статичных” маячков.
     * TLM/EID и прочее “динамическое” сюда не включаем.
     */
    private fun buildStaticBeaconId(b: Beacon): String? {
        // iBeacon
        if (b.manufacturer == 0x004C && b.beaconTypeCode == 0x0215) {
            return "ibeacon:${b.id1}:${b.id2}:${b.id3}"
        }

        // AltBeacon
        if (b.beaconTypeCode == 0xBEAC) {
            return "altbeacon:${b.id1}:${b.id2}:${b.id3}"
        }

        // Eddystone UID (static)
        if (b.serviceUuid == 0xFEAA && b.beaconTypeCode == 0x00) {
            return "eddystone_uid:${b.id1}${b.id2}"
        }

        // Eddystone URL (URL is usually in id1; static if beacon doesn't rotate URL)
        if (b.serviceUuid == 0xFEAA && b.beaconTypeCode == 0x10) {
            return "eddystone_url:${b.id1}"
        }

        // URI Beacon (fed8)
        if (b.serviceUuid == 0xFED8 && b.beaconTypeCode == 0x00) {
            return "uribeacon:${b.id1}"
        }

        // Eddystone TLM (0x20) intentionally ignored: telemetry, no stable ID
        // Eddystone EID (0x30) intentionally ignored: rotating ID

        return null
    }

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isLocationEnabled(): Boolean {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }


}