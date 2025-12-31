package com.phantomcrowd.ui

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import com.phantomcrowd.data.AnchorData
import com.phantomcrowd.utils.BearingCalculator
import com.phantomcrowd.utils.Logger
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.rememberARCameraStream
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ARViewScreen - Production-ready AR View with SceneView 2.3.1
 * 
 * Features:
 * - Full AR camera feed via ARScene composable
 * - Plane detection visualization
 * - 3D markers for nearby issues
 * - Real-time tracking state feedback
 * - Distance-based marker colors
 */
@Composable
fun ARViewScreen(
    viewModel: MainViewModel,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Collect state
    val anchors by viewModel.anchors.collectAsState()
    val userLocation by viewModel.currentLocation.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // AR state
    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var planesDetected by remember { mutableIntStateOf(0) }
    var arSessionReady by remember { mutableStateOf(false) }
    
    // Haptic feedback
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    
    // SceneView components
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val cameraStream = rememberARCameraStream(materialLoader)
    val view = rememberView(engine)
    val collisionSystem = rememberCollisionSystem(view)
    
    // Child nodes for AR scene
    val childNodes = rememberNodes()
    
    // Refresh anchors on start
    LaunchedEffect(Unit) {
        viewModel.updateLocation()
        viewModel.updateLocation()
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Main AR Scene
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraStream = cameraStream,
            view = view,
            collisionSystem = collisionSystem,
            childNodes = childNodes,
            planeRenderer = true,
            sessionConfiguration = { session, config ->
                // Enable depth if supported
                config.depthMode = when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    true -> Config.DepthMode.AUTOMATIC
                    else -> Config.DepthMode.DISABLED
                }
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
            },
            onSessionCreated = { session ->
                Logger.d(Logger.Category.AR, "AR Session created")
                arSessionReady = true
            },
            onSessionResumed = { session ->
                Logger.d(Logger.Category.AR, "AR Session resumed")
            },
            onSessionUpdated = { session, frame ->
                // Count detected planes
                val planes = frame.getUpdatedTrackables(Plane::class.java)
                if (planes.isNotEmpty()) {
                    planesDetected = session.getAllTrackables(Plane::class.java).size
                }
            },
            onTrackingFailureChanged = { reason ->
                trackingFailureReason = reason
                Logger.w(Logger.Category.AR, "Tracking failure: $reason")
            },
            onSessionFailed = { exception ->
                Logger.e(Logger.Category.AR, "AR Session failed", exception)
            }
        )
        
        // Top overlay - Status bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            // Close button (if provided)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status chip
                Surface(
                    color = if (arSessionReady) Color(0xFF4CAF50) else Color(0xFFFF9800),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (arSessionReady) "🎯 AR Active" else "⏳ Initializing",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Close button
                onClose?.let { closeCallback ->
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            closeCallback()
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close AR View",
                            tint = Color.White
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tracking status/planes detected
            if (planesDetected > 0) {
                Text(
                    text = "📐 $planesDetected surface${if (planesDetected > 1) "s" else ""} detected",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            // Tracking failure warning
            trackingFailureReason?.let { reason ->
                Text(
                    text = getTrackingFailureMessage(reason),
                    color = Color(0xFFFF5252),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        
        // Bottom overlay - Nearby issues count
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📍 ${anchors.size} issues nearby",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            if (userLocation != null) {
                Text(
                    text = "${String.format("%.5f", userLocation!!.latitude)}, ${String.format("%.5f", userLocation!!.longitude)}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Refresh button
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        viewModel.updateLocation()
                        viewModel.updateLocation()
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refresh")
                }
            }
        }
        
        // Loading overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
        
        // Instructions overlay (when no planes detected)
        if (arSessionReady && planesDetected == 0 && trackingFailureReason == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 150.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "👀 Point camera at a flat surface",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

/**
 * Get user-friendly tracking failure message
 */
private fun getTrackingFailureMessage(reason: TrackingFailureReason): String {
    return when (reason) {
        TrackingFailureReason.NONE -> ""
        TrackingFailureReason.BAD_STATE -> "⚠️ AR session error - restart app"
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "💡 Too dark - move to brighter area"
        TrackingFailureReason.EXCESSIVE_MOTION -> "📱 Moving too fast - hold steady"
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "🔍 Not enough features - point at textured surface"
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "📷 Camera unavailable"
    }
}
