package com.example.gpslessclient

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gpslessclient.api.RetrofitClient
import com.example.gpslessclient.api.TokenManager
import com.example.gpslessclient.model.NetworkSnapshot
import com.example.gpslessclient.model.UserLocation
import com.example.gpslessclient.scanner.AdminSnapshotCollector
import com.example.networkscanner.services.NetworkDataCollector
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.osmdroid.config.Configuration
import org.osmdroid.library.BuildConfig
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggleGps: Button
    private lateinit var tvStatus: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SnapshotAdapter
    private lateinit var mapView: MapView
    private lateinit var switchUseGps: Switch
    private lateinit var btnCenterMap: Button
    private lateinit var btnLogout: Button

    private var dataCollector: NetworkDataCollector? = null
    private lateinit var tokenManager: TokenManager
    private lateinit var adminCollector: AdminSnapshotCollector
    private var isCollecting = false
    private var lastErrorTime: Long = 0
    private val ERROR_COOLDOWN_MS = 5000L

    private var useGps = true
    private var currentLocationMarker: Marker? = null
    private var lastKnownLocation: UserLocation? = null
    private var hasReceivedFirstGps = false
    private var isAdmin = false

    private var snapshotBatch = mutableListOf<NetworkSnapshot>()
    private val BATCH_SIZE = 5

    private var isForeground = false;

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

        // Инициализация Retrofit
        RetrofitClient.init(this)

        // Проверка авторизации
        tokenManager = TokenManager(this)
        val token = tokenManager.getAccessToken()
        if (token.isNullOrEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Инициализация админского коллектора
        adminCollector = AdminSnapshotCollector(this)

        Configuration.getInstance().load(
            applicationContext,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID)

        setContentView(R.layout.activity_main)

        initViews()
        setupRecyclerView()
        setupMap()
        setupMapDoubleClickListener()

        // Инициализируем dataCollector ТОЛЬКО после проверки токена
        dataCollector = NetworkDataCollector(this)
        dataCollector?.setScanIntervals(30, 30)

        // Подписка на реальные GPS обновления
        dataCollector?.onGpsUpdate = { gpsData ->
            runOnUiThread {
                if (!hasReceivedFirstGps) {
                    hasReceivedFirstGps = true
                    Toast.makeText(this, "GPS получен", Toast.LENGTH_SHORT).show()
                }
                updateMapLocation(gpsData.latitude, gpsData.longitude)
                Log.d(TAG, "GPS обновление: ${gpsData.latitude}, ${gpsData.longitude}, точность: ${gpsData.accuracy}")
            }
        }

        dataCollector?.onNewSnapshot = { snapshot ->
            runOnUiThread {
                adapter.addSnapshot(snapshot)
                updateStatusText(snapshot)

                if (useGps) {
                    if (snapshot.location != null) {
                        snapshotBatch.add(snapshot)
                        if (snapshotBatch.size >= BATCH_SIZE || isForeground ) {
                            sendSnapshotsToServer(snapshotBatch.toList())
                            snapshotBatch.clear()
                            adapter.clearSnapshots()
                        }
                    } else {
                        Log.d(TAG, "Снапшот без GPS - пропущен")
                    }
                } else {
                    requestLocationFromServer(snapshot)
                }
            }
        }

        dataCollector?.onCollectionError = { error ->
            val now = System.currentTimeMillis()
            if (now - lastErrorTime > ERROR_COOLDOWN_MS) {
                lastErrorTime = now
                runOnUiThread {
                    Toast.makeText(this, "Ошибка: $error", Toast.LENGTH_SHORT).show()
                }
            }
            Log.e(TAG, "Ошибка: $error")
        }

        dataCollector?.onAppStateChanged = { isForeground ->
            runOnUiThread {
                Log.d(TAG, "Состояние: ${if (isForeground) "активен" else "фон"}")
                this.isForeground = isForeground
            }
        }

        dataCollector?.onInfo = { info ->
            runOnUiThread {
                Log.d(TAG, info)
            }
        }

        updateButtonAppearance()
        checkAllRequirements()

        // Загружаем информацию о пользователе
        loadUserInfo()

        Toast.makeText(this, "Ожидание GPS сигнала...", Toast.LENGTH_LONG).show()
    }

    private fun initViews() {
        btnToggleGps = findViewById(R.id.btnToggleGps)
        tvStatus = findViewById(R.id.tvStatus)
        recyclerView = findViewById(R.id.recyclerView)
        mapView = findViewById(R.id.mapView)
        switchUseGps = findViewById(R.id.switchUseGps)
        btnCenterMap = findViewById(R.id.btnCenterMap)
        btnLogout = findViewById(R.id.btnLogout)

        btnToggleGps.setOnClickListener {
            toggleDataCollection()
        }

        switchUseGps.setOnCheckedChangeListener { _, isChecked ->
            useGps = isChecked
            dataCollector?.setUseGps(useGps)
            Toast.makeText(
                this,
                if (useGps) "Режим: с GPS" else "Режим: без GPS",
                Toast.LENGTH_SHORT
            ).show()
            updateButtonAppearance()
        }

        btnCenterMap.setOnClickListener {
            centerMapOnLastLocation()
        }

        btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun setupMap() {
        try {
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            mapView.setMultiTouchControls(true)
            mapView.setUseDataConnection(true)

            // Устанавливаем начальный центр (Москва) и зум
            val startPoint = GeoPoint(55.7558, 37.6176)
            mapView.controller.setZoom(12.0)  // Более общий зум для старта
            mapView.controller.setCenter(startPoint)

            mapView.invalidate()
            Log.d(TAG, "Карта инициализирована с центром: Москва")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка карты: ${e.message}")
        }
    }

    private fun setupMapDoubleClickListener() {
        mapView.overlays.add(object : org.osmdroid.views.overlay.Overlay() {
            override fun onDoubleTap(event: MotionEvent?, mapView: MapView?): Boolean {
                event?.let {
                    val geoPoint = mapView?.projection?.fromPixels(it.x.toInt(), it.y.toInt())
                    geoPoint?.let { point ->
                        if (isAdmin) {
                            showAdminSnapshotDialog(point.latitude, point.longitude)
                        }
                    }
                }
                return true
            }
        })
    }

    private fun sendAdminSnapshot(lat: Double, lon: Double) {
        lifecycleScope.launch {
            try {
                val progressDialog = ProgressDialog(this@MainActivity).apply {
                    setMessage("Сбор данных о сетях...")
                    setCancelable(false)
                    show()
                }

                val snapshot = adminCollector.collectSnapshot(lat, lon)

                progressDialog.dismiss()

                val response = RetrofitClient.adminApi.saveSnapshot(snapshot)

                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity,
                        "✅ Снапшот отправлен (${String.format("%.6f", lat)}, ${String.format("%.6f", lon)})\n" +
                                "Wi-Fi: ${snapshot.wifiNetworks?.size ?: 0}, " +
                                "Bluetooth: ${snapshot.bluetoothDevices?.size ?: 0}",
                        Toast.LENGTH_LONG).show()
                    Log.d(TAG, "Admin snapshot sent: $lat, $lon")
                } else {
                    Toast.makeText(this@MainActivity,
                        "❌ Ошибка отправки: ${response.code()}",
                        Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error sending admin snapshot", e)
                Toast.makeText(this@MainActivity,
                    "Ошибка: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAdminSnapshotDialog(lat: Double, lon: Double) {
        val tempMarker = Marker(mapView).apply {
            position = GeoPoint(lat, lon)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Отправка снапшота..."
            mapView.overlays.add(this)
            mapView.invalidate()
        }

        AlertDialog.Builder(this)
            .setTitle("👑 Админский снапшот")
            .setMessage("Отправить снапшот с координатами:\n" +
                    "${String.format("%.6f", lat)}, ${String.format("%.6f", lon)}?\n\n" +
                    "Будут собраны данные:\n" +
                    "📶 Wi-Fi сети\n" +
                    "📱 Сотовая сеть\n" +
                    "🔵 Bluetooth устройства")
            .setPositiveButton("Отправить") { _, _ ->
                sendAdminSnapshot(lat, lon)
                mapView.overlays.remove(tempMarker)
                mapView.invalidate()
            }
            .setNegativeButton("Отмена") { _, _ ->
                mapView.overlays.remove(tempMarker)
                mapView.invalidate()
            }
            .show()
    }

    private fun setupRecyclerView() {
        adapter = SnapshotAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun updateMapLocation(lat: Double, lon: Double) {
        val geoPoint = GeoPoint(lat, lon)

        if (currentLocationMarker == null) {
            currentLocationMarker = Marker(mapView).apply {
                position = geoPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Вы здесь"
                mapView.overlays.add(this)
            }
            mapView.controller.setCenter(geoPoint)
        } else {
            currentLocationMarker?.position = geoPoint
        }

        lastKnownLocation = UserLocation(lat, lon, System.currentTimeMillis())
        mapView.invalidate()
    }

    private fun centerMapOnLastLocation() {
        if (lastKnownLocation != null) {
            val geoPoint = GeoPoint(lastKnownLocation!!.lat, lastKnownLocation!!.lon)
            mapView.controller.animateTo(geoPoint)
            Toast.makeText(this, "Центрировано", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Нет данных GPS. Ожидание сигнала...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendSnapshotsToServer(snapshots: List<NetworkSnapshot>) {
        val validSnapshots = snapshots.filter { it.location != null }

        if (validSnapshots.isEmpty()) {
            return
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.trackApi?.saveTrack(validSnapshots)
                if (response != null && response.isSuccessful) {
                    Log.d(TAG, "Отправлено ${validSnapshots.size} снапшотов")
                } else if (response != null && response.code() == 401) {
                    // Токен истек, но не выходим, пусть обновляется автоматически
                } else {
                    Log.e(TAG, "Ошибка отправки: ${response?.code()}")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Ошибка сети: ${e.message}")
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    // Токен истек, но не выходим, пусть обновляется автоматически
                }
                Log.e(TAG, "Ошибка HTTP: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка отправки: ${e.message}")
            }
        }
    }

    private fun requestLocationFromServer(snapshot: NetworkSnapshot) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.locationApi.getLocation(snapshot)
                if (response.isSuccessful) {
                    val location = response.body()
                    if (location != null) {
                        updateMapLocation(location.lat, location.lon)
                        Log.d(TAG, "Location from server: ${location.lat}, ${location.lon}")
                    }
                } else if (response.code() == 401) {
                    // Токен истек - выходим
                    logout()
                } else {
                    Log.e(TAG, "Ошибка получения локации: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка: ${e.message}")
            }
        }
    }

    private fun toggleDataCollection() {
        val requirements = checkAllRequirements()

        if (!requirements.isGpsEnabled && useGps) {
            showToastWithAction("Включите GPS") {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            return
        }

        if (!requirements.isWifiEnabled) {
            showToastWithAction("Включите Wi-Fi") {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            return
        }

        if (!requirements.isBluetoothEnabled) {
            showToastWithAction("Включите Bluetooth") {
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
            dataCollector?.setUseGps(useGps)
            val started = dataCollector?.startCollecting() ?: false
            if (started) {
                snapshotBatch.clear()
                adapter.clearSnapshots()
                hasReceivedFirstGps = false
                Toast.makeText(this, if (useGps) "Сбор начат. Ожидание GPS..." else "Сбор начат", Toast.LENGTH_SHORT).show()
            } else {
                isCollecting = false
                Toast.makeText(this, "Ошибка запуска", Toast.LENGTH_SHORT).show()
            }
        } else {
            dataCollector?.stopCollecting()
            Toast.makeText(this, "Сбор остановлен", Toast.LENGTH_SHORT).show()
        }

        updateButtonAppearance()
    }

    private fun loadUserInfo() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.authApi.getCurrentUser()
                if (response.isSuccessful) {
                    val user = response.body()
                    if (user != null) {
                        isAdmin = user.roles.contains("ROLE_ADMIN")
                        val roleText = if (isAdmin) "👑 Администратор (двойной тап по карте)" else "Пользователь"
                        tvStatus.text = buildString {
                            append("Пользователь: ${user.username}\n")
                            append("Роль: $roleText\n")
                            append("---\n")
                            append(tvStatus.text.toString())
                        }

                        if (isAdmin) {
                            Toast.makeText(this@MainActivity,
                                "👑 Админ режим: двойное нажатие на карту для отправки снапшота",
                                Toast.LENGTH_LONG).show()
                        }
                    }
                } else if (response.code() == 401) {
                    logout()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки пользователя: ${e.message}")
            }
        }
    }

    private fun logout() {
        tokenManager.clearTokens()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private data class RequirementsStatus(
        val isGpsEnabled: Boolean,
        val isWifiEnabled: Boolean,
        val isBluetoothEnabled: Boolean,
        val hasLocationPermission: Boolean,
        val hasBluetoothPermission: Boolean
    )

    @SuppressLint("SetTextI18n")
    private fun checkAllRequirements(): RequirementsStatus {
        val isGpsEnabled = dataCollector?.isGpsEnabled() ?: false
        val isWifiEnabled = (getSystemService(WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true
        val isBluetoothEnabled = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        val hasLocationPermission = dataCollector?.hasLocationPermission() ?: false

        val hasBluetoothPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            hasLocationPermission
        }

        tvStatus.text =
            "Wi‑Fi:${if (isWifiEnabled) "✅" else "❌"} | " +
                    "BT:${if (isBluetoothEnabled) "✅" else "❌"} | " +
                    "Режим:${if (useGps) "GPS" else "без GPS"}" +
                    (if (isCollecting) " | СБОР" else "") +
                    (if (isCollecting && useGps && !hasReceivedFirstGps) " | ⏳GPS" else "")

        return RequirementsStatus(
            isGpsEnabled, isWifiEnabled, isBluetoothEnabled,
            hasLocationPermission, hasBluetoothPermission
        )
    }

    private fun showToastWithAction(message: String, action: () -> Unit) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        btnToggleGps.postDelayed({ action() }, 1500)
    }

    private fun updateButtonAppearance() {
        val requirements = checkAllRequirements()
        val allRequirementsMet = if (useGps) {
            requirements.isGpsEnabled && requirements.isWifiEnabled &&
                    requirements.isBluetoothEnabled && requirements.hasLocationPermission &&
                    requirements.hasBluetoothPermission
        } else {
            requirements.isWifiEnabled && requirements.isBluetoothEnabled &&
                    requirements.hasLocationPermission && requirements.hasBluetoothPermission
        }

        if (isCollecting) {
            btnToggleGps.text = "Остановить"
            btnToggleGps.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
        } else {
            btnToggleGps.text = if (allRequirementsMet) {
                if (useGps) "Начать (с GPS)" else "Начать (без GPS)"
            } else {
                "Проверить"
            }
            btnToggleGps.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        }
    }

    private fun updateStatusText(snapshot: NetworkSnapshot) {
        tvStatus.text = buildString {
            // Краткая информация о пользователе и статусе
            if (isAdmin) {
                append("👑 ")
            }
            append("${snapshot.snapshotTime} | ")

            snapshot.location?.let {
                append("📍 ${String.format("%.4f", it.latitude)}, ${String.format("%.4f", it.longitude)}")
                append(" (${String.format("%.0f", it.accuracy)}м)")
            } ?: append("📍 ожидание...")

            append(" | 📶${snapshot.wifiNetworks?.size ?: 0}")
            append(" | 📱${if (snapshot.cellularNetwork != null) "✓" else "✗"}")
            append(" | 🔵${snapshot.bluetoothDevices?.size ?: 0}")

            if (isCollecting) append(" | ⚡")
            if (isCollecting && useGps && !hasReceivedFirstGps) append(" | ⏳GPS")
        }
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
                    if (!isCollecting) toggleDataCollection()
                } else {
                    Toast.makeText(this, "Нужны разрешения", Toast.LENGTH_LONG).show()
                }
            }
            BLUETOOTH_PERMISSION_REQUEST_CODE -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    checkAllRequirements()
                    if (!isCollecting) toggleDataCollection()
                } else {
                    Toast.makeText(this, "Нужны разрешения Bluetooth", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        checkAllRequirements()
        updateButtonAppearance()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        dataCollector?.dispose()
    }
}