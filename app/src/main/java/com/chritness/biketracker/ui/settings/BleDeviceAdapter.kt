package com.FreeWheel.biketracker.ui.settings

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.FreeWheel.biketracker.databinding.ItemBleDeviceBinding

class BleDeviceAdapter(
    private val onDeviceClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<BleDeviceAdapter.DeviceViewHolder>() {
    
    private val devices = mutableListOf<ScanResult>()
    
    class DeviceViewHolder(
        private val binding: ItemBleDeviceBinding,
        private val onDeviceClick: (BluetoothDevice) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        @SuppressLint("MissingPermission")
        fun bind(scanResult: ScanResult) {
            val device = scanResult.device
            val deviceName = device.name ?: "Unknown Device"
            val deviceAddress = device.address ?: "Unknown Address"
            val rssi = scanResult.rssi
            
            binding.deviceNameText.text = deviceName
            binding.deviceAddressText.text = deviceAddress
            binding.deviceRssiText.text = "$rssi dBm"
            
            binding.root.setOnClickListener {
                onDeviceClick(device)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemBleDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding, onDeviceClick)
    }
    
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }
    
    override fun getItemCount(): Int = devices.size
    
    @SuppressLint("NotifyDataSetChanged")
    fun updateDevices(newDevices: List<ScanResult>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }
    
    fun addDevice(scanResult: ScanResult) {
        // Check if device is already in the list
        val existingIndex = devices.indexOfFirst { it.device.address == scanResult.device.address }
        if (existingIndex != -1) {
            // Update existing device with new RSSI
            devices[existingIndex] = scanResult
            notifyItemChanged(existingIndex)
        } else {
            // Add new device
            devices.add(scanResult)
            notifyItemInserted(devices.size - 1)
        }
    }
    
    fun clearDevices() {
        devices.clear()
        notifyDataSetChanged()
    }
}