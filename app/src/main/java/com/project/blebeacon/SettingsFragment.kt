package com.project.blebeacon

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {
    private lateinit var ivBluetooth: ImageView
    private lateinit var ivLocation: ImageView
    private lateinit var ivBackgroundLocation: ImageView
    private lateinit var btnRequestPermissions: Button

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        updatePermissionStatus()
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            updatePermissionStatus()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ivBluetooth = view.findViewById(R.id.ivBluetooth)
        ivLocation = view.findViewById(R.id.ivLocation)
        ivBackgroundLocation = view.findViewById(R.id.ivBackgroundLocation)
        btnRequestPermissions = view.findViewById(R.id.btnRequestPermissions)

        btnRequestPermissions.setOnClickListener {
            requestRequiredPermissions()
        }

        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        context?.let { ctx ->
            // Check Bluetooth
            val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val isBluetoothEnabled = bluetoothManager.adapter?.isEnabled == true
            ivBluetooth.setImageResource(
                if (isBluetoothEnabled) R.drawable.ic_check_circle
                else R.drawable.ic_error_circle
            )

            // Check Location
            val hasLocationPermission = hasLocationPermission()
            ivLocation.setImageResource(
                if (hasLocationPermission) R.drawable.ic_check_circle
                else R.drawable.ic_error_circle
            )

            // Check Background Location
            val hasBackgroundLocation = hasBackgroundLocationPermission()
            ivBackgroundLocation.setImageResource(
                if (hasBackgroundLocation) R.drawable.ic_check_circle
                else R.drawable.ic_error_circle
            )

            // Update button visibility
            btnRequestPermissions.visibility = if (hasAllPermissions()) View.GONE else View.VISIBLE
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun hasAllPermissions(): Boolean {
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val isBluetoothEnabled = bluetoothManager.adapter?.isEnabled == true
        return isBluetoothEnabled && hasLocationPermission() && hasBackgroundLocationPermission()
    }

    private fun requestRequiredPermissions() {
        context?.let { ctx ->
            // Request Bluetooth enable if needed
            val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            if (bluetoothManager.adapter?.isEnabled == false) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(enableBtIntent)
            }

            // Location permission
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )

            // Background location permission (Android 10 and above)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }

            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }
}