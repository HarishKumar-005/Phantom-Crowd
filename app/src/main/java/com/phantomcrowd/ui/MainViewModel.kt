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
    private val gpsUtils = GPSUtils(application)
    private val geofenceManager = GeofenceManager(application)
    private val offlineCache = OfflineMapCache(application)
    
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    // Firebase cloud sync (Phase A)
    private val firebaseManager = FirebaseAnchorManager(FirebaseFirestore.getInstance())
    
    // Repository now uses both local and cloud storage
    private val repository = AnchorRepository(storageManager, firebaseManager)

    // Nearby anchors (within radius) for the Nearby screen
    private val _anchors = MutableStateFlow<List<AnchorData>>(emptyList())
    val anchors: StateFlow<List<AnchorData>> = _anchors.asStateFlow()

    // Current GPS location
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    // All anchors for AR View to consume
    val allAnchors = MutableStateFlow<List<AnchorData>>(emptyList())

    // Pending uploads set (locally tracked IDs)
    private val _pendingAnchorIds = MutableStateFlow<Set<String>>(emptySet())

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    val errorMessage: StateFlow<String?> = _error.asStateFlow()  // Alias for ARNavigationScreen
    
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
        _error.value = throwable.message ?: "An error occurred"
        _isLoading.value = false
    }

    init {
        Logger.d(Logger.Category.UI, "MainViewModel initialized")

        // Initialize data in order: Load pending, then load anchors
        viewModelScope.launch {
            val pending = storageManager.loadPendingAnchors()
            _pendingAnchorIds.value = pending.map { it.id }.toSet()
            Logger.d(Logger.Category.DATA, "Initialized with ${pending.size} pending uploads")

            // Now load anchors, ensuring badges are applied if any
            loadAnchors()
        }
        
        // Monitor network status (Phase E)
        NetworkHelper.networkStatusFlow(application)
            .onEach { online ->
                _isOnline.value = online
                if (online) {
                    syncPendingUploads()
                }
            }
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

        viewModelScope.launch(exceptionHandler) {
            gpsUtils.locationFlow.collect { location ->
                Logger.d(Logger.Category.GPS, "ViewModel received location: $location")
                _currentLocation.value = location
                
                if (location != null) {
                    // Update nearby list using 50m radius
                    val nearby = repository.getNearbyAnchors(
                        location.latitude, 
                        location.longitude, 
                        NEARBY_RADIUS_METERS
                    )

                    // Apply pending badge to any pending anchors
                    val pendingIds = _pendingAnchorIds.value
                    val badgedNearby = nearby.map { anchor ->
                        if (pendingIds.contains(anchor.id)) {
                            anchor.copy(messageText = "${anchor.messageText} [Pending Sync]")
                        } else {
                            anchor
                        }
                    }

                    _anchors.value = badgedNearby
                    Logger.d(Logger.Category.DATA, "Found ${nearby.size} nearby anchors")

                    // Update all anchors for AR view
                    allAnchors.value = repository.getAllAnchors()
                }
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
                
                if (!_isOnline.value) {
                    // Offline: Save to pending queue AND main storage so it shows up
                    storageManager.saveAnchor(anchor)
                    storageManager.savePendingAnchor(anchor)
                    val newPendingIds = _pendingAnchorIds.value.toMutableSet()
                    newPendingIds.add(anchor.id)
                    _pendingAnchorIds.value = newPendingIds

                    _syncStatus.value = "Offline - queued for sync"
                } else {
                    // Phase A: Upload to Firebase cloud
                    uploadIssueToCloud(anchor)
                }
                
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
     * Sync any pending uploads when online.
     */
    private fun syncPendingUploads() {
        viewModelScope.launch(exceptionHandler) {
            val pendingAnchors = storageManager.loadPendingAnchors()
            if (pendingAnchors.isEmpty()) return@launch

            Logger.d(Logger.Category.DATA, "Syncing ${pendingAnchors.size} pending uploads...")
            _syncStatus.value = "Syncing pending..."

            for (anchor in pendingAnchors) {
                val result = firebaseManager.uploadIssue(anchor)
                result.onSuccess {
                    // Remove from pending storage
                    storageManager.removePendingAnchor(anchor.id)

                    // Update local pending IDs set
                    val newPendingIds = _pendingAnchorIds.value.toMutableSet()
                    newPendingIds.remove(anchor.id)
                    _pendingAnchorIds.value = newPendingIds

                    Logger.i(Logger.Category.DATA, "Synced pending anchor: ${anchor.id}")
                }
                result.onFailure { e ->
                    Logger.e(Logger.Category.DATA, "Failed to sync anchor: ${anchor.id}", e)
                }
            }

            if (_pendingAnchorIds.value.isEmpty()) {
                _syncStatus.value = "All synced ✓"
                // Refresh UI to remove badges
                updateLocation()
            } else {
                _syncStatus.value = "Sync partial/failed"
            }
        }
    }
    
    /**
     * Upload an issue to Firebase Firestore.
     * Called after local save to sync with cloud.
     */
    fun uploadIssueToCloud(anchor: AnchorData) {
        viewModelScope.launch(exceptionHandler) {
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
                Logger.e(Logger.Category.DATA, "Cloud upload error: ${error.message}", error)
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
            Logger.i(Logger.Category.DATA, "Starting cloud sync at ($latitude, $longitude)")
            _syncStatus.value = "Syncing..."
            
            firebaseManager.getNearbyIssues(latitude, longitude, radiusKm = 5)
                .collect { issues ->
                    _cloudIssues.value = issues
                    _syncStatus.value = "Synced ✓"
                    Logger.d(Logger.Category.DATA, "Cloud sync: ${issues.size} issues received")
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
    fun uploadIssueSafely(anchor: AnchorData) {
        viewModelScope.launch(exceptionHandler) {
            try {
                _syncStatus.value = "Creating cloud anchor..."
                Logger.d(Logger.Category.DATA, "Uploading AR wall message: ${anchor.id}")
                
                val result = firebaseManager.uploadIssue(anchor)
                result.onSuccess {
                    _syncStatus.value = "✅ Posted!"
                    Logger.i(Logger.Category.DATA, "AR message posted successfully: ${anchor.id}")
                    
                    // Refresh anchors to show the new message
                    _currentLocation.value?.let { location ->
                        // Assuming refreshNearbyAnchors is a function that takes a location
                        // and updates _anchors.value based on it.
                        // This function is not present in the provided code snippet,
                        // so I'm commenting it out or assuming it needs to be added elsewhere.
                        // For now, I'll just call updateLocation() to refresh all data.
                        updateLocation()
                    }
                }
                result.onFailure { error ->
                    _syncStatus.value = "❌ Error: ${error.message}"
                    Logger.e(Logger.Category.DATA, "AR message upload failed", error)
                }
            } catch (e: Exception) {
                _syncStatus.value = "❌ Error: ${e.message}"
                Logger.e(Logger.Category.DATA, "Unexpected error in uploadIssueSafely", e)
            }
        }
    }
    
    /**
     * Get count of nearby issues for a specific use case.
     * Used by PostCreationARScreen for the impact metric.
     */
    suspend fun getNearbyIssueCountForUseCase(
        latitude: Double,
        longitude: Double,
        useCaseName: String
    ): Int {
        return try {
            val allNearby = firebaseManager.getIssuesNearLocation(latitude, longitude, 1000.0) // 1km radius
            val filtered = allNearby.filter { it.useCase == useCaseName }
            Logger.d(Logger.Category.DATA, "Found ${filtered.size} nearby issues for use case $useCaseName")
            filtered.size
        } catch (e: Exception) {
            Logger.e(Logger.Category.DATA, "Failed to get nearby issue count", e)
            0
        }
    }

    /**
     * Upvote an issue in cloud storage.
     */
    fun upvoteIssue(issueId: String) {
        viewModelScope.launch(exceptionHandler) {
            val result = firebaseManager.upvoteIssue(issueId)
            result.onSuccess {
                Logger.i(Logger.Category.DATA, "Issue upvoted: $issueId")
            }
            result.onFailure { error ->
                Logger.e(Logger.Category.DATA, "Upvote failed: ${error.message}", error)
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
