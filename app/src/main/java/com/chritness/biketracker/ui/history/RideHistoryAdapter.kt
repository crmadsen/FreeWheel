package com.FreeWheel.biketracker.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.FreeWheel.biketracker.R
import com.FreeWheel.biketracker.data.model.Ride
import com.FreeWheel.biketracker.databinding.ItemRideBinding
import java.text.SimpleDateFormat
import java.util.*

class RideHistoryAdapter(
    private val onRideClick: (Ride) -> Unit,
    private val onDeleteRide: (Ride) -> Unit,
    private val onExportRide: (Ride) -> Unit,
    private val onShareRide: (Ride) -> Unit
) : ListAdapter<Ride, RideHistoryAdapter.RideViewHolder>(RideDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RideViewHolder {
        val binding = ItemRideBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RideViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RideViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RideViewHolder(private val binding: ItemRideBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(ride: Ride) {
            binding.apply {
                // Format date
                val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.getDefault())
                rideDate.text = dateFormat.format(ride.startTime)
                
                // Format duration
                val duration = ride.duration
                val hours = duration / 3600000
                val minutes = (duration % 3600000) / 60000
                rideDuration.text = when {
                    hours > 0 -> String.format("Duration: %dh %02dm", hours, minutes)
                    else -> String.format("Duration: %dm", minutes)
                }
                
                // Distance
                rideDistance.text = String.format("%.2f km", ride.distance)
                
                // Average speed
                rideAvgSpeed.text = String.format("Avg: %.1f km/h", ride.averageSpeed)
                
                // Max speed
                rideMaxSpeed.text = String.format("Max: %.1f km/h", ride.maxSpeed)
                
                // Click listeners
                root.setOnClickListener { onRideClick(ride) }
                
                // Long click for context menu
                root.setOnLongClickListener { view ->
                    showContextMenu(view, ride)
                    true
                }
            }
        }
        
        private fun showContextMenu(view: View, ride: Ride) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.ride_context_menu, popup.menu)
            
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_delete -> {
                        onDeleteRide(ride)
                        true
                    }
                    R.id.action_export -> {
                        onExportRide(ride)
                        true
                    }
                    R.id.action_share -> {
                        onShareRide(ride)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }
}

class RideDiffCallback : DiffUtil.ItemCallback<Ride>() {
    override fun areItemsTheSame(oldItem: Ride, newItem: Ride): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Ride, newItem: Ride): Boolean {
        return oldItem == newItem
    }
}