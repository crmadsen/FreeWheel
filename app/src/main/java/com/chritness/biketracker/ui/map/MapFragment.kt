package com.FreeWheel.biketracker.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.FreeWheel.biketracker.R
import com.FreeWheel.biketracker.databinding.FragmentMapBinding
import com.FreeWheel.biketracker.ui.tracking.RideState
import com.FreeWheel.biketracker.ui.tracking.TrackingViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    
    private val trackingViewModel: TrackingViewModel by activityViewModels()
    private var currentLocationMarker: Marker? = null
    private var routePolyline: Polyline? = null
    private val routePoints = mutableListOf<GeoPoint>()
    private var lastDisplayedLocation: Location? = null
    private var lastMapUpdateTime: Long = 0
    private var hasZoomedToLocation: Boolean = false // Track if we've zoomed to first GPS location

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Initialize osmdroid configuration
        Configuration.getInstance().load(
            requireContext(),
            requireContext().getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )

        // Set up the map
        setupMap()
        
        // Set up FAB for current location
        binding.fabCurrentLocation.setOnClickListener {
            centerOnCurrentLocation()
        }

        // Observe location updates from TrackingViewModel instead of binding to service directly
        trackingViewModel.currentLocation.observe(viewLifecycleOwner) { location ->
            location?.let { updateLocationOnMap(it) }
        }

        // Observe ride state to manage route tracking
        trackingViewModel.rideState.observe(viewLifecycleOwner) { state ->
            android.util.Log.d("MapFragment", "Ride state changed to: $state")
            when (state) {
                RideState.ACTIVE -> {
                    android.util.Log.d("MapFragment", "Ride is ACTIVE, creating route if needed")
                    if (routePolyline == null) {
                        createRoutePolyline()
                        android.util.Log.d("MapFragment", "Created new route polyline")
                        
                        // If this is a fragment recreation during an active ride, restore the route
                        restoreActiveRoute()
                    } else {
                        android.util.Log.d("MapFragment", "Route polyline already exists")
                    }
                }
                RideState.PAUSED -> {
                    android.util.Log.d("MapFragment", "Ride is PAUSED - keeping existing route")
                    // Ensure route exists even if fragment was recreated during pause
                    if (routePolyline == null) {
                        createRoutePolyline()
                        restoreActiveRoute()
                    }
                }
                RideState.FINISHED, RideState.IDLE -> {
                    android.util.Log.d("MapFragment", "Ride is FINISHED/IDLE, clearing route")
                    clearRoute()
                }
            }
        }

        return root
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        
        // Ensure route is properly restored when returning to map fragment
        if (trackingViewModel.rideState.value == RideState.ACTIVE || 
            trackingViewModel.rideState.value == RideState.PAUSED) {
            
            // Force route restoration to handle tab switching issues
            if (routePolyline == null) {
                android.util.Log.d("MapFragment", "onResume: Route polyline missing, recreating")
                createRoutePolyline()
                restoreActiveRoute()
            } else {
                // Refresh existing route to fix any display issues
                android.util.Log.d("MapFragment", "onResume: Refreshing existing route")
                restoreActiveRoute()
            }
        }
        
        // Update current location marker if available
        trackingViewModel.currentLocation.value?.let { location ->
            updateLocationMarker(location)
        }
    }

    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            
            // Start centered on Colorado Springs with wider zoom to show area context
            controller.setZoom(12.0) // Wider zoom to show Colorado Springs area
            controller.setCenter(GeoPoint(38.8339, -104.8214)) // Colorado Springs, CO
        }

        // Create and configure the current location marker
        createLocationMarker()
    }


    private fun createLocationMarker() {
        // Create a custom marker for current location
        currentLocationMarker = Marker(binding.mapView).apply {
            // Set custom marker icon (smaller than default)
            try {
                val markerDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.location_marker)
                markerDrawable?.let { drawable ->
                    // Make the marker smaller (scale down by 75%)
                    val scaleFactor = 0.75f
                    val newWidth = (drawable.intrinsicWidth * scaleFactor).toInt()
                    val newHeight = (drawable.intrinsicHeight * scaleFactor).toInt()
                    
                    val bitmap = android.graphics.Bitmap.createBitmap(
                        newWidth,
                        newHeight,
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bitmap)
                    drawable.setBounds(0, 0, newWidth, newHeight)
                    drawable.draw(canvas)
                    
                    icon = android.graphics.drawable.BitmapDrawable(resources, bitmap)
                }
            } catch (e: Exception) {
                // Fall back to default location icon if custom marker fails
                icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_mylocation)
            }
            
            // Configure marker properties
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) // Center the marker on the location
            title = "Current Location"
            setInfoWindow(null) // Disable info window to prevent accidental taps
        }
        
        // Initially hide the marker until we have a location
        // Don't add to overlays yet - will be added when first location is received
    }

    private fun updateLocationMarker(location: Location) {
        val geoPoint = GeoPoint(location.latitude, location.longitude)
        
        currentLocationMarker?.let { marker ->
            // Update marker position
            marker.position = geoPoint
            
            // Add marker to map if not already added
            if (!binding.mapView.overlays.contains(marker)) {
                // Add marker on top of route (last in overlay list)
                binding.mapView.overlays.add(marker)
                android.util.Log.d("MapFragment", "Added location marker to map overlays")
            }
            
            // Force map redraw to show updated marker position
            binding.mapView.invalidate()
            
            android.util.Log.d("MapFragment", "Updated location marker to: ${location.latitude}, ${location.longitude}")
        }
    }
    private fun updateLocationOnMap(location: Location) {
        val geoPoint = GeoPoint(location.latitude, location.longitude)
        val currentTime = System.currentTimeMillis()
        
        android.util.Log.d("MapFragment", "Updating location on map: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}")
        android.util.Log.d("MapFragment", "Ride state: ${trackingViewModel.rideState.value}")
        
        // Enhanced accuracy filtering - stricter for map display
        if (location.accuracy > 20f) {
            android.util.Log.d("MapFragment", "Skipping location update due to poor accuracy: ${location.accuracy}m")
            return
        }
        
        // Distance-based filtering to reduce jerkiness when stationary
        lastDisplayedLocation?.let { lastLoc ->
            val distanceFromLast = lastLoc.distanceTo(location)
            
            // If we haven't moved much (< 3m) and accuracy isn't significantly better, skip update
            if (distanceFromLast < 3.0f && location.accuracy >= lastLoc.accuracy * 0.8f) {
                android.util.Log.d("MapFragment", "Skipping location update - minimal movement: ${distanceFromLast}m")
                // Still add to route if tracking, but don't update visual marker
                if (trackingViewModel.rideState.value == RideState.ACTIVE) {
                    addPointToRoute(geoPoint)
                }
                return
            }
        }
        
        // Rate limiting - don't update map visuals more than once per second
        if (currentTime - lastMapUpdateTime < 1000) {
            android.util.Log.d("MapFragment", "Skipping location update - rate limited")
            // Still add to route if tracking
            if (trackingViewModel.rideState.value == RideState.ACTIVE) {
                addPointToRoute(geoPoint)
            }
            return
        }
        
        // Add to route if actively tracking
        if (trackingViewModel.rideState.value == RideState.ACTIVE) {
            android.util.Log.d("MapFragment", "Adding point to route: $geoPoint")
            addPointToRoute(geoPoint)
        }
        
        // Update the location marker at the precise position
        updateLocationMarker(location)
        
        // Handle map centering
        if (!hasZoomedToLocation) {
            android.util.Log.d("MapFragment", "First GPS lock - zooming to location: ${location.latitude}, ${location.longitude}")
            binding.mapView.controller.animateTo(geoPoint, 17.0, 2000L) // Animate to location with 17x zoom over 2 seconds
            hasZoomedToLocation = true
        } else {
            // Use gentle map centering for subsequent updates to avoid jerky movement
            binding.mapView.controller.setCenter(geoPoint)
        }
        
        // Update tracking variables
        lastDisplayedLocation = location
        lastMapUpdateTime = currentTime
    }

    private fun createRoutePolyline() {
        android.util.Log.d("MapFragment", "Creating route polyline")
        routePolyline = Polyline().apply {
            outlinePaint.color = Color.parseColor("#4285F4") // Google Maps blue
            outlinePaint.strokeWidth = 16f // Thicker line for better visibility
            outlinePaint.alpha = 200 // Slightly transparent
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
            outlinePaint.isAntiAlias = true
        }
        
        // Add route polyline behind location marker (at index 0)
        binding.mapView.overlays.add(0, routePolyline)
        routePoints.clear()
        
        android.util.Log.d("MapFragment", "Route polyline created and added to map overlays")
        
        // Refresh map to show the new overlay
        binding.mapView.invalidate()
    }

    private fun addPointToRoute(point: GeoPoint) {
        android.util.Log.d("MapFragment", "addPointToRoute called with: $point")
        routePolyline?.let { polyline ->
            routePoints.add(point)
            android.util.Log.d("MapFragment", "Route now has ${routePoints.size} points")
            
            // Only draw route if we have at least 2 points
            if (routePoints.size >= 2) {
                polyline.setPoints(ArrayList(routePoints)) // Create new list to trigger update
                binding.mapView.invalidate()
                android.util.Log.d("MapFragment", "Route polyline updated with ${routePoints.size} points")
            }
            
            // Update location marker to the latest route point (this ensures perfect alignment)
            currentLocationMarker?.let { marker ->
                marker.position = point
                android.util.Log.d("MapFragment", "Updated location marker to latest route point: $point")
                
                // Add marker to map if not already added
                if (!binding.mapView.overlays.contains(marker)) {
                    binding.mapView.overlays.add(marker)
                    android.util.Log.d("MapFragment", "Added location marker to map overlays")
                }
            }
            
        } ?: run {
            android.util.Log.w("MapFragment", "routePolyline is null! Cannot add point to route")
        }
    }

    private fun clearRoute() {
        routePolyline?.let { polyline ->
            binding.mapView.overlays.remove(polyline)
        }
        routePolyline = null
        routePoints.clear()
        
        // Remove location marker when route is cleared
        currentLocationMarker?.let { marker ->
            binding.mapView.overlays.remove(marker)
        }
        
        binding.mapView.invalidate()
    }

    private fun restoreActiveRoute() {
        android.util.Log.d("MapFragment", "Restoring active route from TrackingViewModel")
        val currentPoints = trackingViewModel.getCurrentRoutePoints()
        
        if (currentPoints.isNotEmpty()) {
            android.util.Log.d("MapFragment", "Restoring ${currentPoints.size} route points")
            
            // Clear existing route points to prevent duplication
            routePoints.clear()
            
            // Convert LocationPoint to GeoPoint and add to route
            currentPoints.forEach { locationPoint ->
                val geoPoint = GeoPoint(locationPoint.latitude, locationPoint.longitude)
                routePoints.add(geoPoint)
            }
            
            // Update the polyline with restored points if we have enough points
            routePolyline?.let { polyline ->
                if (routePoints.size >= 2) {
                    polyline.setPoints(ArrayList(routePoints)) // Create new list to trigger update
                    binding.mapView.invalidate()
                    android.util.Log.d("MapFragment", "Route restored and updated with ${routePoints.size} points")
                } else {
                    android.util.Log.d("MapFragment", "Not enough points to draw route (${routePoints.size} < 2)")
                }
            } ?: run {
                android.util.Log.w("MapFragment", "Route polyline is null during restoration")
            }
            
            // Position marker at the last route point for perfect alignment
            if (routePoints.isNotEmpty()) {
                val lastPoint = routePoints.last()
                currentLocationMarker?.let { marker ->
                    marker.position = lastPoint
                    if (!binding.mapView.overlays.contains(marker)) {
                        binding.mapView.overlays.add(marker)
                    }
                    android.util.Log.d("MapFragment", "Restored location marker to last route point: $lastPoint")
                }
            }
        } else {
            android.util.Log.d("MapFragment", "No route points to restore")
        }
    }

    private fun centerOnCurrentLocation() {
        currentLocationMarker?.position?.let { geoPoint ->
            binding.mapView.controller.animateTo(geoPoint)
            binding.mapView.controller.setZoom(17.0) // Consistent zoom for tracking
        } ?: run {
            // Fallback to current location from tracking service
            trackingViewModel.currentLocation.value?.let { location ->
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                binding.mapView.controller.animateTo(geoPoint)
                binding.mapView.controller.setZoom(17.0)
            } ?: run {
                // Final fallback to Colorado Springs if no GPS location yet
                binding.mapView.controller.animateTo(GeoPoint(38.8339, -104.8214))
                binding.mapView.controller.setZoom(17.0)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        clearRoute()
        // Remove location marker
        currentLocationMarker?.let { marker ->
            binding.mapView.overlays.remove(marker)
        }
        currentLocationMarker = null
        _binding = null
    }
}