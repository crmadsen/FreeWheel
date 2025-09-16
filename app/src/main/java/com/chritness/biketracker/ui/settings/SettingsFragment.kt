package com.FreeWheel.biketracker.ui.settings

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.FreeWheel.biketracker.R
import com.FreeWheel.biketracker.databinding.FragmentSettingsBinding
import com.FreeWheel.biketracker.databinding.DialogDeviceSelectionBinding
import com.FreeWheel.biketracker.service.BleScannerService
import com.FreeWheel.biketracker.ui.tracking.TrackingViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private val trackingViewModel: TrackingViewModel by activityViewModels()
    private lateinit var sharedPreferences: SharedPreferences
    
    // BLE scanning
    private var bleScanner: BleScannerService? = null
    private var scanDialog: AlertDialog? = null
    private var deviceAdapter: BleDeviceAdapter? = null

    companion object {
        private const val PREFS_NAME = "bike_tracker_settings"
        private const val PREF_UNIT_SYSTEM = "unit_system"
        private const val PREF_DARK_MODE = "dark_mode"
        private const val UNIT_METRIC = "metric"
        private const val UNIT_IMPERIAL = "imperial"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Initialize BLE scanner
        bleScanner = BleScannerService(requireContext())
        
        setupUnitSelection()
        setupDarkModeToggle()
        setupHeartRateConnection()
    }

    private fun setupUnitSelection() {
        val currentUnit = sharedPreferences.getString(PREF_UNIT_SYSTEM, UNIT_METRIC)
        
        // Set initial state
        when (currentUnit) {
            UNIT_METRIC -> binding.unitRadioGroup.check(R.id.radioMetric)
            UNIT_IMPERIAL -> binding.unitRadioGroup.check(R.id.radioImperial)
        }
        
        // Handle unit changes
        binding.unitRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newUnit = when (checkedId) {
                R.id.radioMetric -> UNIT_METRIC
                R.id.radioImperial -> UNIT_IMPERIAL
                else -> UNIT_METRIC
            }
            
            sharedPreferences.edit()
                .putString(PREF_UNIT_SYSTEM, newUnit)
                .apply()
                
            Toast.makeText(
                requireContext(),
                "Units changed to ${if (newUnit == UNIT_METRIC) "Metric" else "Imperial"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupDarkModeToggle() {
        // Get current dark mode state
        val isDarkMode = sharedPreferences.getBoolean(PREF_DARK_MODE, false)
        binding.darkModeSwitch.isChecked = isDarkMode
        
        // Set up switch listener
        binding.darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Save preference
            sharedPreferences.edit()
                .putBoolean(PREF_DARK_MODE, isChecked)
                .apply()
            
            // Apply theme change
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            
            Toast.makeText(
                requireContext(),
                "Theme changed to ${if (isChecked) "Dark" else "Light"} mode",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupHeartRateConnection() {
        binding.connectHeartRateButton.setOnClickListener {
            if (trackingViewModel.isHeartRateConnected()) {
                // Disconnect if already connected
                trackingViewModel.disconnectHeartRateMonitor()
                Toast.makeText(requireContext(), "Disconnected from heart rate monitor", Toast.LENGTH_SHORT).show()
            } else {
                // Show scanning dialog
                showDeviceScanDialog()
            }
        }
        
        // Update connection status
        trackingViewModel.heartRate.observe(viewLifecycleOwner) { heartRate ->
            val isConnected = heartRate > 0 || trackingViewModel.isHeartRateConnected()
            updateHeartRateStatus(isConnected, heartRate)
        }
    }
    
    private fun updateHeartRateStatus(isConnected: Boolean, heartRate: Int) {
        binding.heartRateStatusText.text = when {
            isConnected && heartRate > 0 -> "Connected - $heartRate bpm"
            isConnected -> "Connected"
            else -> "Not connected"
        }
        
        binding.heartRateStatusIcon.setImageResource(
            if (isConnected) R.drawable.ic_favorite_24 else R.drawable.ic_favorite_border_24
        )
        
        binding.connectHeartRateButton.text = if (isConnected) {
            "Disconnect"
        } else {
            "Connect Heart Rate Monitor"
        }
    }
    
    private fun showDeviceScanDialog() {
        val dialogBinding = DialogDeviceSelectionBinding.inflate(layoutInflater)
        
        // Setup RecyclerView
        deviceAdapter = BleDeviceAdapter { device ->
            connectToDevice(device)
        }
        
        dialogBinding.deviceRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = deviceAdapter
        }
        
        scanDialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()
        
        // Setup button listeners
        dialogBinding.rescanButton.setOnClickListener {
            startScanning(dialogBinding)
        }
        
        dialogBinding.cancelButton.setOnClickListener {
            scanDialog?.dismiss()
        }
        
        scanDialog?.setOnDismissListener {
            bleScanner?.stopScanning()
        }
        
        scanDialog?.show()
        
        // Start scanning
        startScanning(dialogBinding)
    }
    
    private fun startScanning(dialogBinding: DialogDeviceSelectionBinding) {
        if (bleScanner?.isBluetoothEnabled() != true) {
            Toast.makeText(requireContext(), "Please enable Bluetooth", Toast.LENGTH_LONG).show()
            scanDialog?.dismiss()
            return
        }
        
        deviceAdapter?.clearDevices()
        dialogBinding.scanStatusText.text = "Scanning for devices..."
        dialogBinding.scanProgressBar.visibility = View.VISIBLE
        
        // Collect scan results
        lifecycleScope.launch {
            bleScanner?.scanResults?.collectLatest { scanResult ->
                deviceAdapter?.addDevice(scanResult)
            }
        }
        
        // Collect scanning state
        lifecycleScope.launch {
            bleScanner?.isScanning?.collectLatest { isScanning ->
                if (!isScanning) {
                    dialogBinding.scanProgressBar.visibility = View.GONE
                    val deviceCount = deviceAdapter?.itemCount ?: 0
                    dialogBinding.scanStatusText.text = if (deviceCount > 0) {
                        "Found $deviceCount device(s)"
                    } else {
                        "No devices found. Make sure your Polar H10 is nearby and active."
                    }
                }
            }
        }
        
        if (!bleScanner?.startScanning()!!) {
            Toast.makeText(requireContext(), "Failed to start scanning. Check permissions.", Toast.LENGTH_LONG).show()
            scanDialog?.dismiss()
        }
    }
    
    private fun connectToDevice(device: BluetoothDevice) {
        scanDialog?.dismiss()
        
        val success = trackingViewModel.connectToHeartRateDevice(device)
        if (success) {
            Toast.makeText(requireContext(), "Connecting to ${device.name ?: "device"}...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Failed to connect to device", Toast.LENGTH_SHORT).show()
        }
    }

    fun getUnitSystem(): String {
        return sharedPreferences.getString(PREF_UNIT_SYSTEM, UNIT_METRIC) ?: UNIT_METRIC
    }

    fun isMetricSystem(): Boolean {
        return getUnitSystem() == UNIT_METRIC
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bleScanner?.stopScanning()
        scanDialog?.dismiss()
        _binding = null
    }
}