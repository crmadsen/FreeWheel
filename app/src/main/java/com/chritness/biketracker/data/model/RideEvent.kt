package com.FreeWheel.biketracker.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

enum class EventType {
    STOPPED,        // Auto-pause/resume events
    PAUSED,         // Manual pause events
    SPRINT,         // High speed/acceleration event
    CLIMB_START,    // Beginning of significant elevation gain
    CLIMB_END,      // End of significant elevation gain
    DESCENT_START,  // Beginning of significant elevation loss
    DESCENT_END     // End of significant elevation loss
}

@Entity(
    tableName = "ride_events",
    foreignKeys = [
        ForeignKey(
            entity = Ride::class,
            parentColumns = ["id"],
            childColumns = ["rideId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["rideId"])]
)
data class RideEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rideId: Long,
    val eventType: EventType,
    val timestamp: Date,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val speed: Double? = null, // km/h at time of event
    val notes: String? = null // Additional event details
)