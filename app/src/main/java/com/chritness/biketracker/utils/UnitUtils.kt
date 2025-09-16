package com.FreeWheel.biketracker.utils

import android.content.Context
import android.content.SharedPreferences

object UnitUtils {
    // Constants for unit system
    const val PREFS_NAME = "bike_tracker_settings"
    const val PREF_UNIT_SYSTEM = "unit_system"
    const val UNIT_METRIC = "metric"
    const val UNIT_IMPERIAL = "imperial"
    
    // Conversion constants
    private const val KM_TO_MILES = 0.621371
    private const val MILES_TO_KM = 1.60934
    
    /**
     * Get the current unit system from SharedPreferences
     */
    fun getUnitSystem(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_UNIT_SYSTEM, UNIT_METRIC) ?: UNIT_METRIC
    }
    
    /**
     * Check if imperial units are enabled
     */
    fun isImperialUnits(context: Context): Boolean {
        return getUnitSystem(context) == UNIT_IMPERIAL
    }
    
    /**
     * Convert speed from km/h to appropriate unit
     */
    fun formatSpeed(speedKmh: Double, context: Context): String {
        return if (isImperialUnits(context)) {
            val speedMph = speedKmh * KM_TO_MILES
            String.format("%.1f mph", speedMph)
        } else {
            String.format("%.1f km/h", speedKmh)
        }
    }
    
    /**
     * Convert distance from km to appropriate unit
     */
    fun formatDistance(distanceKm: Double, context: Context): String {
        return if (isImperialUnits(context)) {
            val distanceMiles = distanceKm * KM_TO_MILES
            String.format("%.2f mi", distanceMiles)
        } else {
            String.format("%.2f km", distanceKm)
        }
    }
    
    /**
     * Get speed unit label
     */
    fun getSpeedUnit(context: Context): String {
        return if (isImperialUnits(context)) "mph" else "km/h"
    }
    
    /**
     * Get distance unit label
     */
    fun getDistanceUnit(context: Context): String {
        return if (isImperialUnits(context)) "mi" else "km"
    }
    
    /**
     * Convert speed value from km/h to appropriate unit (without formatting)
     */
    fun convertSpeed(speedKmh: Double, context: Context): Double {
        return if (isImperialUnits(context)) {
            speedKmh * KM_TO_MILES
        } else {
            speedKmh
        }
    }
    
    /**
     * Convert distance value from km to appropriate unit (without formatting)
     */
    fun convertDistance(distanceKm: Double, context: Context): Double {
        return if (isImperialUnits(context)) {
            distanceKm * KM_TO_MILES
        } else {
            distanceKm
        }
    }
}