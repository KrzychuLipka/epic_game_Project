package pl.pw.epicgameproject

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Region
import pl.pw.epicgameproject.data.model.ArchiveBeacon
import pl.pw.epicgameproject.data.model.BeaconFile
import android.content.res.AssetManager
import com.google.gson.Gson
import org.osmdroid.views.MapView
import org.osmdroid.config.Configuration
import android.preference.PreferenceManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.overlay.Marker
import org.osmdroid.util.GeoPoint



class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "pw.MainActivity"
    }

    private lateinit var connectionStateReceiver: ConnectionStateChangeReceiver
    private var beaconManager: BeaconManager? = null
    private val region = Region("all-beacons-region", null, null, null)
    private val archiveBeacons= mutableListOf<ArchiveBeacon>()
    private val mapView:MapView by lazy {
        findViewById(R.id.map_view)
    }
    private var deviceMarker: Marker? = null


    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.entries.any { !it.value }) {
                Toast.makeText(
                    this,
                    "Bez przydzielenia niezbędnych uprawnień aplikacja nie będzie działać prawidłowo.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                listenForConnectionChanges()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        Configuration.getInstance().load(
            this,
            PreferenceManager.getDefaultSharedPreferences(this)
        )
        val mapView: MapView = findViewById(R.id.map_view)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        val mapController = mapView.controller
        mapController.setZoom(14.0) // np. zoom 14
        val startPoint = org.osmdroid.util.GeoPoint(52.2297, 21.0122) // Warszawa
        mapController.setCenter(startPoint)
        setUpBeaconManager()
        setUpUI()

        requestRequiredPermissions()
        loadBeaconsFromAssets()
        Log.d(TAG, "Rchivalne becony :${archiveBeacons.count()}")

    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(connectionStateReceiver)
        cleanupBeaconManager()

    }

    private fun cleanupBeaconManager() {
        beaconManager?.stopRangingBeacons(region)
    }

    private fun setUpUI() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        if (allPermissionsGranted(permissions)) {
            listenForConnectionChanges()
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun allPermissionsGranted(permissions: Array<String>): Boolean {
        permissions.forEach { permissionName ->
            if (ContextCompat.checkSelfPermission(
                    this,
                    permissionName
                ) == PackageManager.PERMISSION_DENIED
            ) {
                return false
            }
        }
        return true
    }

    private fun listenForConnectionChanges() {
        Toast.makeText(
            this,
            "Upewnij się, że masz włączony GPS oraz Bluetooth.",
            Toast.LENGTH_SHORT
        ).show()
        connectionStateReceiver = ConnectionStateChangeReceiver(
            onBothEnabled = { startScanningIfPossible() },
            onEitherDisabled = { cleanupBeaconManager() }
        )

        val intentFilter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        registerReceiver(connectionStateReceiver, intentFilter)

        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothEnabled = bluetoothManager.adapter.isEnabled
        if (gpsEnabled && bluetoothEnabled) {
            startScanningIfPossible()
        }
    }

    private fun startScanningIfPossible() {
        Toast.makeText(this, "Skanowanie rozpoczęte :)", Toast.LENGTH_SHORT).show()
        scanForBeacons()

    }

    private fun setUpBeaconManager() {
        if (beaconManager == null) {
            beaconManager = BeaconManager.getInstanceForApplication(this)
            listOf(
                BeaconParser.EDDYSTONE_UID_LAYOUT,
                BeaconParser.EDDYSTONE_TLM_LAYOUT,
                BeaconParser.EDDYSTONE_URL_LAYOUT,
            ).forEach {
                beaconManager?.beaconParsers?.add(BeaconParser().setBeaconLayout(it))
            }
            beaconManager?.addRangeNotifier { beacons, _ ->
                Log.d(TAG, "num of becones:${beacons.count()}")
                calculateDevicePosition(beacons)
            }

        }
    }

    private fun scanForBeacons() {
        //beaconManager.getRegionViewModel(region).rangedBeacons.observe(this, rangingObserver)
        beaconManager?.startRangingBeacons(region)
    }

        private fun calculateDevicePosition(beacons: MutableCollection<Beacon>) {
        if (beacons.isEmpty()) {
            Log.d(TAG, "Brak wykrytych beaconów")
            return
        }

        var weightedLatitudeSum = 0.0
        var weightedLongitudeSum = 0.0
        var weightSum = 0.0

        beacons.forEach { beacon ->
            val realBeacon = archiveBeacons.find { it.beaconUid == beacon.bluetoothAddress }
            if (realBeacon != null && beacon.distance > 0) {
                val weight = 1.0 / beacon.distance
                weightedLatitudeSum += realBeacon.latitude * weight
                weightedLongitudeSum += realBeacon.longitude * weight
                weightSum += weight
            }
        }

        if (weightSum > 0) {
            val estimatedLatitude = weightedLatitudeSum / weightSum
            val estimatedLongitude = weightedLongitudeSum / weightSum
            Log.d(TAG, "Szacowana pozycja: Latitude: $estimatedLatitude, Longitude: $estimatedLongitude")
            updateMarker(estimatedLatitude, estimatedLongitude)

        } else {
            Log.d(TAG, "Nie można oszacować pozycji - brak poprawnych danych.")
        }
    }

    private fun loadBeaconsFromAssets() {
        val assetManager = assets
        val beaconFiles: List<String> = assetManager.list("")?.filter {
            it.startsWith("beacons_") && it.endsWith(".txt")
        } ?: emptyList()

        val gson = Gson()
        val allBeacons = mutableListOf<ArchiveBeacon>()

        for (fileName in beaconFiles) {
            assetManager.open(fileName).use { inputStream ->
                val jsonText = inputStream.bufferedReader().use { it.readText() }
                val beaconFile = gson.fromJson(jsonText, BeaconFile::class.java)
                beaconFile.items?.forEach { item ->
                    if (item.beaconUid != null) {
                        allBeacons.add(
                            ArchiveBeacon(
                                beaconUid = item.beaconUid,
                                latitude = item.latitude,
                                longitude = item.longitude
                            )
                        )
                    }
                }

            }
        }
        archiveBeacons.clear()
        archiveBeacons.addAll(allBeacons)
    }

    private fun updateMarker(latitude: Double, longitude: Double) {
        if (deviceMarker == null) {
            Log.d(TAG, "Jestem w markerze : $deviceMarker")
            deviceMarker = Marker(mapView).apply {
                position = GeoPoint(latitude, longitude)
                title = "Pozycja urządzenia"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(deviceMarker)

        } else {
            deviceMarker!!.position = GeoPoint(latitude, longitude)
        }
        mapView.invalidate()
    }



}
