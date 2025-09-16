package com.FreeWheel.biketracker.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.FreeWheel.biketracker.data.database.BikeTrackerDatabase
import com.FreeWheel.biketracker.data.model.Ride
import com.FreeWheel.biketracker.data.repository.BikeTrackerRepository

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BikeTrackerRepository
    val allRides: LiveData<List<Ride>>

    init {
        val database = BikeTrackerDatabase.getDatabase(application)
        repository = BikeTrackerRepository(
            database.rideDao(),
            database.locationPointDao(),
            database.heartRateDao()
        )
        allRides = repository.getAllRides()
    }
    
    fun deleteRide(ride: Ride) {
        viewModelScope.launch {
            repository.deleteRide(ride)
        }
    }
}