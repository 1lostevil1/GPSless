package com.example.gpslessclient

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gpslessclient.model.NetworkSnapshot
import com.example.networkscanner.services.NetworkDataCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggleGps: Button
    private lateinit var tvStatus: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SnapshotAdapter

    private lateinit var dataCollector: NetworkDataCollector
    private var isCollecting = false
    private var lastErrorTime: Long = 0
    private val ERROR_COOLDOWN_MS = 5000L


    private val SERVER_IP = "192.168.0.13" // <-- ЗАМЕНИТЕ НА СВОЙ IP
    private val SERVER_PORT = "8080"
    private val SERVER_URL = "http://$SERVER_IP:$SERVER_PORT/api/track/save"
    // ========================

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "MainActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupRecyclerView()

        dataCollector = NetworkDataCollector(this)
        dataCollector.onNewSnapshot = { snapshot ->
            runOnUiThread {
                adapter.addSnapshot(snapshot)
                updateStatusText(snapshot)

                if(adapter.itemCount == 2) {
                    sendSnapshotToServer(adapter.getSnapshots().toList())
                    Log.i(TAG, "Отправлено на сервер: $SERVER_IP")
                }
            }
        }

        dataCollector.onError = { error ->
            val now = System.currentTimeMillis()
            if (now - lastErrorTime > ERROR_COOLDOWN_MS) {
                lastErrorTime = now
                runOnUiThread {
                    Toast.makeText(this, "Ошибка: $error", Toast.LENGTH_SHORT).show()
                }
            }
            Log.e(TAG, "Ошибка сбора данных: $error")
        }

        updateButtonAppearance()
        checkAllRequirements()
    }

    private fun initViews() {
        btnToggleGps = findViewById(R.id.btnToggleGps)
        tvStatus = findViewById(R.id.tvStatus)
        recyclerView = findViewById(R.id.recyclerView)

        btnToggleGps.setOnClickListener {
            toggleDataCollection()
        }
    }

    private fun setupRecyclerView() {
        adapter = SnapshotAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun toggleDataCollection() {
        val requirements = checkAllRequirements()

        if (!requirements.isGpsEnabled) {
            showToastWithAction("Включите GPS в настройках устройства") {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            return
        }

        if (!requirements.isWifiEnabled) {
            showToastWithAction("Включите Wi-Fi для сканирования сетей") {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            return
        }

        if (!requirements.isBluetoothEnabled) {
            showToastWithAction("Включите Bluetooth для сканирования устройств") {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            return
        }

        if (!requirements.hasLocationPermission) {
            requestLocationPermissions()
            return
        }

        if (!requirements.hasBluetoothPermission) {
            requestBluetoothPermissions()
            return
        }

        isCollecting = !isCollecting

        if (isCollecting) {
            val started = dataCollector.startCollecting()
            if (started) {
                Toast.makeText(this, "Сбор данных начат", Toast.LENGTH_SHORT).show()
            } else {
                isCollecting = false
                Toast.makeText(this, "Не удалось начать сбор данных", Toast.LENGTH_SHORT).show()
            }
        } else {
            dataCollector.stopCollecting()
            Toast.makeText(this, "Сбор данных остановлен", Toast.LENGTH_SHORT).show()
        }

        updateButtonAppearance()
    }

    // Функция отправки снапшота на сервер
    private fun sendSnapshotToServer(snapshots: List<NetworkSnapshot>) {
        coroutineScope.launch {
            try {
                val json = gson.toJson(snapshots)
                Log.d(TAG, "Отправка на сервер: $json")

                val requestBody = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(SERVER_URL)
                    .post(requestBody)
                    .build()

                val response = withContext(Dispatchers.IO) {
                    okHttpClient.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    Log.d(TAG, "Снапшот успешно отправлен на сервер")
                } else {
                    Log.e(TAG, "Ошибка отправки: ${response.code} - ${response.body?.string()}")
                }

                response.close()

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при отправке на сервер: ${e.message}")
            }
        }
    }

    private data class RequirementsStatus(
        val isGpsEnabled: Boolean,
        val isWifiEnabled: Boolean,
        val isBluetoothEnabled: Boolean,
        val hasLocationPermission: Boolean,
        val hasBluetoothPermission: Boolean
    )

    private fun checkAllRequirements(): RequirementsStatus {
        val isGpsEnabled = dataCollector.isGpsEnabled()
        val isWifiEnabled = (getSystemService(WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true
        val isBluetoothEnabled = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        val hasLocationPermission = dataCollector.hasLocationPermission()

        val hasBluetoothPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            hasLocationPermission // На старых Android используем разрешение локации
        }

        tvStatus.text = buildString {
            append("Требования для работы:\n")
            append("GPS: ${if (isGpsEnabled) "✅" else "❌"}\n")
            append("Wi-Fi: ${if (isWifiEnabled) "✅" else "❌"}\n")
            append("Bluetooth: ${if (isBluetoothEnabled) "✅" else "❌"}\n")
            append("Разрешения локации: ${if (hasLocationPermission) "✅" else "❌"}\n")
            append("Разрешения Bluetooth: ${if (hasBluetoothPermission) "✅" else "❌"}\n")
        }

        return RequirementsStatus(
            isGpsEnabled, isWifiEnabled, isBluetoothEnabled,
            hasLocationPermission, hasBluetoothPermission
        )
    }

    private fun showToastWithAction(message: String, action: () -> Unit) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        // Даем время пользователю прочитать Toast, затем выполняем действие
        btnToggleGps.postDelayed({
            action()
        }, 1500)
    }

    private fun updateButtonAppearance() {
        val requirements = checkAllRequirements()
        val allRequirementsMet = requirements.isGpsEnabled &&
                requirements.isWifiEnabled &&
                requirements.isBluetoothEnabled &&
                requirements.hasLocationPermission &&
                requirements.hasBluetoothPermission

        if (isCollecting) {
            btnToggleGps.text = "Остановить сбор (активно)"
            btnToggleGps.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.holo_red_light)
            )
        } else {
            btnToggleGps.text = if (allRequirementsMet) {
                "Начать сбор данных"
            } else {
                "Проверить настройки"
            }
            btnToggleGps.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.darker_gray)
            )
        }
    }

    private fun updateStatusText(snapshot: NetworkSnapshot) {
        tvStatus.text = buildString {
            append("Последний снимок:\n")
            append("Время: ${DateFormat.format("HH:mm:ss", snapshot.timestamp)}\n")
            snapshot.location?.let {
                append("Координаты: ${String.format("%.6f", it.latitude)}, ${String.format("%.6f", it.longitude)}\n")
            }
            append("Wi-Fi сетей: ${snapshot.wifiNetworks}\n")
            append("СОТ сетей: ${snapshot.cellularNetworks}\n")
            append("Bluetooth устройств: ${snapshot.bluetoothDevices}\n")
        }
        Log.i(TAG, "Новый снимок: ${tvStatus.text}")
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                BLUETOOTH_PERMISSION_REQUEST_CODE
            )
        } else {
            // На старых версиях используем разрешения локации для Bluetooth
            requestLocationPermissions()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    checkAllRequirements()
                    if (!isCollecting) {
                        toggleDataCollection()
                    }
                } else {
                    Toast.makeText(this, "Разрешения локации необходимы для работы", Toast.LENGTH_LONG).show()
                }
            }

            BLUETOOTH_PERMISSION_REQUEST_CODE -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    checkAllRequirements()
                    if (!isCollecting) {
                        toggleDataCollection()
                    }
                } else {
                    Toast.makeText(this, "Разрешения Bluetooth необходимы для сканирования", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // При возвращении в приложение проверяем настройки
        checkAllRequirements()
        updateButtonAppearance()
    }

    override fun onDestroy() {
        super.onDestroy()
        dataCollector.stopCollecting()
    }
}