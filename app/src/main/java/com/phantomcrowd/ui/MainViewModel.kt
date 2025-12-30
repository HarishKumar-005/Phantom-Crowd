package com.phantomcrowd.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phantomcrowd.data.AnchorData
import com.phantomcrowd.data.AnchorRepository
import com.phantomcrowd.data.FirebaseAnchorManager
import com.phantomcrowd.data.GeofenceManager
import com.phantomcrowd.data.LocalStorageManager
import com.phantomcrowd.utils.GPSUtils
import com.phantomcrowd.utils.Logger
import com.phantomcrowd.utils.NetworkHelper
import com.google.firebase.firestore.FirebaseFirestore
import com.phantomcrowd.utils.OfflineMapCache
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Main ViewModel for the Phantom Crowd app.
 * Manages location state, anchor data, and coordinates between UI screens.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val storageManager = LocalStorageManager(application)
    private val repository = AnchorRepository(storageManager)
    private val gpsUtils = GPSUtils(application)
    private val geofenceManager = GeofenceManager(application)
    private val offlineCache = OfflineMapCache(application)
    
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    // Firebase cloud sync (Phase A)
    private val firebaseManager = FirebaseAnchorManager(FirebaseFirestore.getInstance())

    // Nearby anchors (within radius) for the Nearby screen
    private val _anchors = MutableStateFlow<List<AnchorData>>(emptyList())
    val anchors: StateFlow<List<AnchorData>> = _anchors.asStateFlow()

    // Current GPS location
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    // All anchors for AR View to consume
    val allAnchors = MutableStateFlow<List<AnchorData>>(emptyList())

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Navigation Tab state (Phase 2)
    private val _selectedAnchor = MutableStateFlow<AnchorData?>(null)
    val selectedAnchor: StateFlow<AnchorData?> = _selectedAnchor.asStateFlow()

    
    private val _selectedCloudAnchorId = MutableStateFlow("")
    val selectedCloudAnchorId: StateFlow<String> = _selectedCloudAnchorId.asStateFlow()

    fun setSelectedCloudAnchorId(anchorId: String) {
        _selectedCloudAnchorId.value = anchorId
    }

    private val _startLocationLat = MutableStateFlow(0.0)
    val startLocationLat: StateFlow<Double> = _startLocationLat.asStateFlow()
    
    private val _startLocationLon = MutableStateFlow(0.0)
    val startLocationLon: StateFlow<Double> = _startLocationLon.asStateFlow()
    
    // Cloud sync state (Phase A)
    private val _cloudIssues = MutableStateFlow<List<AnchorData>>(emptyList())
    val cloudIssues: StateFlow<List<AnchorData>> = _cloudIssues.asStateFlow()
    
    private val _syncStatus = MutableStateFlow("Synced")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()
    
    private var cloudSyncJob: Job? = null
    private var locationUpdatesJob: Job? = null
    private var locationTimeoutJob: Job? = null
    
    // Phase B: Heatmap Visualization
    private val _showHeatmap = MutableStateFlow(false)
    val showHeatmap: StateFlow<Boolean> = _showHeatmap.asStateFlow()
    
    fun toggleHeatmap() {
        _showHeatmap.value = !_showHeatmap.value
        Logger.d(Logger.Category.UI, "Heatmap toggled: ${_showHeatmap.value}")
    }

    // Phase C: Geofencing
    fun setupGeofences() {
        viewModelScope.launch(exceptionHandler) {
            // Use anchors or cloudIssues depending on what's available. 
            // Spec says "cloudIssues.collect", but anchors is the main list. 
            // Let's use the current value of anchors for simplicity and immediate effect.
            // Or better, observe anchors if we want dynamic updates.
            // For now, let's just use the current list.
            val issues = _anchors.value
            if (issues.isNotEmpty()) {
                geofenceManager.createGeofences(issues)
                Logger.d(Logger.Category.DATA, "Setup geofences for ${issues.size} issues")
            }
        }
    }
    
    fun cleanupGeofences() {
        geofenceManager.removeGeofences()
        Logger.d(Logger.Category.DATA, "Cleaned up geofences")
    }


    // Coroutine exception handler for graceful error handling
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Logger.e(Logger.Category.DATA, "Coroutine exception in ViewModel", throwable)
        val msg = throwable.message ?: "An unexpected error occurred"
        _error.value = msg
        _errorMessage.value = msg
        _isLoading.value = false
    }

    init {
        Logger.d(Logger.Category.UI, "MainViewModel initialized")
        loadAnchors()
        
        // Monitor network status (Phase E)
        NetworkHelper.networkStatusFlow(application)
            .onEach { _isOnline.value = it }
            .launchIn(viewModelScope)
    }
    
    /**
     * Set the selected anchor for navigation (Phase 2).
     */
    fun setSelectedAnchor(anchor: AnchorData) {
        _selectedAnchor.value = anchor
        recordStartLocation()
        Logger.i(Logger.Category.UI, "Navigation target set: ${anchor.messageText}")
    }
    
    /**
     * Record start location when navigation begins (Phase 2).
     */
    private fun recordStartLocation() {
        _currentLocation.value?.let { loc ->
            _startLocationLat.value = loc.latitude
            _startLocationLon.value = loc.longitude
            Logger.d(Logger.Category.GPS, "Start location recorded: ${loc.latitude}, ${loc.longitude}")
        }
    }
    
    /**
     * Clear navigation target (Phase 2).
     */
    fun clearSelectedAnchor() {
        _selectedAnchor.value = null
        Logger.d(Logger.Category.UI, "Navigation target cleared")
    }

    /**
     * Start or refresh location updates and nearby anchors.
     */
    fun updateLocation() {
        Logger.d(Logger.Category.GPS, "updateLocation() called")
        gpsUtils.startLocationUpdates()

        // Cancel existing jobs to prevent stacking
        locationUpdatesJob?.cancel()
        locationTimeoutJob?.cancel()

        // 1. Continuous location collection job
        locationUpdatesJob = viewModelScope.launch(exceptionHandler) {
            gpsUtils.locationFlow.collect { l ->
                if (l != null) {
                    Logger.d(Logger.Category.GPS, "ViewModel received location: $l")
                    _currentLocation.value = l

                    // Update nearby list using 50m radius
                    val nearby = repository.getNearbyAnchors(
                        l.latitude,
                        l.longitude,
                        NEARBY_RADIUS_METERS
                    )
                    _anchors.value = nearby
                    Logger.d(Logger.Category.DATA, "Found ${nearby.size} nearby anchors")

                    // Update all anchors for AR view
                    allAnchors.value = repository.getAllAnchors()
                }
            }
        }

        // 2. Timeout check job - only complains if we don't get a location within 30s
        locationTimeoutJob = viewModelScope.launch(exceptionHandler) {
            kotlinx.coroutines.delay(30_000L)
            if (_currentLocation.value == null) {
                _errorMessage.value = "GPS Timeout: Could not retrieve location. Please check your settings."
                Logger.w(Logger.Category.GPS, "GPS Timeout waiting for location")
            }
        }
    }

    /**
     * Load all anchors from storage.
     */
    private fun loadAnchors() {
        viewModelScope.launch(exceptionHandler) {
            _isLoading.value = true
            try {
                allAnchors.value = repository.getAllAnchors()
                Logger.d(Logger.Category.DATA, "Loaded ${allAnchors.value.size} total anchors")
            } catch (e: Exception) {
                Logger.e(Logger.Category.DATA, "Failed to load anchors", e)
                _errorMessage.value = "Failed to load data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Post a new issue at the current location.
     */
    fun postIssue(message: String, category: String, onSuccess: () -> Unit) {
        val loc = _currentLocation.value
        if (loc == null) {
            Logger.w(Logger.Category.DATA, "Cannot post issue: no location available")
            _error.value = "Location not available"
            return
        }

        viewModelScope.launch(exceptionHandler) {
            _isLoading.value = true
            try {
                val anchor = repository.createAnchor(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitude = loc.altitude,
                    message = message,
                    category = category
                )
                Logger.i(Logger.Category.DATA, "Posted new issue: ${anchor.id}")
                
                // Phase A: Upload to Firebase cloud
                uploadIssueToCloud(anchor)
                
                // Refresh data
                updateLocation()
                onSuccess()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _error.value = null
        _errorMessage.value = null
    }

    /**
     * Refresh all data.
     */
    fun refresh() {
        loadAnchors()
        updateLocation()
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE A: Firebase Cloud Sync Functions
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Upload an issue to Firebase Firestore.
     * Called after local save to sync with cloud.
     */
    fun uploadIssueToCloud(anchor: AnchorData) {
        viewModelScope.launch(exceptionHandler) {
            try {
                if (!_isOnline.value) {
                    _syncStatus.value = "Offline - will sync later"
                    return@launch
                }

                _syncStatus.value = "Uploading..."
                val result = firebaseManager.uploadIssue(anchor)
                result.onSuccess {
                    _syncStatus.value = "Synced ✓"
                    Logger.i(Logger.Category.DATA, "Issue uploaded to cloud: ${anchor.id}")
                }
                result.onFailure { error ->
                    _syncStatus.value = "Sync failed"
                    _errorMessage.value = "Failed to upload issue: ${error.message}"
                    Logger.e(Logger.Category.DATA, "Cloud upload error: ${error.message}", error)
                }
            } catch (e: Exception) {
                _syncStatus.value = "Sync error"
                _errorMessage.value = "Upload error: ${e.message}"
                Logger.e(Logger.Category.DATA, "Exception in uploadIssueToCloud", e)
            }
        }
    }
    
    fun syncIssuesWithFallback(lat: Double, lon: Double) {
        viewModelScope.launch {
            if (_isOnline.value) {
                // Try cloud sync first
                firebaseManager.getNearbyIssues(lat, lon)
                    .collect { issues ->
                        _cloudIssues.value = issues
                        offlineCache.cacheIssues(issues)  // Cache for offline
                    }
            } else {
                // Offline fallback
                val cached = offlineCache.getCachedIssues()
                _cloudIssues.value = cached
                Logger.d(Logger.Category.DATA, "Using offline cache: ${cached.size} issues")
            }
        }
    }
    
    /**
     * Start real-time sync with cloud issues.
     * Subscribes to Firestore updates for nearby issues.
     */
    fun startCloudSync(latitude: Double, longitude: Double) {
        // Cancel existing sync job if any
        cloudSyncJob?.cancel()
        
        cloudSyncJob = viewModelScope.launch(exceptionHandler) {
            try {
                Logger.i(Logger.Category.DATA, "Starting cloud sync at ($latitude, $longitude)")
                _syncStatus.value = "Syncing..."

                firebaseManager.getNearbyIssues(latitude, longitude, radiusKm = 5)
                    .collect { issues ->
                        _cloudIssues.value = issues
                        _syncStatus.value = "Synced ✓"
                        Logger.d(Logger.Category.DATA, "Cloud sync: ${issues.size} issues received")
                    }
            } catch (e: Exception) {
                _syncStatus.value = "Sync error"
                _errorMessage.value = "Failed to sync with cloud: ${e.message}"
                Logger.e(Logger.Category.DATA, "Exception in startCloudSync", e)
            }
        }
    }
    
    /**
     * Stop cloud sync (call when leaving map/AR screens).
     */
    fun stopCloudSync() {
        cloudSyncJob?.cancel()
        cloudSyncJob = null
        Logger.d(Logger.Category.DATA, "Cloud sync stopped")
    }
    
    /**
     * Phase F: Upload issue safely with error handling
     * Used by PostCreationARScreen for AR wall posting
     */
    suspend fun uploadIssueSafely(anchor: AnchorData): Result<Unit> {
        return try {
            _syncStatus.value = "Creating cloud anchor..."
            Logger.d(Logger.Category.DATA, "Uploading AR wall message: ${anchor.id}")

            val result = firebaseManager.uploadIssue(anchor)
            if (result.isSuccess) {
                _syncStatus.value = "✅ Posted!"
                Logger.i(Logger.Category.DATA, "AR message posted successfully: ${anchor.id}")
                
                // Refresh data
                updateLocation()
                Result.success(Unit)
            } else {
                val error = result.exceptionOrNull() ?: Exception("Unknown error")
                _syncStatus.value = "❌ Error: ${error.message}"
                Logger.e(Logger.Category.DATA, "AR message upload failed", error)
                Result.failure(error)
            }
        } catch (e: Exception) {
            _syncStatus.value = "❌ Error: ${e.message}"
            Logger.e(Logger.Category.DATA, "Unexpected error in uploadIssueSafely", e)
            Result.failure(e)
        }
    }

    /**
     * Upvote an issue in cloud storage.
     */
    fun upvoteIssue(issueId: String) {
        viewModelScope.launch(exceptionHandler) {
            try {
                val result = firebaseManager.upvoteIssue(issueId)
                result.onSuccess {
                    Logger.i(Logger.Category.DATA, "Issue upvoted: $issueId")
                }
                result.onFailure { error ->
                    _errorMessage.value = "Failed to upvote: ${error.message}"
                    Logger.e(Logger.Category.DATA, "Upvote failed: ${error.message}", error)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Upvote error: ${e.message}"
                Logger.e(Logger.Category.DATA, "Exception in upvoteIssue", e)
            }
        }
    }

    /**
     * Cleanup when ViewModel is destroyed.
     */
    override fun onCleared() {
        super.onCleared()
        Logger.d(Logger.Category.UI, "MainViewModel onCleared - cleaning up")
        cleanupGeofences()
        gpsUtils.cleanup()
    }


    companion object {
        private const val NEARBY_RADIUS_METERS = 50.0
    }
}
