package com.FreeWheel.biketracker.ui.history

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.FreeWheel.biketracker.databinding.FragmentHistoryBinding
import com.FreeWheel.biketracker.data.model.Ride

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var rideAdapter: RideHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupRecyclerView()
        setupViewModel()
        
        return root
    }
    
    private fun setupRecyclerView() {
        rideAdapter = RideHistoryAdapter(
            onRideClick = { ride -> showRideDetails(ride) },
            onDeleteRide = { ride -> confirmDeleteRide(ride) },
            onExportRide = { ride -> exportRide(ride) },
            onShareRide = { ride -> shareRide(ride) }
        )
        
        binding.ridesRecyclerView.apply {
            adapter = rideAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }
    
    private fun setupViewModel() {
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        
        historyViewModel.allRides.observe(viewLifecycleOwner) { rides ->
            if (rides.isEmpty()) {
                binding.ridesRecyclerView.visibility = View.GONE
                binding.emptyStateText.visibility = View.VISIBLE
            } else {
                binding.ridesRecyclerView.visibility = View.VISIBLE
                binding.emptyStateText.visibility = View.GONE
                
                // Sort by date descending (newest first)
                val sortedRides = rides.sortedByDescending { it.startTime }
                rideAdapter.submitList(sortedRides)
            }
        }
    }
    
    private fun showRideDetails(ride: Ride) {
        // Create detailed view dialog
        val message = buildString {
            appendLine("Date: ${ride.startTime}")
            appendLine("Distance: ${String.format("%.2f km", ride.distance)}")
            
            val duration = ride.duration
            val hours = duration / 3600000
            val minutes = (duration % 3600000) / 60000
            val durationText = when {
                hours > 0 -> String.format("%dh %02dm", hours, minutes)
                else -> String.format("%dm", minutes)
            }
            appendLine("Duration: $durationText")
            
            appendLine("Average Speed: ${String.format("%.1f km/h", ride.averageSpeed)}")
            appendLine("Max Speed: ${String.format("%.1f km/h", ride.maxSpeed)}")
            
            if (ride.averageHeartRate > 0) {
                appendLine("Average Heart Rate: ${ride.averageHeartRate} bpm")
                appendLine("Max Heart Rate: ${ride.maxHeartRate} bpm")
            }
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("Ride Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun confirmDeleteRide(ride: Ride) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Ride")
            .setMessage("Are you sure you want to delete this ride? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                historyViewModel.deleteRide(ride)
                Toast.makeText(requireContext(), "Ride deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun exportRide(@Suppress("UNUSED_PARAMETER") ride: Ride) {
        // TODO: Implement GPX export functionality
        Toast.makeText(requireContext(), "Export functionality coming soon", Toast.LENGTH_SHORT).show()
    }
    
    private fun shareRide(ride: Ride) {
        val shareText = buildString {
            appendLine("🚴 Bike Ride Summary")
            appendLine("📅 ${ride.startTime}")
            appendLine("📍 Distance: ${String.format("%.2f km", ride.distance)}")
            
            val duration = ride.duration
            val hours = duration / 3600000
            val minutes = (duration % 3600000) / 60000
            val durationText = when {
                hours > 0 -> String.format("%dh %02dm", hours, minutes)
                else -> String.format("%dm", minutes)
            }
            appendLine("⏱️ Duration: $durationText")
            appendLine("🏃 Avg Speed: ${String.format("%.1f km/h", ride.averageSpeed)}")
            appendLine("🚀 Max Speed: ${String.format("%.1f km/h", ride.maxSpeed)}")
            
            if (ride.averageHeartRate > 0) {
                appendLine("❤️ Avg HR: ${ride.averageHeartRate} bpm")
                appendLine("💓 Max HR: ${ride.maxHeartRate} bpm")
            }
            
            appendLine("\nTracked with FreeWheel Bike Tracker")
        }
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        
        startActivity(Intent.createChooser(shareIntent, "Share Ride"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}