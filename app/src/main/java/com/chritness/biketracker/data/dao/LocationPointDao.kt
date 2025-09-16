package com.FreeWheel.biketracker.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.FreeWheel.biketracker.data.model.LocationPoint

@Dao
interface LocationPointDao {
    
    @Query("SELECT * FROM location_points WHERE rideId = :rideId ORDER BY timestamp ASC")
    fun getLocationPointsForRide(rideId: Long): LiveData<List<LocationPoint>>
    
    @Query("SELECT * FROM location_points WHERE rideId = :rideId ORDER BY timestamp ASC")
    suspend fun getLocationPointsForRideSync(rideId: Long): List<LocationPoint>
    
    @Insert
    suspend fun insertLocationPoint(locationPoint: LocationPoint)
    
    @Insert
    suspend fun insertLocationPoints(locationPoints: List<LocationPoint>)
    
    @Query("DELETE FROM location_points WHERE rideId = :rideId")
    suspend fun deleteLocationPointsForRide(rideId: Long)
}