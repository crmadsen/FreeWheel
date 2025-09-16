package com.FreeWheel.biketracker.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.FreeWheel.biketracker.data.model.HeartRateData

@Dao
interface HeartRateDao {
    
    @Query("SELECT * FROM heart_rate_data WHERE rideId = :rideId ORDER BY timestamp ASC")
    fun getHeartRateDataForRide(rideId: Long): LiveData<List<HeartRateData>>
    
    @Query("SELECT * FROM heart_rate_data WHERE rideId = :rideId ORDER BY timestamp ASC")
    suspend fun getHeartRateDataForRideSync(rideId: Long): List<HeartRateData>
    
    @Insert
    suspend fun insertHeartRateData(heartRateData: HeartRateData)
    
    @Insert
    suspend fun insertHeartRateData(heartRateDataList: List<HeartRateData>)
    
    @Query("DELETE FROM heart_rate_data WHERE rideId = :rideId")
    suspend fun deleteHeartRateDataForRide(rideId: Long)
}