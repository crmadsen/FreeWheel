package com.FreeWheel.biketracker.ui.tracking

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.FreeWheel.biketracker.data.database.BikeTrackerDatabase
import com.FreeWheel.biketracker.data.model.HeartRateData
import com.FreeWheel.biketracker.data.model.LocationPoint
import com.FreeWheel.biketracker.data.model.Ride
import com.FreeWheel.biketracker.data.model.RideEvent
import com.FreeWheel.biketracker.data.model.EventType
import com.FreeWheel.biketracker.data.repository.BikeTrackerRepository
import com.FreeWheel.biketracker.service.LocationTrackingService
import com.FreeWheel.biketracker.service.HeartRateService

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.exp
import kotlin.math.max

enum class RideState {
    IDLE,      // Not started
    ACTIVE,    // Currently tracking
    PAUSED,    // Temporarily paused
    FINISHED   // Completed and saved
}

class TrackingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BikeTrackerRepository
    private var locationService: LocationTrackingService? = null
    private var heartRateService: HeartRateService? = null
    private var locationCollectionJob: Job? = null
    private var heartRateCollectionJob: Job? = null
    private var timerJob: Job? = null

    private val _currentSpeed = MutableLiveData<Double>()
    val currentSpeed: LiveData<Double> = _currentSpeed

    private val _distance = MutableLiveData<Double>()
    val distance: LiveData<Double> = _distance

    private val _duration = MutableLiveData<Long>()
    val duration: LiveData<Long> = _duration

    private val _heartRate = MutableLiveData<Int>()
    val heartRate: LiveData<Int> = _heartRate

    private val _rideState = MutableLiveData<RideState>()
    val rideState: LiveData<RideState> = _rideState

    private val _averageSpeed = MutableLiveData<Double>()
    val averageSpeed: LiveData<Double> = _averageSpeed

    private val _maxSpeed = MutableLiveData<Double>()
    val maxSpeed: LiveData<Double> = _maxSpeed

    private val _averageHeartRate = MutableLiveData<Int>()
    val averageHeartRate: LiveData<Int> = _averageHeartRate

    private val _maxHeartRate = MutableLiveData<Int>()
    val maxHeartRate: LiveData<Int> = _maxHeartRate

    private val _currentLocation = MutableLiveData<Location?>()
    val currentLocation: LiveData<Location?> = _currentLocation

    // Debug function to check tracking state
    fun debugTrackingState(): String {
        return buildString {
            appendLine("=== Tracking Debug Info ===")
            appendLine("Ride State: ${_rideState.value}")
            appendLine("Location Service: ${if (locationService != null) "Connected" else "Null"}")
            appendLine("Location Collection Job: ${if (locationCollectionJob != null) "Active" else "Null"}")
            appendLine("Service Tracking: ${locationService?.isTracking() ?: "Unknown"}")
            appendLine("Current Distance: ${_distance.value} km")
            appendLine("Current Speed: ${_currentSpeed.value} km/h")
            appendLine("Location Points: ${locationPoints.size}")
            appendLine("Total Distance: $totalDistance km")
            appendLine("Last Location: ${lastLocation?.let { "${it.latitude}, ${it.longitude}" } ?: "None"}")
            appendLine("Is Manual Pause: $isManualPause")
            appendLine("===========================")
        }
    }

    // Expose current route points for map restoration
    fun getCurrentRoutePoints(): List<LocationPoint> = locationPoints.toList()

    // Tracking data
    private var lastLocation: Location? = null
    private var lastLocationTime: Long = 0L
    private var totalDistance = 0.0
    private var rideStartTime = 0L
    private var pauseStartTime = 0L
    private var totalPausedTime = 0L
    
    // Ride data storage
    private var currentRideId: Long? = null
    private val locationPoints = mutableListOf<LocationPoint>()
    private val heartRateReadings = mutableListOf<HeartRateData>()
    private val rideEvents = mutableListOf<RideEvent>()
    private val polylinePointsList = mutableListOf<Pair<Double, Double>>()
    
    // Elevation tracking
    private var totalElevationGain = 0.0
    private var totalElevationLoss = 0.0
    private var lastAltitude: Double? = null
    
    // Heart rate tracking for averaging
    private val heartRateHistory = mutableListOf<Int>()
    private var maxHeartRateValue = 0

    // Advanced speed filtering system
    private val speedHistory = mutableListOf<Double>()
    private val maxSpeedHistorySize = 10 // For weighted averaging display
    
    // Professional speed filtering
    private val rawSpeedSamples = mutableListOf<Pair<Double, Long>>() // (speed, timestamp)
    private val medianFilterSize = 5
    private var lastSmoothSpeed = 0.0
    private var lastSpeedUpdateTime = 0L
    private val tau = 3000L // 3 seconds for EMA
    
    // Stop detection with hysteresis
    private var stopDetectionStartTime = 0L
    private var isInStopState = false
    private val stopThreshold = 0.5 * 3.6 // 0.5 m/s = 1.8 km/h
    private val resumeThreshold = 1.0 * 3.6 // 1.0 m/s = 3.6 km/h
    private val stopDetectionDuration = 2500L // 2.5 seconds
    
    // GPS point acceptance thresholds for track smoothing
    private val maxDisplacementThreshold = 8.0 // 8 meters XY displacement threshold
    private val maxElevationChangeThreshold = 1.5 // 1.5 meters elevation change threshold
    private val displacementThresholdPerSample = 3.0 // 3 meters per sample displacement threshold
    
    // Auto-pause detection with more reasonable thresholds
    private var lastSpeedTime = 0L
    private val autoPauseThreshold = 15000L // 15 seconds - less aggressive
    private val stationarySpeedThreshold = 0.5 // 0.5 km/h - more reasonable for stopped
    private val autoResumeSpeedThreshold = 2.0 // 2 km/h - easier to resume
    
    // Track whether the current pause was manual or automatic
    private var isManualPause = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            android.util.Log.d("TrackingViewModel", "Service connected: ${name?.className}")
            when (service) {
                is LocationTrackingService.LocationServiceBinder -> {
                    locationService = service.getService()
                    // If we're waiting to start tracking, start it now
                    if (_rideState.value == RideState.ACTIVE && locationCollectionJob == null) {
                        android.util.Log.d("TrackingViewModel", "Starting location collection after service connection")
                        startLocationCollection()
                    }
                }
                is HeartRateService.HeartRateServiceBinder -> {
                    heartRateService = service.getService()
                    android.util.Log.d("TrackingViewModel", "Heart rate service connected")
                    startHeartRateCollection()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            android.util.Log.d("TrackingViewModel", "Service disconnected: ${name?.className}")
            when (name?.className) {
                LocationTrackingService::class.java.name -> locationService = null
                HeartRateService::class.java.name -> heartRateService = null
            }
        }
    }

    init {
        val database = BikeTrackerDatabase.getDatabase(application)
        repository = BikeTrackerRepository(
            database.rideDao(),
            database.locationPointDao(),
            database.heartRateDao()
        )

        // Initialize values
        resetRideData()
        _rideState.value = RideState.IDLE

        // Always keep services bound for reliable access
        bindToLocationService()
        bindToHeartRateService()
    }

    private fun bindToLocationService() {
        val intent = Intent(getApplication(), LocationTrackingService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun bindToHeartRateService() {
        val intent = Intent(getApplication(), HeartRateService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun startRide() {
        android.util.Log.d("TrackingViewModel", "startRide() called. Current state: ${_rideState.value}")
        viewModelScope.launch {
            _rideState.value = RideState.ACTIVE
            android.util.Log.d("TrackingViewModel", "Ride state set to ACTIVE")
            resetRideData()
            rideStartTime = System.currentTimeMillis()
            totalPausedTime = 0L
            
            // Create new ride record in database
            val initialRide = Ride(
                startTime = Date(rideStartTime),
                endTime = null,
                isCompleted = false
            )
            currentRideId = repository.insertRide(initialRide)
            
            // Always start location service first
            startLocationService()
            
            // Start timer immediately
            startTimer()
            
            // Start location collection if service is ready, otherwise it will start in onServiceConnected
            if (locationService != null) {
                startLocationCollection()
            }
        }
    }

    fun pauseRide() {
        viewModelScope.launch {
            android.util.Log.d("TrackingViewModel", "Manual pause triggered")
            isManualPause = true
            _rideState.value = RideState.PAUSED
            pauseStartTime = System.currentTimeMillis()
            
            // Log pause event
            lastLocation?.let { location ->
                addRideEvent(location, EventType.PAUSED, "Manual pause")
            }
            
            // Keep location collection active for auto-resume detection
            // stopLocationCollection() 
            stopTimer()
        }
    }

    fun resumeRide() {
        viewModelScope.launch {
            android.util.Log.d("TrackingViewModel", "Manual resume triggered")
            isManualPause = false // Reset manual pause flag
            _rideState.value = RideState.ACTIVE
            totalPausedTime += System.currentTimeMillis() - pauseStartTime
            
            // Log resume event (or end of stopped event)
            lastLocation?.let { location ->
                addRideEvent(location, EventType.STOPPED, "Resume from pause/stop")
            }
            
            // Ensure location collection is properly restarted
            stopLocationCollection() // First stop any existing collection
            startLocationCollection() // Then start fresh
            startTimer()
        }
    }

    private fun autoPauseRide() {
        viewModelScope.launch {
            android.util.Log.d("TrackingViewModel", "Auto-pause triggered")
            isManualPause = false
            _rideState.value = RideState.PAUSED
            pauseStartTime = System.currentTimeMillis()
            
            // Log pause event
            lastLocation?.let { location ->
                addRideEvent(location, EventType.PAUSED, "Auto-pause (stationary)")
            }
            
            // Keep location collection active for auto-resume detection
            stopTimer()
        }
    }

    fun finishRide() {
        viewModelScope.launch {
            _rideState.value = RideState.FINISHED
            stopLocationService()
            stopLocationCollection()
            stopTimer()
            saveCurrentRide()
            resetRideData()
            _rideState.value = RideState.IDLE
        }
    }

    fun discardRide() {
        viewModelScope.launch {
            _rideState.value = RideState.FINISHED
            stopLocationService()
            stopLocationCollection()
            stopTimer()
            
            // Delete any partially saved ride data
            currentRideId?.let { rideId ->
                try {
                    // Delete the ride and all associated data
                    repository.deleteRideWithData(rideId)
                    android.util.Log.d("TrackingViewModel", "Ride data discarded successfully")
                } catch (e: Exception) {
                    android.util.Log.e("TrackingViewModel", "Error discarding ride data", e)
                }
            }
            
            resetRideData()
            _rideState.value = RideState.IDLE
        }
    }

    private fun startLocationService() {
        val application = getApplication<Application>()
        val intent = Intent(application, LocationTrackingService::class.java)
        application.startForegroundService(intent)
        locationService?.startLocationTracking()
    }

    private fun stopLocationService() {
        locationService?.stopLocationTracking()
        val application = getApplication<Application>()
        val intent = Intent(application, LocationTrackingService::class.java)
        application.stopService(intent)
    }

    private fun startLocationCollection() {
        // Always cancel any existing collection first
        locationCollectionJob?.cancel()
        locationCollectionJob = null
        
        locationService?.let { service ->
            // Ensure service is actually tracking
            service.startLocationTracking()
            
            locationCollectionJob = viewModelScope.launch {
                android.util.Log.d("TrackingViewModel", "Starting location collection coroutine")
                service.locationUpdates.collectLatest { location ->
                    processLocationUpdate(location)
                }
            }
        } ?: run {
            android.util.Log.w("TrackingViewModel", "Cannot start location collection - service is null")
        }
    }

    private fun stopLocationCollection() {
        locationCollectionJob?.cancel()
        locationCollectionJob = null
    }

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            while (_rideState.value == RideState.ACTIVE) {
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - rideStartTime - totalPausedTime
                _duration.value = max(0L, elapsedTime)
                kotlinx.coroutines.delay(1000) // Update every second
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun processLocationUpdate(location: Location) {
        android.util.Log.d("TrackingViewModel", "Processing location: ${location.latitude}, ${location.longitude}, Accuracy: ${location.accuracy}m, Raw Speed: ${location.speed * 3.6} km/h, State: ${_rideState.value}")
        
        // Always update current location for map display
        _currentLocation.value = location
        
        // 1) Accuracy filtering - reject poor samples
        if (location.accuracy > 25.0f) { // Reject if horizontal accuracy > 25m
            android.util.Log.d("TrackingViewModel", "Rejecting location due to poor accuracy: ${location.accuracy}m")
            return
        }
        
        // 2) GPS point acceptance thresholds for track smoothing
        // Only reject points that are obviously erroneous, not normal slow movement or stops
        lastLocation?.let { last ->
            val displacement = last.distanceTo(location)
            val elevationChange = if (location.hasAltitude() && last.hasAltitude()) {
                kotlin.math.abs(location.altitude - last.altitude)
            } else 0.0
            
            val timeDiff = (System.currentTimeMillis() - lastLocationTime) / 1000.0 // seconds
            
            android.util.Log.d("TrackingViewModel", "GPS displacement: ${displacement}m in ${timeDiff}s, elevation_change: ${elevationChange}m")
            
            // Reject GPS jumps - large displacement in short time with poor accuracy
            if (displacement > 100.0 && timeDiff < 10.0 && location.accuracy > 15.0) {
                android.util.Log.d("TrackingViewModel", "Rejecting location due to GPS jump: displacement=${displacement}m in ${timeDiff}s, accuracy=${location.accuracy}m")
                return
            }
            
            // Reject if displacement is extremely large regardless of time
            if (displacement > 500.0) {
                android.util.Log.d("TrackingViewModel", "Rejecting location due to extreme displacement: ${displacement}m")
                return
            }
            
            // Always accept location updates for speed calculation, even during stops
        }
        
        // Calculate speed from both GPS and position changes
        val rawSpeedKmh = location.speed * 3.6
        val positionBasedSpeedKmh = calculatePositionBasedSpeed(location)
        
        // Use the most reliable speed calculation
        val bestSpeedKmh = if (rawSpeedKmh > 0.5) {
            rawSpeedKmh // Use GPS speed if it seems reliable
        } else {
            positionBasedSpeedKmh // Fall back to position-based calculation
        }
        
        android.util.Log.d("TrackingViewModel", "Speed comparison - GPS: $rawSpeedKmh km/h, Position: $positionBasedSpeedKmh km/h, Using: $bestSpeedKmh km/h")
        
        // 3) Absurd jump detection
        if (bestSpeedKmh > 100.0) { // Reject impossible cycling speeds
            android.util.Log.d("TrackingViewModel", "Rejecting absurd speed: $bestSpeedKmh km/h")
            return
        }
        
        // Process speed with professional filtering
        val filteredSpeed = processSpeedWithFiltering(bestSpeedKmh)
        
        // Update UI with filtered speed
        updateCurrentSpeed(filteredSpeed)
        
        // Track elevation changes
        trackElevationChanges(location)

        // Save location data to ride history (regardless of ride state for auto-pause)
        saveLocationPoint(location, filteredSpeed)

        // Calculate distance when actively tracking OR auto-paused (but not manually paused)
        val shouldCalculateDistance = _rideState.value == RideState.ACTIVE || 
                                    (_rideState.value == RideState.PAUSED && !isManualPause)
        
        if (shouldCalculateDistance) {
            // Calculate distance
            lastLocation?.let { last ->
                val distanceMeters = last.distanceTo(location)
                // Only add distance if movement is significant (> 1m) to avoid GPS jitter
                if (distanceMeters > 1.0) {
                    totalDistance += distanceMeters / 1000.0 // Convert to kilometers
                    _distance.value = totalDistance
                    android.util.Log.d("TrackingViewModel", "Distance updated: $totalDistance km (movement: ${distanceMeters}m)")
                }
            }
            
            // Add to polyline for route visualization (always track route during rides)
            polylinePointsList.add(Pair(location.latitude, location.longitude))

            // Update average speed (only when actively moving)
            if (_rideState.value == RideState.ACTIVE) {
                val durationHours = (_duration.value ?: 0L) / (1000.0 * 60.0 * 60.0)
                if (durationHours > 0) {
                    _averageSpeed.value = totalDistance / durationHours
                }
            }
        }

        // Always update last location and time for next calculation
        lastLocation = location
        lastLocationTime = System.currentTimeMillis()
    }
    
    private fun calculatePositionBasedSpeed(location: Location): Double {
        val currentTime = System.currentTimeMillis()
        
        return lastLocation?.let { last ->
            if (lastLocationTime > 0) {
                val distance = last.distanceTo(location) // meters
                val timeDiff = (currentTime - lastLocationTime) / 1000.0 // seconds
                
                if (timeDiff > 0) {
                    val speedMs = distance / timeDiff // m/s
                    val speedKmh = speedMs * 3.6 // km/h
                    android.util.Log.d("TrackingViewModel", "Position-based speed: ${distance}m in ${timeDiff}s = $speedKmh km/h")
                    speedKmh
                } else {
                    0.0
                }
            } else {
                0.0
            }
        } ?: 0.0
    }
    
    private fun processSpeedWithFiltering(rawSpeed: Double): Double {
        val currentTime = System.currentTimeMillis()
        
        // Add raw sample with timestamp
        rawSpeedSamples.add(Pair(rawSpeed, currentTime))
        
        // Keep only recent samples (last 10 seconds)
        val cutoffTime = currentTime - 10000
        rawSpeedSamples.removeAll { it.second < cutoffTime }
        
        if (rawSpeedSamples.isEmpty()) return 0.0
        
        // 3) Median filter for spike removal
        val recentSpeeds = rawSpeedSamples.takeLast(medianFilterSize).map { it.first }
        val medianSpeed = if (recentSpeeds.size >= 3) {
            recentSpeeds.sorted()[recentSpeeds.size / 2]
        } else {
            rawSpeed
        }
        
        android.util.Log.d("TrackingViewModel", "Raw: $rawSpeed, Median: $medianSpeed")
        
        // 4) EMA smoothing
        val smoothedSpeed = if (lastSpeedUpdateTime > 0) {
            val deltaT = currentTime - lastSpeedUpdateTime
            val alpha = 1.0 - exp(-deltaT.toDouble() / tau)
            lastSmoothSpeed + alpha * (medianSpeed - lastSmoothSpeed)
        } else {
            medianSpeed
        }
        
        lastSmoothSpeed = smoothedSpeed
        lastSpeedUpdateTime = currentTime
        
        // 5) Stop detection with hysteresis
        val finalSpeed = applyStopDetection(medianSpeed, currentTime)
        
        android.util.Log.d("TrackingViewModel", "Final speed: Raw=$rawSpeed, Median=$medianSpeed, Smoothed=$smoothedSpeed, Final=$finalSpeed")
        
        return finalSpeed
    }
    
    private fun applyStopDetection(medianSpeed: Double, currentTime: Long): Double {
        when {
            // Currently moving, check if we should enter stop state
            !isInStopState && medianSpeed < stopThreshold -> {
                if (stopDetectionStartTime == 0L) {
                    stopDetectionStartTime = currentTime
                } else if (currentTime - stopDetectionStartTime > stopDetectionDuration) {
                    // Been below threshold for long enough, enter stop state
                    isInStopState = true
                    android.util.Log.d("TrackingViewModel", "Entering stop state")
                    return 0.0
                }
                return lastSmoothSpeed // Keep previous speed during detection period
            }
            
            // Currently in stop state, check if we should resume
            isInStopState && medianSpeed > resumeThreshold -> {
                isInStopState = false
                stopDetectionStartTime = 0L
                android.util.Log.d("TrackingViewModel", "Resuming from stop state")
                return lastSmoothSpeed
            }
            
            // Currently in stop state and still slow
            isInStopState -> {
                return 0.0
            }
            
            // Normal operation
            else -> {
                stopDetectionStartTime = 0L // Reset detection timer
                return lastSmoothSpeed
            }
        }
    }

    fun connectHeartRateMonitor() {
        android.util.Log.d("TrackingViewModel", "connectHeartRateMonitor called")
        // This function will be called from the UI to trigger device connection
        // The actual scanning and connection will be handled in the SettingsFragment
    }

    fun connectToHeartRateDevice(device: android.bluetooth.BluetoothDevice): Boolean {
        return heartRateService?.connectToDevice(device) ?: false
    }

    fun disconnectHeartRateMonitor() {
        heartRateService?.disconnect()
        _heartRate.value = 0
    }

    fun isHeartRateConnected(): Boolean {
        return heartRateService?.isConnected() ?: false
    }



    private fun startHeartRateCollection() {
        heartRateCollectionJob?.cancel()
        heartRateCollectionJob = null
        
        heartRateService?.let { service ->
            heartRateCollectionJob = viewModelScope.launch {
                android.util.Log.d("TrackingViewModel", "Starting heart rate collection")
                service.heartRateUpdates.collectLatest { heartRate ->
                    android.util.Log.d("TrackingViewModel", "Heart rate update: $heartRate bpm")
                    updateHeartRate(heartRate)
                }
            }
        }
    }

    private fun stopHeartRateCollection() {
        heartRateCollectionJob?.cancel()
        heartRateCollectionJob = null
    }

    fun updateCurrentSpeed(speed: Double) {
        // Speed is already filtered, just update UI and max tracking
        android.util.Log.d("TrackingViewModel", "Updating UI with filtered speed: $speed km/h")
        
        _currentSpeed.value = speed
        
        // Update max speed
        val currentMax = _maxSpeed.value ?: 0.0
        if (speed > currentMax) {
            _maxSpeed.value = speed
        }
        
        // Detect sprint events (high speed)
        if (speed > 35.0) { // Sprint threshold for cycling
            lastLocation?.let { location ->
                addRideEvent(location, EventType.SPRINT, "High speed: ${speed.toInt()} km/h")
            }
        }

        // Use filtered speed for auto-pause detection
        checkForAutoPause(speed)
    }

    private fun checkForAutoPause(speed: Double) {
        val currentTime = System.currentTimeMillis()
        
        android.util.Log.d("TrackingViewModel", "Auto-pause check: speed=$speed, state=${_rideState.value}, isManualPause=$isManualPause")
        
        when (_rideState.value) {
            RideState.ACTIVE -> {
                // Auto-pause when speed drops below stationary threshold for extended time
                if (speed < stationarySpeedThreshold) {
                    if (lastSpeedTime == 0L) {
                        lastSpeedTime = currentTime
                        android.util.Log.d("TrackingViewModel", "Speed below stationary threshold (${speed} < ${stationarySpeedThreshold}), starting pause timer")
                    } else if (currentTime - lastSpeedTime > autoPauseThreshold) {
                        // Auto-pause after threshold time at stationary speed
                        android.util.Log.d("TrackingViewModel", "Auto-pausing ride after ${autoPauseThreshold/1000} seconds")
                        autoPauseRide()
                        lastSpeedTime = 0L
                    }
                } else {
                    // Reset timer if speed picks up
                    if (lastSpeedTime != 0L) {
                        android.util.Log.d("TrackingViewModel", "Speed increased, resetting pause timer")
                    }
                    lastSpeedTime = 0L
                }
            }
            RideState.PAUSED -> {
                // Auto-resume only for automatic pauses, not manual ones
                if (!isManualPause && speed >= autoResumeSpeedThreshold) {
                    android.util.Log.d("TrackingViewModel", "Auto-resuming ride (speed: ${speed} >= ${autoResumeSpeedThreshold})")
                    resumeRide()
                    lastSpeedTime = 0L
                } else if (isManualPause) {
                    android.util.Log.d("TrackingViewModel", "Manual pause - auto-resume disabled")
                }
            }
            else -> {
                lastSpeedTime = 0L
            }
        }
    }

    fun updateDistance(distance: Double) {
        _distance.value = distance
    }

    fun updateDuration(duration: Long) {
        _duration.value = duration
        // Calculate average speed
        val dist = _distance.value ?: 0.0
        if (duration > 0) {
            val hours = duration / (1000.0 * 60.0 * 60.0)
            _averageSpeed.value = dist / hours
        }
    }

    fun updateHeartRate(heartRate: Int) {
        _heartRate.value = heartRate
        val currentMax = _maxHeartRate.value ?: 0
        if (heartRate > currentMax) {
            _maxHeartRate.value = heartRate
            maxHeartRateValue = heartRate
        }
        
        // Track heart rate history for averaging
        heartRateHistory.add(heartRate)
        
        // Save heart rate data to ride history
        currentRideId?.let { rideId ->
            val hrData = HeartRateData(
                rideId = rideId,
                heartRate = heartRate,
                timestamp = Date()
            )
            heartRateReadings.add(hrData)
        }
    }
    
    private fun trackElevationChanges(location: Location) {
        if (location.hasAltitude()) {
            lastAltitude?.let { lastAlt ->
                val elevationChange = location.altitude - lastAlt
                if (kotlin.math.abs(elevationChange) > 0.5) { // Only track significant changes
                    if (elevationChange > 0) {
                        totalElevationGain += elevationChange
                    } else {
                        totalElevationLoss += kotlin.math.abs(elevationChange)
                    }
                    
                    // Detect climb/descent events
                    if (kotlin.math.abs(elevationChange) > 5.0) { // Significant elevation change
                        val eventType = if (elevationChange > 0) EventType.CLIMB_START else EventType.DESCENT_START
                        addRideEvent(location, eventType, "Elevation change: ${elevationChange.toInt()}m")
                    }
                }
            }
            lastAltitude = location.altitude
        }
    }
    
    private fun saveLocationPoint(location: Location, speed: Double) {
        currentRideId?.let { rideId ->
            val locationPoint = LocationPoint(
                rideId = rideId,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else 0.0,
                accuracy = location.accuracy,
                speed = location.speed, // Keep as m/s for database
                timestamp = Date()
            )
            locationPoints.add(locationPoint)
        }
    }
    
    private fun addRideEvent(location: Location, eventType: EventType, notes: String? = null) {
        currentRideId?.let { rideId ->
            val event = RideEvent(
                rideId = rideId,
                eventType = eventType,
                timestamp = Date(),
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else null,
                speed = location.speed * 3.6, // Convert to km/h
                notes = notes
            )
            rideEvents.add(event)
            android.util.Log.d("TrackingViewModel", "Added ride event: $eventType at ${location.latitude}, ${location.longitude}")
        }
    }
    
    private fun resetRideData() {
        _currentSpeed.value = 0.0
        _distance.value = 0.0
        _duration.value = 0L
        _heartRate.value = 0
        _averageSpeed.value = 0.0
        _maxSpeed.value = 0.0
        _averageHeartRate.value = 0
        _maxHeartRate.value = 0
        
        totalDistance = 0.0
        lastLocation = null
        lastLocationTime = 0L
        rideStartTime = 0L
        pauseStartTime = 0L
        totalPausedTime = 0L
        
        // Clear ride data collections
        currentRideId = null
        locationPoints.clear()
        heartRateReadings.clear()
        rideEvents.clear()
        polylinePointsList.clear()
        heartRateHistory.clear()
        
        // Reset elevation tracking
        totalElevationGain = 0.0
        totalElevationLoss = 0.0
        lastAltitude = null
        maxHeartRateValue = 0
    }
    
    private suspend fun saveCurrentRide() {
        if (currentRideId == null) return
        
        try {
            // Calculate moving time (total time minus paused time)
            val movingTime = max(0L, (_duration.value ?: 0L) - totalPausedTime)
            
            // Calculate average heart rate
            val avgHeartRate = if (heartRateHistory.isNotEmpty()) {
                heartRateHistory.average().toInt()
            } else 0
            
            // Create polyline string (simplified - could use actual polyline encoding)
            val polylineString = polylinePointsList.joinToString("|") { "${it.first},${it.second}" }
            
            // Create final ride record
            val ride = Ride(
                id = currentRideId!!,
                startTime = Date(rideStartTime),
                endTime = Date(),
                distance = totalDistance,
                duration = _duration.value ?: 0L,
                movingTime = movingTime,
                averageSpeed = _averageSpeed.value ?: 0.0,
                maxSpeed = _maxSpeed.value ?: 0.0,
                averageHeartRate = avgHeartRate,
                maxHeartRate = maxHeartRateValue,
                elevationGain = totalElevationGain,
                elevationLoss = totalElevationLoss,
                polyline = polylineString,
                isCompleted = true
            )
            
            // Save all data to database
            repository.updateRide(ride)
            repository.saveLocationPoints(locationPoints)
            repository.saveHeartRateData(heartRateReadings)
            repository.saveRideEvents(rideEvents)
            
            android.util.Log.d("TrackingViewModel", "Ride saved successfully: ${locationPoints.size} location points, ${heartRateReadings.size} HR readings, ${rideEvents.size} events")
            
        } catch (e: Exception) {
            android.util.Log.e("TrackingViewModel", "Error saving ride data", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unbindService(serviceConnection)
        stopLocationService()
        stopLocationCollection()
        stopHeartRateCollection()
        stopTimer()
    }
}