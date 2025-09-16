package com.FreeWheel.biketracker.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.FreeWheel.biketracker.data.dao.HeartRateDao
import com.FreeWheel.biketracker.data.dao.LocationPointDao
import com.FreeWheel.biketracker.data.dao.RideDao
import com.FreeWheel.biketracker.data.model.HeartRateData
import com.FreeWheel.biketracker.data.model.LocationPoint
import com.FreeWheel.biketracker.data.model.Ride

@Database(
    entities = [Ride::class, LocationPoint::class, HeartRateData::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BikeTrackerDatabase : RoomDatabase() {
    
    abstract fun rideDao(): RideDao
    abstract fun locationPointDao(): LocationPointDao
    abstract fun heartRateDao(): HeartRateDao
    
    companion object {
        @Volatile
        private var INSTANCE: BikeTrackerDatabase? = null
        
        fun getDatabase(context: Context): BikeTrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BikeTrackerDatabase::class.java,
                    "bike_tracker_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}