package com.FreeWheel.biketracker.data.repository

import androidx.lifecycle.LiveData
import com.FreeWheel.biketracker.data.dao.HeartRateDao
import com.FreeWheel.biketracker.data.dao.LocationPointDao
import com.FreeWheel.biketracker.data.dao.RideDao
import com.FreeWheel.biketracker.data.model.HeartRateData
import com.FreeWheel.biketracker.data.model.LocationPoint
import com.FreeWheel.biketracker.data.model.Ride
import com.FreeWheel.biketracker.data.model.RideEvent

class BikeTrackerRepository(
    private val rideDao: RideDao,
    private val locationPointDao: LocationPointDao,
    private val heartRateDao: HeartRateDao
) {
    
    // Ride operations
    fun getAllRides(): LiveData<List<Ride>> = rideDao.getAllRides()
    
    suspend fun insertRide(ride: Ride): Long = rideDao.insertRide(ride)
    
    suspend fun updateRide(ride: Ride) = rideDao.updateRide(ride)
    
    suspend fun getRideById(rideId: Long): Ride? = rideDao.getRideById(rideId)
    
    suspend fun getCurrentRide(): Ride? = rideDao.getCurrentRide()
    
    suspend fun deleteRide(ride: Ride) = rideDao.deleteRide(ride)
    
    suspend fun deleteRideWithData(rideId: Long) {
        // Delete associated location points first
        locationPointDao.deleteLocationPointsForRide(rideId)
        
        // Delete associated heart rate data
        heartRateDao.deleteHeartRateDataForRide(rideId)
        
        // Finally delete the ride itself
        rideDao.deleteRideById(rideId)
    }
    
    // Location point operations
    fun getLocationPointsForRide(rideId: Long): LiveData<List<LocationPoint>> = 
        locationPointDao.getLocationPointsForRide(rideId)
    
    suspend fun insertLocationPoint(locationPoint: LocationPoint) = 
        locationPointDao.insertLocationPoint(locationPoint)
        
    suspend fun saveLocationPoints(locationPoints: List<LocationPoint>) {
        locationPoints.forEach { insertLocationPoint(it) }
    }
    
    suspend fun getLocationPointsForRideSync(rideId: Long): List<LocationPoint> = 
        locationPointDao.getLocationPointsForRideSync(rideId)
    
    // Heart rate operations
    fun getHeartRateDataForRide(rideId: Long): LiveData<List<HeartRateData>> = 
        heartRateDao.getHeartRateDataForRide(rideId)
    
    suspend fun insertHeartRateData(heartRateData: HeartRateData) = 
        heartRateDao.insertHeartRateData(heartRateData)
        
    suspend fun saveHeartRateData(heartRateData: List<HeartRateData>) {
        heartRateData.forEach { insertHeartRateData(it) }
    }
    
    suspend fun getHeartRateDataForRideSync(rideId: Long): List<HeartRateData> = 
        heartRateDao.getHeartRateDataForRideSync(rideId)
        
    // Ride events operations (placeholder for now)
    suspend fun saveRideEvents(rideEvents: List<RideEvent>) {
        // TODO: Implement when RideEvent DAO is created
        // For now, just log the events
        rideEvents.forEach { event ->
            android.util.Log.d("BikeTrackerRepository", "Saving ride event: ${event.eventType} at ${event.timestamp}")
        }
    }
}