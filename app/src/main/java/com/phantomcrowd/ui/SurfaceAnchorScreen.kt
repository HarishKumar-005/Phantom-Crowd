package com.phantomcrowd.ui

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler

import com.google.ar.core.*
import com.phantomcrowd.data.SurfaceAnchor
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
import kotlinx.coroutines.sync.Mutex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

/**
 * FIXED SurfaceAnchorScreen - AR placement with plane detection
 * 
 * KEY FIXES for SceneView 2.0.3:
 * 1. Use proper ARScene API (manual frame updates via LaunchedEffect)
 * 2. Implement custom frame callback via arCore session reference
 * 3. Add proper null safety throughout
 * 4. Fix race conditions with Mutex
 * 5. Use DisplayMetrics for correct screen coordinates
 * 6. Handle edge cases (small views, null locations)
 * 
 * Author: Phantom Crowd Team
 * Version: 2.0 (Fixed for production)
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
    val configuration = LocalConfiguration.current

    BackHandler(enabled = true) {
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
    var placementSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Plane stability tracking
    var planeStableTime by remember { mutableLongStateOf(0L) }
    val requiredStableTime = 1500L
    
    // Thread-safe placement flag
    val placementMutex = remember { Mutex() }
    var shouldPlaceAnchor by remember { mutableStateOf(false) }

    // AR session reference for manual frame updates
    var arCoreSession by remember { mutableStateOf<Session?>(null) }
    var lastFrameTime by remember { mutableLongStateOf(0L) }

    // SceneView components
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val cameraStream = rememberARCameraStream(materialLoader)
    val view = rememberView(engine)
    val collisionSystem = rememberCollisionSystem(view)
    val childNodes = rememberNodes()

    // CRITICAL: Explicitly clean up ARCore session when composable leaves
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            Logger.d(Logger.Category.AR, "SurfaceAnchorScreen disposing - letting SceneView handle cleanup")
            arSessionReady = false // Stop the frame loop
            // NOTE: Do NOT call arCoreSession?.pause() here - SceneView manages the session lifecycle
            // and calling pause() on an already-paused session causes FatalException
        }
    }

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
            planeRenderer = true,
            sessionConfiguration = { session, config ->
                try {
                    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    config.depthMode = when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                        true -> Config.DepthMode.AUTOMATIC
                        else -> Config.DepthMode.DISABLED
                    }
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                    Logger.d(Logger.Category.AR, "AR config applied successfully")
                } catch (e: Exception) {
                    Logger.e(Logger.Category.AR, "Error configuring AR session", e)
                    errorMessage = "AR config error: ${e.message}"
                }
            },
            onSessionCreated = { session ->
                try {
                    arCoreSession = session
                    arSessionReady = true
                    Logger.d(Logger.Category.AR, "AR Session created successfully")
                } catch (e: Exception) {
                    Logger.e(Logger.Category.AR, "Session creation failed", e)
                    errorMessage = "Session error: ${e.message}"
                }
            },
            onTrackingFailureChanged = { reason ->
                trackingFailureReason = reason
            }
        )

        // Manual frame update loop (KEY FIX for SceneView 2.0.3)
        val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        LaunchedEffect(arSessionReady, lifecycleOwner) {
            if (arSessionReady && arCoreSession != null) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    while (arSessionReady) {
                        try {
                            val session = arCoreSession ?: break
                            
                            // Get current frame from ARCore session
                            val frame = try {
                                session.update()
                            } catch (e: Exception) {
                                Logger.w(Logger.Category.AR, "Frame update failed: ${e.message}")
                                delay(100)
                                continue
                            }
                            
                            lastFrameTime = System.currentTimeMillis()
                            
                            // Update tracking state
                            val camera = frame.camera
                            trackingState = camera.trackingState
                            
                            // Find detected planes (filter small planes)
                            val planes = session.getAllTrackables(Plane::class.java)
                                .filter { it.trackingState == TrackingState.TRACKING }
                                .filter { it.extentX > 0.1f && it.extentZ > 0.1f }
                            
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
                            
                            // Handle placement request (thread-safe with Mutex)
                            if (shouldPlaceAnchor && !isPlacing) {
                                isPlacing = true
                                placementMutex.lock()
                                try {
                                    val planeToUse = bestPlane
                                    val location = userLocation
                                    
                                    if (planeToUse != null && location != null) {
                                        // Get screen center using DisplayMetrics (CORRECT approach)
                                        val displayMetrics = context.resources.displayMetrics
                                        val screenCenterX = displayMetrics.widthPixels / 2f
                                        val screenCenterY = displayMetrics.heightPixels / 2f
                                        
                                        // Perform hit test at screen center
                                        val hitResults = frame.hitTest(screenCenterX, screenCenterY)
                                        val hit = hitResults.firstOrNull { result ->
                                            val trackable = result.trackable
                                            // Type-safe check (no unsafe cast)
                                            if (trackable is Plane) {
                                                trackable.trackingState == TrackingState.TRACKING &&
                                                trackable.isPoseInPolygon(result.hitPose)
                                            } else {
                                                false
                                            }
                                        }
                                        
                                        if (hit != null) {
                                            // Successful hit test - use hit pose
                                            performPlacement(
                                                hit = hit,
                                                location = location,
                                                messageText = messageText,
                                                category = category,
                                                onSuccess = { anchor ->
                                                    placementSuccess = true
                                                    scope.launch {
                                                        delay(800)
                                                        onAnchorPlaced(anchor)
                                                    }
                                                },
                                                onError = { error ->
                                                    errorMessage = error
                                                    Logger.e(Logger.Category.AR, "Placement failed: $error")
                                                }
                                            )
                                        } else {
                                            // Fallback to plane center if hit test fails
                                            usePlaneCenterPlacement(
                                                plane = planeToUse,
                                                location = location,
                                                messageText = messageText,
                                                category = category,
                                                onSuccess = { anchor ->
                                                    placementSuccess = true
                                                    scope.launch {
                                                        delay(800)
                                                        onAnchorPlaced(anchor)
                                                    }
                                                },
                                                onError = { error ->
                                                    errorMessage = error
                                                }
                                            )
                                        }
                                    } else {
                                        // Clear error messages for each case
                                        errorMessage = when {
                                            location == null -> "📍 Location not available - please enable GPS"
                                            planeToUse == null -> "👀 No surface detected - point camera at a flat surface"
                                            else -> "Unknown error - please try again"
                                        }
                                    }
                                } finally {
                                    shouldPlaceAnchor = false
                                    isPlacing = false
                                    placementMutex.unlock()
                                }
                            }
                            
                            // Frame rate limiter (~60fps)
                            delay(16)
                        } catch (e: Exception) {
                            Logger.e(Logger.Category.AR, "Frame update error", e)
                            delay(100) // Wait before retrying
                        }
                    }
                }
            }
        }

        // Top status bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.ArrowBack, "Cancel", tint = Color.White)
                }

                // Status indicator
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
                            planesDetected > 0 -> "🟡 ${planesDetected} surface(s)"
                            arSessionReady -> "🔍 Searching..."
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

            // Tracking failure message
            trackingFailureReason?.let { reason ->
                if (reason != TrackingFailureReason.NONE) {
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

            // Error message display
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

        // Center instruction overlay
        if (planesDetected > 0 && !placementSuccess) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "✨",
                    fontSize = 48.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Surface detected!",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Text(
                    text = "Tap PLACE below to anchor your message",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Bottom panel with controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Message preview
            Surface(
                color = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📍",
                        fontSize = 24.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = "\"$messageText\"",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Category: $category",
                            color = Color(0xFFBDBDBD),
                            fontSize = 11.sp
                        )
                        userLocation?.let { loc ->
                            Text(
                                text = "%.5f, %.5f".format(loc.latitude, loc.longitude),
                                color = Color(0xFF9E9E9E),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Plane stability indicator
            if (planesDetected > 0 && bestPlane != null) {
                val stableTime = System.currentTimeMillis() - planeStableTime
                val isStable = stableTime >= requiredStableTime
                val stabilityPercent = (stableTime * 100 / requiredStableTime).toInt().coerceAtMost(100)
                
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = stabilityPercent / 100f,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(4.dp),
                    color = if (isStable) Color(0xFF4CAF50) else Color(0xFFFF9800)
                )
                Text(
                    text = if (isStable) "✅ Ready to place" else "Hold steady...",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // PLACE button
            Button(
                onClick = {
                    if (!isPlacing && planesDetected > 0) {
                        errorMessage = null
                        shouldPlaceAnchor = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(48.dp),
                enabled = planesDetected > 0 && !isPlacing && !placementSuccess,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    disabledContainerColor = Color(0xFFBDBDBD)
                )
            ) {
                if (isPlacing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (isPlacing) "Placing..." else "🌐 PLACE ON SURFACE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Point at a wall or floor. Tap when grid appears.",
                color = Color(0xFFBDBDBD),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Perform placement using hit test result
 */
private fun performPlacement(
    hit: HitResult,
    location: Location,
    messageText: String,
    category: String,
    onSuccess: (SurfaceAnchor) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val hitPose = hit.hitPose
        val trackable = hit.trackable
        
        // Type-safe check
        if (trackable !is Plane) {
            onError("Hit target is not a plane")
            return
        }
        
        if (trackable.trackingState != TrackingState.TRACKING) {
            onError("Plane not tracking")
            return
        }

        val translation = hitPose.translation
        val zAxis = hitPose.zAxis
        
        // Validate array sizes
        if (translation.size < 3 || zAxis.size < 3) {
            onError("Invalid pose data")
            return
        }

        val anchor = SurfaceAnchor(
            messageText = messageText,
            category = category,
            latitude = location.latitude,
            longitude = location.longitude,
            geohash = com.phantomcrowd.utils.GeohashingUtility.encode(
                location.latitude, location.longitude
            ),
            relativeOffsetX = translation[0],
            relativeOffsetY = translation[1],
            relativeOffsetZ = translation[2],
            planeType = trackable.type.name,
            surfaceNormalX = zAxis[0],
            surfaceNormalY = zAxis[1],
            surfaceNormalZ = zAxis[2],
            timestamp = System.currentTimeMillis()
        )
        
        Logger.i(Logger.Category.AR, "Anchor created at (${translation[0]}, ${translation[1]}, ${translation[2]})")
        onSuccess(anchor)
    } catch (e: Exception) {
        Logger.e(Logger.Category.AR, "Placement error", e)
        onError("Placement failed: ${e.message}")
    }
}

/**
 * Fallback placement using plane center pose (when hit test fails)
 */
private fun usePlaneCenterPlacement(
    plane: Plane,
    location: Location,
    messageText: String,
    category: String,
    onSuccess: (SurfaceAnchor) -> Unit,
    onError: (String) -> Unit
) {
    try {
        // Verify plane is still tracking
        if (plane.trackingState != TrackingState.TRACKING) {
            onError("Plane lost tracking")
            return
        }

        val centerPose = plane.centerPose
        val translation = centerPose.translation
        val zAxis = centerPose.zAxis
        
        val anchor = SurfaceAnchor(
            messageText = messageText,
            category = category,
            latitude = location.latitude,
            longitude = location.longitude,
            geohash = com.phantomcrowd.utils.GeohashingUtility.encode(
                location.latitude, location.longitude
            ),
            relativeOffsetX = translation[0],
            relativeOffsetY = translation[1],
            relativeOffsetZ = translation[2],
            planeType = plane.type.name,
            surfaceNormalX = zAxis[0],
            surfaceNormalY = zAxis[1],
            surfaceNormalZ = zAxis[2],
            timestamp = System.currentTimeMillis()
        )
        
        Logger.i(Logger.Category.AR, "Fallback anchor at plane center: (${translation[0]}, ${translation[1]}, ${translation[2]})")
        onSuccess(anchor)
    } catch (e: Exception) {
        Logger.e(Logger.Category.AR, "Fallback placement error", e)
        onError("Center placement failed: ${e.message}")
    }
}

/**
 * Get user-friendly tracking failure message
 */
private fun getTrackingFailureMessage(reason: TrackingFailureReason): String {
    return when (reason) {
        TrackingFailureReason.NONE -> "Tracking OK"
        TrackingFailureReason.BAD_STATE -> "⏳ Waiting for AR to initialize..."
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "💡 Need more light"
        TrackingFailureReason.EXCESSIVE_MOTION -> "📱 Move more slowly"
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "❌ Point at a textured surface"
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "📷 Camera unavailable"
        else -> "Tracking issue: $reason"
    }
}
