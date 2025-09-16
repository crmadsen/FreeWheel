package com.FreeWheel.biketracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.FreeWheel.biketracker.R
import com.FreeWheel.biketracker.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.*

class HeartRateService : Service() {
    
    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "heart_rate_channel"
        
        // Standard Heart Rate Service UUID
        private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        private val HEART_RATE_MEASUREMENT_UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }
    
    private val binder = HeartRateServiceBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedDevice: BluetoothDevice? = null
    
    private val _heartRateUpdates = MutableSharedFlow<Int>()
    val heartRateUpdates = _heartRateUpdates.asSharedFlow()
    
    private val _connectionState = MutableSharedFlow<ConnectionState>()
    val connectionState = _connectionState.asSharedFlow()
    
    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING
    }
    
    inner class HeartRateServiceBinder : Binder() {
        fun getService(): HeartRateService = this@HeartRateService
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    serviceScope.launch {
                        _connectionState.emit(ConnectionState.CONNECTED)
                    }
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    serviceScope.launch {
                        _connectionState.emit(ConnectionState.DISCONNECTED)
                    }
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    serviceScope.launch {
                        _connectionState.emit(ConnectionState.CONNECTING)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    serviceScope.launch {
                        _connectionState.emit(ConnectionState.DISCONNECTING)
                    }
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                val heartRateService = gatt.getService(HEART_RATE_SERVICE_UUID)
                val heartRateCharacteristic = heartRateService?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
                
                heartRateCharacteristic?.let { characteristic ->
                    gatt.setCharacteristicNotification(characteristic, true)
                    
                    val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    descriptor?.let { desc ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(desc)
                        }
                    }
                }
            }
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            
            if (characteristic.uuid == HEART_RATE_MEASUREMENT_UUID) {
                val heartRate = parseHeartRate(value)
                serviceScope.launch {
                    _heartRateUpdates.emit(heartRate)
                }
            }
        }
        
        // Fallback for older Android versions
        @Deprecated("Deprecated in Android API level 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicChanged(gatt, characteristic)
            
            characteristic?.let { char ->
                if (char.uuid == HEART_RATE_MEASUREMENT_UUID) {
                    @Suppress("DEPRECATION")
                    val heartRate = parseHeartRate(char.value ?: byteArrayOf())
                    serviceScope.launch {
                        _heartRateUpdates.emit(heartRate)
                    }
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }
    
    fun connectToDevice(device: BluetoothDevice): Boolean {
        try {
            connectedDevice = device
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
            return true
        } catch (exception: Exception) {
            return false
        }
    }
    
    fun disconnect() {
        bluetoothGatt?.disconnect()
    }
    
    fun isConnected(): Boolean {
        return bluetoothGatt != null && connectedDevice != null
    }
    
    private fun parseHeartRate(data: ByteArray): Int {
        return if (data.isNotEmpty()) {
            // Heart Rate format flag (bit 0)
            if ((data[0].toInt() and 0x01) == 0) {
                // 8-bit heart rate value
                data[1].toInt() and 0xFF
            } else {
                // 16-bit heart rate value
                ((data[2].toInt() and 0xFF) shl 8) + (data[1].toInt() and 0xFF)
            }
        } else {
            0
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Heart Rate Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background heart rate monitoring"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Heart Rate Monitor Active")
            .setContentText("Monitoring heart rate...")
            .setSmallIcon(R.drawable.ic_favorite)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }
}