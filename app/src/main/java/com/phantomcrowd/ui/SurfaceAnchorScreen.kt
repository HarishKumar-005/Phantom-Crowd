package com.phantomcrowd.ui

import android.location.Location
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ar.core.*
import com.phantomcrowd.data.SurfaceAnchor
import com.phantomcrowd.data.SurfaceAnchorManager
import com.phantomcrowd.utils.Logger
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.rememberARCameraStream
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SurfaceAnchorScreen - AR placement screen with plane detection.
 * 
 * Features:
 * - Yellow grid visualization on detected planes
 * - Tap button to place on center of detected plane
 * - Plane validation (size, stability)
 * - GPS + offset calculation for persistence
 */
@Composable
fun SurfaceAnchorScreen(
    messageText: String,
    category: String,
    userLocation: Location?,
    onAnchorPlaced: (SurfaceAnchor) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Handle system back button - close overlay properly
    androidx.activity.compose.BackHandler(enabled = true) {
        onCancel()
    }
    
    // AR state
    var arSessionReady by remember { mutableStateOf(false) }
    var planesDetected by remember { mutableIntStateOf(0) }
    var trackingState by remember { mutableStateOf<TrackingState?>(null) }
    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    
    // Placement state
    var bestPlane by remember { mutableStateOf<Plane?>(null) }
    var isPlacing by remember { mutableStateOf(false) }
    var shouldPlaceAnchor by remember { mutableStateOf(false) }
    var placementSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Plane stability tracking
    var planeStableTime by remember { mutableLongStateOf(0L) }
    val requiredStableTime = 1500L // 1.5 seconds
    
    // SceneView components
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val cameraStream = rememberARCameraStream(materialLoader)
    val view = rememberView(engine)
    val collisionSystem = rememberCollisionSystem(view)
    val childNodes = rememberNodes()
    
    // Current AR session reference
    var currentSession by remember { mutableStateOf<Session?>(null) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // AR Scene
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraStream = cameraStream,
            view = view,
            collisionSystem = collisionSystem,
            childNodes = childNodes,
            planeRenderer = true, // Shows yellow grid on planes!
            sessionConfiguration = { session, config ->
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                config.depthMode = when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    true -> Config.DepthMode.AUTOMATIC
                    else -> Config.DepthMode.DISABLED
                }
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
            },
            onSessionCreated = { session ->
                currentSession = session
                arSessionReady = true
                Logger.d(Logger.Category.AR, "AR Session created")
            },
            onSessionUpdated = { session, frame ->
                currentSession = session
                
                // Update tracking state
                val camera = frame.camera
                trackingState = camera.trackingState
                
                // Find detected planes
                val planes = session.getAllTrackables(Plane::class.java)
                    .filter { it.trackingState == TrackingState.TRACKING }
                planesDetected = planes.size
                
                // Track best plane (largest, most stable)
                if (planes.isNotEmpty()) {
                    bestPlane = planes.maxByOrNull { it.extentX * it.extentZ }
                    if (planeStableTime == 0L) {
                        planeStableTime = System.currentTimeMillis()
                    }
                } else {
                    bestPlane = null
                    planeStableTime = 0L
                }

                // Handle synchronized placement (runs safely in frame loop)
                if (shouldPlaceAnchor) {
                    shouldPlaceAnchor = false
                    
                    try {
                        // Perform hit test at screen center
                        val viewAsView = view as android.view.View
                        val hitResults = frame.hitTest(viewAsView.width.toFloat() / 2f, viewAsView.height.toFloat() / 2f)
                        val hit = hitResults.firstOrNull { 
                            val trackable = it.trackable
                            trackable is Plane && trackable.isPoseInPolygon(it.hitPose) 
                        }

                        if (hit != null && userLocation != null) {
                            val hitPose = hit.hitPose
                            val plane = hit.trackable as Plane

                            // Extract data safely
                            val zAxis = hitPose.zAxis
                            val surfaceNormal = floatArrayOf(zAxis[0], zAxis[1], zAxis[2])
                            
                            val translation = hitPose.translation
                            val offset = Triple(translation[0], translation[1], translation[2])
                            
                            // Safe non-null access for closure
                            val location = userLocation
                            
                            val anchor = SurfaceAnchor(
                                messageText = messageText,
                                category = category,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                geohash = com.phantomcrowd.utils.GeohashingUtility.encode(
                                    location.latitude, location.longitude
                                ),
                                relativeOffsetX = offset.first,
                                relativeOffsetY = offset.second,
                                relativeOffsetZ = offset.third,
                                planeType = plane.type.name,
                                surfaceNormalX = surfaceNormal[0],
                                surfaceNormalY = surfaceNormal[1],
                                surfaceNormalZ = surfaceNormal[2],
                                timestamp = System.currentTimeMillis()
                            )
                            
                            placementSuccess = true
                            scope.launch {
                                delay(800)
                                onAnchorPlaced(anchor)
                            }
                        } else {
                            // If hit test fails, fallback to bestPlane centerPose (defensive)
                            val plane = bestPlane
                            if (plane != null && plane.trackingState == TrackingState.TRACKING && userLocation != null) {
                                val hitPose = plane.centerPose
                                val offset = SurfaceAnchorManager.calculateOffset(hitPose)
                                val location = userLocation
                                
                                val anchor = SurfaceAnchor(
                                    messageText = messageText,
                                    category = category,
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    geohash = com.phantomcrowd.utils.GeohashingUtility.encode(
                                        location.latitude, location.longitude
                                    ),
                                    relativeOffsetX = offset.first,
                                    relativeOffsetY = offset.second,
                                    relativeOffsetZ = offset.third,
                                    planeType = plane.type.name,
                                    surfaceNormalX = hitPose.zAxis[0],
                                    surfaceNormalY = hitPose.zAxis[1],
                                    surfaceNormalZ = hitPose.zAxis[2],
                                    timestamp = System.currentTimeMillis()
                                )
                                placementSuccess = true
                                scope.launch {
                                    delay(800)
                                    onAnchorPlaced(anchor)
                                }
                            } else {
                                errorMessage = "Could not place here - try moving closer"
                                isPlacing = false
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e(Logger.Category.AR, "Placement error in frame loop", e)
                        errorMessage = "Error: ${e.message}"
                        isPlacing = false
                    }
                }
            },
            onTrackingFailureChanged = { reason ->
                trackingFailureReason = reason
            }
        )
        
        // Top status bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            // Back button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel button
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.ArrowBack, "Cancel", tint = Color.White)
                }
                
                // Status chip
                Surface(
                    color = when {
                        placementSuccess -> Color(0xFF4CAF50)
                        planesDetected > 0 -> Color(0xFF4CAF50)
                        arSessionReady -> Color(0xFFFF9800)
                        else -> Color(0xFFFF5252)
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = when {
                            placementSuccess -> "✅ Placed!"
                            planesDetected > 0 -> "🟡 ${planesDetected} surface${if (planesDetected > 1) "s" else ""}"
                            arSessionReady -> "👀 Looking..."
                            else -> "⏳ Starting AR"
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tracking failure warning
            trackingFailureReason?.let { reason ->
                if (reason != TrackingFailureReason.NONE) {
                    Text(
                        text = getTrackingFailureMessageSurface(reason),
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            // Error message
            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ $error",
                    color = Color(0xFFFF5252),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        
        // Center instructions
        if (!placementSuccess && planesDetected == 0 && arSessionReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 150.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        .padding(24.dp)
                ) {
                    Text("📱", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Point at a wall or floor",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Move slowly to detect surfaces",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }
        }
        
        // Plane detected instructions
        if (!placementSuccess && planesDetected > 0 && !isPlacing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 150.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        .padding(24.dp)
                ) {
                    Text("✨", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Surface detected!",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap PLACE below to anchor your message",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        // Placing indicator
        if (isPlacing && !placementSuccess) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Placing message...",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        }
        
        // Success overlay
        if (placementSuccess) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF4CAF50).copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color(0xFF4CAF50), RoundedCornerShape(16.dp))
                        .padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Message Placed!",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // Bottom panel with PLACE button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📝 \"$messageText\"",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Category: $category",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            
            if (userLocation != null) {
                Text(
                    text = "${String.format("%.5f", userLocation.latitude)}, ${String.format("%.5f", userLocation.longitude)}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // PLACE button
            Button(
                onClick = {
                    if (!isPlacing && userLocation != null) {
                        isPlacing = true
                        errorMessage = null
                        shouldPlaceAnchor = true // Trigger placement in next frame
                    }
                },
                enabled = planesDetected > 0 && !isPlacing && !placementSuccess && userLocation != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    disabledContainerColor = Color.Gray
                )
            ) {
                Text(
                    text = when {
                        placementSuccess -> "✅ PLACED!"
                        isPlacing -> "⏳ PLACING..."
                        planesDetected == 0 -> "🔍 SEARCHING..."
                        userLocation == null -> "📍 WAITING FOR GPS..."
                        else -> "🧱 PLACE ON SURFACE"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Get tracking failure message.
 */
private fun getTrackingFailureMessageSurface(reason: TrackingFailureReason): String {
    return when (reason) {
        TrackingFailureReason.NONE -> ""
        TrackingFailureReason.BAD_STATE -> "⚠️ AR error - try restarting"
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "💡 Too dark - need more light"
        TrackingFailureReason.EXCESSIVE_MOTION -> "📱 Moving too fast - hold steady"
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "🔍 Point at textured surface"
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "📷 Camera unavailable"
    }
}
