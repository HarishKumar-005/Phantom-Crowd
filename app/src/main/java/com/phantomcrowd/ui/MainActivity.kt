package com.phantomcrowd.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcrowd.ui.tabs.MapDiscoveryTab
import com.phantomcrowd.ui.tabs.NavigationTab
import com.phantomcrowd.ui.theme.PhantomCrowdTheme
import com.phantomcrowd.data.SurfaceAnchor
import com.phantomcrowd.data.SurfaceAnchorManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    // Simple ViewModel instance
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create Notification Channel for Geofencing
        createNotificationChannel()
        
        setContent {
            PhantomCrowdTheme {
                MainScreen(viewModel)
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Phantom Crowd Geofencing"
            val descriptionText = "Notifications for nearby issues"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("phantom_crowd_geofence", name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Nearby", "Post", "Map", "Nav", "AR", "Impact")
    
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Collect state for tabs
    val currentLocation by viewModel.currentLocation.collectAsState()
    val nearbyAnchors by viewModel.anchors.collectAsState()
    val selectedAnchor by viewModel.selectedAnchor.collectAsState()
    val startLocationLat by viewModel.startLocationLat.collectAsState()
    val startLocationLon by viewModel.startLocationLon.collectAsState()
    
    // AR Navigation overlay state (Phase G)
    var showARNavigation by remember { mutableStateOf(false) }
    
    // Surface Anchor placement state (Phase I)
    var showSurfaceAnchorScreen by remember { mutableStateOf(false) }
    var surfaceAnchorMessageText by remember { mutableStateOf("") }
    var surfaceAnchorCategory by remember { mutableStateOf("General") }
    val scope = rememberCoroutineScope()
    
    val permissions = arrayOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )
    
    // Add POST_NOTIFICATIONS for Android 13+
    val allPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions + android.Manifest.permission.POST_NOTIFICATIONS
    } else {
        permissions
    }
    
    // Note: ACCESS_BACKGROUND_LOCATION usually needs to be requested *after* foreground location is granted
    // For simplicity in this flow, we'll request foreground first.
    // If we want background, we'd add it here if granted.
    // Let's stick to the prompt's simplicity for now, but adding POST_NOTIFICATIONS is critical.

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val allGranted = permissionsMap.values.all { it }
        if (allGranted) {
            android.util.Log.d(com.phantomcrowd.utils.Constants.TAG_PERMISSION, "All permissions granted")
            viewModel.updateLocation()
        } else {
            android.util.Log.e(com.phantomcrowd.utils.Constants.TAG_PERMISSION, "Permissions denied")
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(allPermissions)
    }
    
    // Trigger geofence setup when anchors change
    LaunchedEffect(nearbyAnchors) {
        if (nearbyAnchors.isNotEmpty()) {
            viewModel.setupGeofences()
        }
    }
    
    Scaffold(
        bottomBar = {
            // Hide bottom navigation when AR Navigation overlay OR Surface Anchor overlay is active
            if (!showARNavigation && !showSurfaceAnchorScreen) {
                NavigationBar {
                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            icon = { 
                                when(index) {
                                    0 -> Icon(Icons.Filled.List, contentDescription = null)
                                    1 -> Icon(Icons.Filled.Add, contentDescription = null)
                                    2 -> Icon(Icons.Filled.Place, contentDescription = null)
                                    3 -> Icon(Icons.Filled.LocationOn, contentDescription = null)
                                    4 -> Icon(Icons.Filled.Home, contentDescription = null)
                                    5 -> Icon(Icons.Filled.Info, contentDescription = null) // Impact tab
                                    else -> Icon(Icons.Filled.Home, contentDescription = null)
                                }
                            },
                            label = { Text(title) },
                            selected = selectedTab == index,
                            onClick = { 
                                // Close any open overlays when switching tabs
                                showSurfaceAnchorScreen = false
                                showARNavigation = false
                                selectedTab = index 
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Surface(modifier = Modifier.padding(paddingValues)) {
            when(selectedTab) {
                0 -> NearbyIssuesScreen(viewModel)
                1 -> PostCreationARScreen(
                    viewModel = viewModel,
                    onPostCreated = {
                        selectedTab = 2 // Switch to Map tab
                        viewModel.updateLocation() // Refresh issues
                    },
                    onCancel = { selectedTab = 0 }, // Go to Nearby tab
                    onOpenARPlacement = { messageText, category ->
                        // Store message and category
                        surfaceAnchorMessageText = messageText
                        surfaceAnchorCategory = category
                        
                        // Release CameraX before opening ARCore-based SurfaceAnchorScreen
                        scope.launch {
                            try {
                                val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
                                val cameraProvider = cameraProviderFuture.get()
                                cameraProvider.unbindAll()
                                com.phantomcrowd.utils.Logger.d(
                                    com.phantomcrowd.utils.Logger.Category.AR, 
                                    "CameraX released before ARCore session"
                                )
                            } catch (e: Exception) {
                                com.phantomcrowd.utils.Logger.e(
                                    com.phantomcrowd.utils.Logger.Category.AR,
                                    "Failed to release CameraX",
                                    e
                                )
                            }
                            // Small delay to ensure camera is fully released
                            kotlinx.coroutines.delay(200)
                            showSurfaceAnchorScreen = true
                        }
                    }
                )
                2 -> {
                    // Map Tab with Heatmap controls - using Box for proper z-ordering
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Map layer (bottom)
                        MapDiscoveryTab(
                            userLocation = currentLocation,
                            nearbyAnchors = nearbyAnchors,
                            showHeatmap = viewModel.showHeatmap.collectAsState().value,
                            onNavigateTo = { anchor ->
                                android.util.Log.d("MapDiscoveryTab", "Navigate to: ${anchor.messageText}")
                                viewModel.setSelectedAnchor(anchor)
                                // Switch to Navigation tab
                                selectedTab = 3
                            }
                        )
                        
                        // Heatmap toggle button (top layer with zIndex)
                        Button(
                            onClick = { viewModel.toggleHeatmap() },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                                .zIndex(10f), // Ensure button stays above map
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewModel.showHeatmap.collectAsState().value) 
                                    Color.Red else Color.Gray
                            )
                        ) {
                            Text(
                                if (viewModel.showHeatmap.collectAsState().value) "🔥 Heatmap ON" 
                                else "❄️ Heatmap OFF"
                            )
                        }
                        
                        // Legend overlay (top-right corner)
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 56.dp, end = 16.dp)
                                .zIndex(10f) // Ensure legend stays above map
                                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Heatmap Legend", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black)
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier
                                    .size(20.dp)
                                    .background(Color.Red)
                                )
                                Text("5+ issues", fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp), color = Color.Black)
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier
                                    .size(20.dp)
                                    .background(Color.Yellow)
                                )
                                Text("2-4 issues", fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp), color = Color.Black)
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier
                                    .size(20.dp)
                                    .background(Color.Green)
                                )
                                Text("1 issue", fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp), color = Color.Black)
                            }
                        }
                    }
                }
                3 -> NavigationTab(
                    userLocation = currentLocation,
                    deviceHeading = 0f, // Heading handled in AR screen
                    targetAnchor = selectedAnchor,
                    startLocationLat = startLocationLat,
                    startLocationLon = startLocationLon,
                    onOpenARNavigation = {
                        if (selectedAnchor != null) {
                            // Release CameraX before opening CameraX-based ARNavigationScreen
                            // This handles case where user is on AR tab (CameraX) and opens navigation
                            scope.launch {
                                try {
                                    val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
                                    val cameraProvider = cameraProviderFuture.get()
                                    cameraProvider.unbindAll()
                                    com.phantomcrowd.utils.Logger.d(
                                        com.phantomcrowd.utils.Logger.Category.AR,
                                        "CameraX released before ARNavigationScreen"
                                    )
                                } catch (e: Exception) {
                                    // Ignore - camera may not be in use
                                }
                                kotlinx.coroutines.delay(100)
                                showARNavigation = true
                            }
                        }
                    }
                )
                4 -> {
                    // Only show ARViewScreen if no overlays are active
                    // This prevents camera conflict between ARCore sessions
                    if (!showARNavigation && !showSurfaceAnchorScreen) {
                        ARViewScreen(viewModel)
                    } else {
                        // Show placeholder when camera is in use by overlay
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "📸 Camera in use by AR placement",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                5 -> {
                    // Impact Dashboard - Community statistics
                    ImpactDashboardScreen(viewModel)
                }
            }
        }
        
        // AR Navigation Overlay (Phase G - WOW MOMENT!)
        if (showARNavigation && selectedAnchor != null) {
            ARNavigationScreen(
                targetAnchor = selectedAnchor!!,
                userLocation = currentLocation,
                onClose = { showARNavigation = false }
            )
        }
        
        // Surface Anchor Placement Overlay (Phase I - Pokemon Go style!)
        if (showSurfaceAnchorScreen) {
            SurfaceAnchorScreen(
                messageText = surfaceAnchorMessageText,
                category = surfaceAnchorCategory,
                userLocation = currentLocation,
                onAnchorPlaced = { anchor ->
                    // Save anchor to Firestore
                    scope.launch {
                        try {
                            // Convert Triple to FloatArray properly
                            val offset = anchor.getOffset()
                            val offsetArray = floatArrayOf(offset.first, offset.second, offset.third)
                            
                            val normal = anchor.getSurfaceNormal()
                            val normalArray = floatArrayOf(normal.first, normal.second, normal.third)
                            
                            val result = SurfaceAnchorManager.saveAnchor(
                                messageText = anchor.messageText,
                                category = anchor.category,
                                location = android.location.Location("").apply {
                                    latitude = anchor.latitude
                                    longitude = anchor.longitude
                                },
                                anchorPose = com.google.ar.core.Pose(
                                    offsetArray,
                                    floatArrayOf(0f, 0f, 0f, 1f)
                                ),
                                planeType = com.google.ar.core.Plane.Type.valueOf(anchor.planeType),
                                surfaceNormal = normalArray
                            )
                            result.onSuccess {
                                android.widget.Toast.makeText(
                                    context, 
                                    "✅ Message placed on surface!", 
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                viewModel.updateLocation() // Refresh anchors
                            }
                            result.onFailure {
                                android.widget.Toast.makeText(
                                    context, 
                                    "❌ Failed to save: ${it.message}", 
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SurfaceAnchor", "Failed to save anchor", e)
                            android.widget.Toast.makeText(
                                context, 
                                "❌ Error: ${e.message}", 
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    showSurfaceAnchorScreen = false
                    surfaceAnchorMessageText = ""
                    selectedTab = 0 // Go to Nearby tab
                },
                onCancel = {
                    showSurfaceAnchorScreen = false
                }
            )
        }
        
        // Network indicator (Phase E) - Overlay over entire scaffold
        if (!viewModel.isOnline.collectAsState().value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp, end = 16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    "⚠️ OFFLINE",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
