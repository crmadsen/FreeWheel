package com.FreeWheel.biketracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "rides")
data class Ride(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Date,
    val endTime: Date?,
    val distance: Double = 0.0, // in kilometers
    val duration: Long = 0, // in milliseconds (total duration)
    val movingTime: Long = 0, // in milliseconds (excluding pauses)
    val averageSpeed: Double = 0.0, // km/h
    val maxSpeed: Double = 0.0, // km/h
    val averageHeartRate: Int = 0, // bpm
    val maxHeartRate: Int = 0, // bpm
    val elevationGain: Double = 0.0, // in meters
    val elevationLoss: Double = 0.0, // in meters
    val polyline: String = "", // Encoded polyline for route visualization
    val isCompleted: Boolean = false
)