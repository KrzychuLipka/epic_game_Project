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
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.Region

class MainActivity : AppCompatActivity() {

    private lateinit var connectionStateReceiver: ConnectionStateChangeReceiver
    private lateinit var beaconManager: BeaconManager
    private val region = Region("all-beacons-region", null, null, null)//?????????

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
        setUpUI()
        beaconManager = BeaconManager.getInstanceForApplication(this)
        requestRequiredPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(connectionStateReceiver)
        cleanupBeaconManager()
    }
    private fun cleanupBeaconManager() {
        beaconManager.stopRangingBeacons(region)
        beaconManager.getRegionViewModel(region).rangedBeacons.removeObservers(this)
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
            if (ContextCompat.checkSelfPermission(this, permissionName) == PackageManager.PERMISSION_DENIED) {
                return false
            }
        }
        return true
    }

    private fun listenForConnectionChanges() {
        Toast.makeText(this, "Upewnij się, że masz włączony GPS oraz Bluetooth.", Toast.LENGTH_SHORT).show()
        connectionStateReceiver = ConnectionStateChangeReceiver(
            onBothEnabled = { startScanningIfPossible() },
            onEitherDisabled = { cleanupBeaconManager() }
        )

        val intentFilter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        registerReceiver(connectionStateReceiver, intentFilter)

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothEnabled = bluetoothManager.adapter.isEnabled
        if (gpsEnabled && bluetoothEnabled) {
            startScanningIfPossible()
        }
    }

    private fun startScanningIfPossible() {
        Toast.makeText(this, "Skanowanie rozpoczęte :)", Toast.LENGTH_SHORT).show()
        scanForBeacons()
    }

    private fun scanForBeacons() {
        val rangingObserver = Observer<Collection<Beacon>> { beacons ->
            if (beacons.isEmpty()) {
                Toast.makeText(this, "Nie znaleziono żadnych beaconów", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Wykryto: ${beacons.count()} beaconów", Toast.LENGTH_SHORT).show()
                val threeClosestBeacons = beacons.sortedBy { it.distance }.take(3)
                calculateDevicePosition(threeClosestBeacons)
            }
        }
        beaconManager.getRegionViewModel(region).rangedBeacons.observe(this, rangingObserver)
        beaconManager.startRangingBeacons(region)
    }

    private fun calculateDevicePosition(beacons: List<Beacon>) {
        // TODO: Implementacja trilateracji
    }
}
