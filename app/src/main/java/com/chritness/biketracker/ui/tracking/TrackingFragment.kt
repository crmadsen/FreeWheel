package com.FreeWheel.biketracker.ui.tracking

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.FreeWheel.biketracker.R
import com.FreeWheel.biketracker.databinding.FragmentTrackingBinding
import com.FreeWheel.biketracker.utils.UnitUtils
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker

class TrackingFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var _binding: FragmentTrackingBinding? = null
    private val binding get() = _binding!!
    
    private val trackingViewModel: TrackingViewModel by activityViewModels()
    private lateinit var sharedPreferences: SharedPreferences
    
    private var routePolyline: Polyline? = null
    private var currentLocationMarker: Marker? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        android.util.Log.d("TrackingFragment", "onCreateView called")

        _binding = FragmentTrackingBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Initialize SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences(UnitUtils.PREFS_NAME, Context.MODE_PRIVATE)

        android.util.Log.d("TrackingFragment", "Setting up observers and button listeners")

        // Observe LiveData and update UI
        trackingViewModel.currentSpeed.observe(viewLifecycleOwner) { speed ->
            binding.currentSpeedText.text = UnitUtils.formatSpeed(speed, requireContext())
        }

        trackingViewModel.averageSpeed.observe(viewLifecycleOwner) { avgSpeed ->
            val speedUnit = UnitUtils.getSpeedUnit(requireContext())
            val formattedSpeed = UnitUtils.convertSpeed(avgSpeed, requireContext())
            binding.averageSpeedText.text = String.format("Avg: %.1f %s", formattedSpeed, speedUnit)
        }

        trackingViewModel.distance.observe(viewLifecycleOwner) { distance ->
            binding.distanceText.text = UnitUtils.formatDistance(distance, requireContext())
        }

        trackingViewModel.duration.observe(viewLifecycleOwner) { duration ->
            binding.durationText.text = formatDuration(duration)
        }

        // Observe heart rate data
        trackingViewModel.heartRate.observe(viewLifecycleOwner) { heartRate ->
            binding.heartRateText.text = if (heartRate > 0) {
                "$heartRate bpm"
            } else {
                "-- bpm"
            }
        }

        // Observe ride state to update button text and visibility
        trackingViewModel.rideState.observe(viewLifecycleOwner) { state ->
            updateButtonsForRideState(state)
        }

        // Set up button listeners
        binding.startStopButton.setOnClickListener {
            android.util.Log.d("TrackingFragment", "Start/Stop button clicked. Current state: ${trackingViewModel.rideState.value}")
            when (trackingViewModel.rideState.value) {
                RideState.IDLE -> {
                    android.util.Log.d("TrackingFragment", "Starting ride...")
                    trackingViewModel.startRide()
                }
                RideState.ACTIVE -> {
                    android.util.Log.d("TrackingFragment", "Pausing ride...")
                    trackingViewModel.pauseRide()
                }
                RideState.PAUSED -> {
                    android.util.Log.d("TrackingFragment", "Resuming ride...")
                    trackingViewModel.resumeRide()
                }
                else -> { 
                    android.util.Log.d("TrackingFragment", "Unhandled state: ${trackingViewModel.rideState.value}")
                }
            }
        }

        binding.finishButton.setOnClickListener {
            showFinishRideDialog()
        }

        return root
    }

    override fun onResume() {
        super.onResume()
        // Register for SharedPreferences changes
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        // Unregister SharedPreferences listener
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == UnitUtils.PREF_UNIT_SYSTEM) {
            // Force refresh of all displayed values when unit system changes
            refreshDisplayedValues()
        }
    }

    private fun refreshDisplayedValues() {
        // Manually trigger observers to refresh displayed values
        trackingViewModel.currentSpeed.value?.let { speed ->
            binding.currentSpeedText.text = UnitUtils.formatSpeed(speed, requireContext())
        }
        
        trackingViewModel.averageSpeed.value?.let { avgSpeed ->
            val speedUnit = UnitUtils.getSpeedUnit(requireContext())
            val formattedSpeed = UnitUtils.convertSpeed(avgSpeed, requireContext())
            binding.averageSpeedText.text = String.format("Avg: %.1f %s", formattedSpeed, speedUnit)
        }
        
        trackingViewModel.distance.value?.let { distance ->
            binding.distanceText.text = UnitUtils.formatDistance(distance, requireContext())
        }
    }

    private fun showFinishRideDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Finish Ride")
            .setMessage("What would you like to do with your ride data?")
            .setPositiveButton("Save Ride") { _, _ ->
                trackingViewModel.finishRide()
            }
            .setNegativeButton("Discard") { _, _ ->
                showDiscardConfirmationDialog()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun showDiscardConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Discard Ride?")
            .setMessage("Are you sure you want to discard your ride data? This action cannot be undone.")
            .setPositiveButton("Discard") { _, _ ->
                trackingViewModel.discardRide()
            }
            .setNegativeButton("Keep Ride", null)
            .show()
    }

    private fun updateButtonsForRideState(state: RideState) {
        when (state) {
            RideState.IDLE -> {
                binding.startStopButton.text = getString(R.string.start_ride)
                binding.finishButton.visibility = View.GONE
            }
            RideState.ACTIVE -> {
                binding.startStopButton.text = getString(R.string.pause_ride)
                binding.finishButton.visibility = View.GONE
            }
            RideState.PAUSED -> {
                binding.startStopButton.text = getString(R.string.resume_ride)
                binding.finishButton.visibility = View.VISIBLE
            }
            RideState.FINISHED -> {
                // This state should quickly transition back to IDLE
                binding.startStopButton.text = getString(R.string.start_ride)
                binding.finishButton.visibility = View.GONE
            }
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = (durationMs / (1000 * 60 * 60))
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}