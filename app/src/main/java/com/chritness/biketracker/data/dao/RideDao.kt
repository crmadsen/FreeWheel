package com.FreeWheel.biketracker.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.FreeWheel.biketracker.data.model.Ride

@Dao
interface RideDao {
    
    @Query("SELECT * FROM rides ORDER BY startTime DESC")
    fun getAllRides(): LiveData<List<Ride>>
    
    @Query("SELECT * FROM rides WHERE id = :rideId")
    suspend fun getRideById(rideId: Long): Ride?
    
    @Query("SELECT * FROM rides WHERE isCompleted = 0 LIMIT 1")
    suspend fun getCurrentRide(): Ride?
    
    @Insert
    suspend fun insertRide(ride: Ride): Long
    
    @Update
    suspend fun updateRide(ride: Ride)
    
    @Delete
    suspend fun deleteRide(ride: Ride)
    
    @Query("DELETE FROM rides WHERE id = :rideId")
    suspend fun deleteRideById(rideId: Long)
}