package com.FreeWheel.biketracker.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.*

class BleScannerService(private val context: Context) {
    
    companion object {
        private const val TAG = "BleScannerService"
        private const val SCAN_PERIOD: Long = 10000 // 10 seconds
        
        // Standard Heart Rate Service UUID
        private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        
        // Known Polar device names
        private val POLAR_DEVICE_NAMES = listOf("Polar H10", "H10", "POLAR H10")
    }
    
    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    
    private val _scanResults = MutableSharedFlow<ScanResult>()
    val scanResults = _scanResults.asSharedFlow()
    
    private val _isScanning = MutableSharedFlow<Boolean>()
    val isScanning = _isScanning.asSharedFlow()
    
    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false
    
    // Store discovered devices to avoid duplicates
    private val discoveredDevices = mutableMapOf<String, BluetoothDevice>()
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            
            val device = result.device
            val deviceName = device.name
            val deviceAddress = device.address
            
            // Log all discovered devices for debugging
            Log.d(TAG, "Discovered device: $deviceName ($deviceAddress) RSSI: ${result.rssi}")
            
            // Filter for heart rate devices
            if (isHeartRateDevice(result)) {
                // Avoid duplicate emissions for same device
                if (!discoveredDevices.containsKey(deviceAddress)) {
                    discoveredDevices[deviceAddress] = device
                    Log.d(TAG, "Heart rate device found: $deviceName ($deviceAddress)")
                    
                    // Emit the scan result
                    try {
                        kotlinx.coroutines.runBlocking {
                            _scanResults.emit(result)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error emitting scan result", e)
                    }
                }
            }
        }
        
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            super.onBatchScanResults(results)
            results.forEach { result ->
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
            stopScanning()
        }
    }
    
    private fun isHeartRateDevice(result: ScanResult): Boolean {
        val device = result.device
        val scanRecord = result.scanRecord
        
        // Check device name for Polar patterns
        val deviceName = device.name
        if (!deviceName.isNullOrBlank()) {
            val upperName = deviceName.uppercase()
            if (POLAR_DEVICE_NAMES.any { polar -> upperName.contains(polar.uppercase()) }) {
                Log.d(TAG, "Device matches Polar name pattern: $deviceName")
                return true
            }
        }
        
        // Check if device advertises Heart Rate Service
        scanRecord?.serviceUuids?.let { serviceUuids ->
            if (serviceUuids.contains(ParcelUuid(HEART_RATE_SERVICE_UUID))) {
                Log.d(TAG, "Device advertises Heart Rate Service: $deviceName")
                return true
            }
        }
        
        // Check manufacturer data for Polar (manufacturer ID 107 for Polar)
        scanRecord?.manufacturerSpecificData?.let { manufacturerData ->
            if (manufacturerData.get(107) != null) { // Polar's manufacturer ID
                Log.d(TAG, "Device has Polar manufacturer data: $deviceName")
                return true
            }
        }
        
        return false
    }
    
    @SuppressLint("MissingPermission")
    fun startScanning(): Boolean {
        if (!isBluetoothEnabled()) {
            Log.w(TAG, "Bluetooth is not enabled")
            return false
        }
        
        if (scanning) {
            Log.d(TAG, "Already scanning")
            return true
        }
        
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null")
            return false
        }
        
        // Clear previous results
        discoveredDevices.clear()
        
        // Create scan settings for optimized heart rate device discovery
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0)
            .build()
        
        // Create scan filters for heart rate devices
        val filters = listOf(
            // Filter for Heart Rate Service UUID
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
                .build(),
            // Filter for Polar devices by name (if available)
            ScanFilter.Builder()
                .setDeviceName("Polar H10")
                .build()
        )
        
        try {
            bluetoothLeScanner?.startScan(filters, scanSettings, scanCallback)
            scanning = true
            
            Log.d(TAG, "Started BLE scan for heart rate devices")
            
            // Emit scanning state
            try {
                kotlinx.coroutines.runBlocking {
                    _isScanning.emit(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error emitting scanning state", e)
            }
            
            // Stop scanning after specified period
            handler.postDelayed({
                stopScanning()
            }, SCAN_PERIOD)
            
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for BLE scanning", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan", e)
            return false
        }
    }
    
    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!scanning) return
        
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            scanning = false
            
            Log.d(TAG, "Stopped BLE scan")
            
            // Emit scanning state
            try {
                kotlinx.coroutines.runBlocking {
                    _isScanning.emit(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error emitting scanning state", e)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for stopping BLE scan", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE scan", e)
        }
    }
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    fun isScanning(): Boolean = scanning
    
    fun getDiscoveredDevices(): List<BluetoothDevice> {
        return discoveredDevices.values.toList()
    }
}