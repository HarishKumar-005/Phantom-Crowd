package com.phantomcrowd.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phantomcrowd.data.AnchorData
import com.phantomcrowd.data.AnchorRepository
import com.phantomcrowd.data.LocalStorageManager
import com.phantomcrowd.utils.GPSUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val storageManager = LocalStorageManager(application)
    private val repository = AnchorRepository(storageManager)
    private val gpsUtils = GPSUtils(application)

    private val _anchors = MutableStateFlow<List<AnchorData>>(emptyList())
    val anchors: StateFlow<List<AnchorData>> = _anchors.asStateFlow()

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()
    
    // For AR View to consume
    val allAnchors = MutableStateFlow<List<AnchorData>>(emptyList())

    init {
        loadAnchors()
    }
    
    fun updateLocation() {
        // Start updates in utils
        gpsUtils.startLocationUpdates()
        
        viewModelScope.launch {
            // Collect updates from the flow
            gpsUtils.locationFlow.collect { location ->
                 _currentLocation.value = location
                 if (location != null) {
                    // Update nearby list using 50m radius
                    val nearby = repository.getNearbyAnchors(location.latitude, location.longitude, 50.0)
                    _anchors.value = nearby
                    
                    // Update all anchors for AR view
                    allAnchors.value = repository.getAllAnchors()
                 }
            }
        }
    }

    private fun loadAnchors() {
        viewModelScope.launch {
             allAnchors.value = repository.getAllAnchors()
        }
    }

    fun postIssue(message: String, category: String, onSuccess: () -> Unit) {
        val loc = _currentLocation.value ?: return
        viewModelScope.launch {
            repository.createAnchor(
                latitude = loc.latitude,
                longitude = loc.longitude,
                altitude = loc.altitude,
                message = message,
                category = category
            )
            updateLocation()
            onSuccess()
        }
    }
}
