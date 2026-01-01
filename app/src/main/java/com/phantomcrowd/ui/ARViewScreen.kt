package com.phantomcrowd.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.phantomcrowd.data.AnchorData
import com.phantomcrowd.utils.BearingCalculator
import com.phantomcrowd.utils.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * ARViewScreen - Simplified AR View with CameraX and Overlay Labels
 * 
 * Features:
 * - CameraX camera preview (no ARCore conflicts!)
 * - Floating labels for nearby issues based on GPS bearing
 * - Real-time compass heading for label positioning
 * - Distance-based label sizing
 * - OPTIMIZED: Throttled sensor updates to prevent performance issues
 */
@Composable
fun ARViewScreen(
    viewModel: MainViewModel,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    
    // Collect state
    val anchors by viewModel.anchors.collectAsState()
    val userLocation by viewModel.currentLocation.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Compass heading - THROTTLED to prevent excessive recompositions
    var deviceHeading by remember { mutableFloatStateOf(0f) }
    var lastUpdateTime by remember { mutableLongStateOf(0L) }
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    
    // Camera executor
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var cameraReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    
    // Pre-calculate anchor bearings and distances (only when location or anchors change)
    val anchorData = remember(userLocation, anchors) {
        if (userLocation == null) emptyList()
        else anchors.map { anchor ->
            val bearing = BearingCalculator.calculateBearing(
                userLocation!!.latitude, userLocation!!.longitude,
                anchor.latitude, anchor.longitude
            ).toFloat()
            
            val distance = FloatArray(1)
            Location.distanceBetween(
                userLocation!!.latitude, userLocation!!.longitude,
                anchor.latitude, anchor.longitude,
                distance
            )
            
            AnchorDisplayData(anchor, bearing, distance[0])
        }.sortedBy { it.distance }
    }
    
    // Compass sensor listener - THROTTLED
    DisposableEffect(sensorManager) {
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        var lastHeading = 0f
        
        val sensorListener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientationAngles = FloatArray(3)
            
            override fun onSensorChanged(event: SensorEvent) {
                val currentTime = System.currentTimeMillis()
                
                // Throttle to max 10 updates per second (100ms)
                if (currentTime - lastUpdateTime < 100) return
                
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                
                // Convert to degrees (0-360)
                val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                val newHeading = (azimuth + 360) % 360
                
                // Only update if heading changed by more than 3 degrees
                val headingDiff = abs(newHeading - lastHeading)
                if (headingDiff > 3 || headingDiff > 357) { // Handle wrap-around
                    deviceHeading = newHeading
                    lastHeading = newHeading
                    lastUpdateTime = currentTime
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        if (rotationSensor != null) {
            sensorManager.registerListener(
                sensorListener,
                rotationSensor,
                SensorManager.SENSOR_DELAY_GAME // Faster updates, but we throttle manually
            )
        }
        
        onDispose {
            sensorManager.unregisterListener(sensorListener)
            cameraExecutor.shutdown()
            // CRITICAL: Explicitly unbind CameraX to release camera for ARCore
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProvider.unbindAll()
                        Logger.d(Logger.Category.AR, "CameraX fully unbound - camera released for ARCore")
                    } catch (e: Exception) {
                        Logger.e(Logger.Category.AR, "Failed to unbind CameraX", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (e: Exception) {
                Logger.e(Logger.Category.AR, "Failed to get CameraProvider for cleanup", e)
            }
            Logger.d(Logger.Category.AR, "ARViewScreen disposed - camera released")
        }
    }
    
    // Refresh anchors on start
    LaunchedEffect(Unit) {
        viewModel.updateLocation()
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    startCamera(ctx, this, lifecycleOwner, cameraExecutor, 
                        onReady = { cameraReady = true },
                        onError = { error -> cameraError = error }
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Issue Labels Overlay - Uses pre-calculated data
        if (anchorData.isNotEmpty()) {
            val visibleAnchors = remember(deviceHeading, anchorData) {
                anchorData.mapNotNull { data ->
                    // Calculate angle difference from current heading
                    var angleDiff = data.bearing - deviceHeading
                    if (angleDiff > 180) angleDiff -= 360
                    if (angleDiff < -180) angleDiff += 360
                    
                    // Only show issues within ±60° field of view
                    if (abs(angleDiff) <= 60) {
                        VisibleAnchor(data.anchor, angleDiff, data.distance)
                    } else null
                }
            }
            
            // Draw labels - max 5
            visibleAnchors.take(5).forEachIndexed { index, visible ->
                // Position label based on angle (center = 0, left = -60, right = +60)
                val xOffset = visible.angleDiff / 60f // -1 to +1
                
                // Size based on distance (closer = bigger)
                val scale = when {
                    visible.distance < 20 -> 1.3f
                    visible.distance < 50 -> 1.1f
                    visible.distance < 100 -> 1.0f
                    visible.distance < 200 -> 0.9f
                    else -> 0.8f
                }
                
                // Color based on category
                val labelColor = when (visible.anchor.category.lowercase()) {
                    "safety" -> Color(0xFFE53935)  // Red
                    "facility" -> Color(0xFF1E88E5)  // Blue
                    else -> Color(0xFF43A047)  // Green
                }
                
                // Vertical position based on index (stack labels)
                val yPosition = 0.3f + (index * 0.12f)
                
                IssueLabel(
                    anchor = visible.anchor,
                    distance = visible.distance,
                    xOffset = xOffset,
                    yPosition = yPosition,
                    scale = scale,
                    color = labelColor,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
        
        // Top status bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            // Status chip
            Surface(
                color = if (cameraReady) Color(0xFF4CAF50) else Color(0xFFFF9800),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (cameraReady) "🎯 AR Active" else "⏳ Initializing",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            
            // Camera error
            cameraError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "📷 $error",
                    color = Color(0xFFFF5252),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            // Compass heading
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "🧭 ${deviceHeading.toInt()}° ${getCardinalDirection(deviceHeading)}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        
        // Bottom info panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
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
            
            Text(
                text = "👆 Point camera towards issues to see labels",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            FilledTonalButton(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    viewModel.updateLocation()
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Refresh")
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
    }
}

// Data classes for caching calculations
private data class AnchorDisplayData(
    val anchor: AnchorData,
    val bearing: Float,
    val distance: Float
)

private data class VisibleAnchor(
    val anchor: AnchorData,
    val angleDiff: Float,
    val distance: Float
)

/**
 * Floating issue label composable
 */
@Composable
private fun IssueLabel(
    anchor: AnchorData,
    distance: Float,
    xOffset: Float,
    yPosition: Float,
    scale: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = (xOffset * 150).dp, y = (yPosition * 1000).dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale
                )
                .background(color.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Category icon
            Text(
                text = when (anchor.category.lowercase()) {
                    "safety" -> "⚠️"
                    "facility" -> "🏢"
                    else -> "📍"
                },
                fontSize = 16.sp
            )
            
            // Issue text
            Text(
                text = anchor.messageText,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 120.dp)
            )
            
            // Distance
            Text(
                text = if (distance < 1000) "${distance.toInt()}m" else "${String.format("%.1f", distance/1000)}km",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Start CameraX preview
 */
private fun startCamera(
    context: Context,
    previewView: PreviewView,
    lifecycleOwner: LifecycleOwner,
    executor: ExecutorService,
    onReady: () -> Unit,
    onError: (String) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    
    cameraProviderFuture.addListener({
        try {
            val cameraProvider = cameraProviderFuture.get()
            
            // Unbind all before rebinding
            cameraProvider.unbindAll()
            
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview
            )
            
            onReady()
            Logger.d(Logger.Category.AR, "CameraX started successfully")
            
        } catch (e: Exception) {
            Logger.e(Logger.Category.AR, "CameraX init failed", e)
            onError(e.message ?: "Camera init failed")
        }
    }, ContextCompat.getMainExecutor(context))
}

/**
 * Get cardinal direction from heading
 */
private fun getCardinalDirection(heading: Float): String {
    return when {
        heading < 22.5 || heading >= 337.5 -> "N"
        heading < 67.5 -> "NE"
        heading < 112.5 -> "E"
        heading < 157.5 -> "SE"
        heading < 202.5 -> "S"
        heading < 247.5 -> "SW"
        heading < 292.5 -> "W"
        else -> "NW"
    }
}
