package com.phantomcrowd.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.phantomcrowd.ar.VoiceGuidanceManager
import com.phantomcrowd.data.AnchorData
import com.phantomcrowd.utils.BearingCalculator
import com.phantomcrowd.utils.Logger
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * ARNavigationScreen - The WOW MOMENT feature!
 * 
 * Displays camera feed with floating arrow pointing to destination.
 * Features:
 * - Camera preview via CameraX (stable, reliable)
 * - Large directional arrow overlay with pulse animation
 * - Real-time distance display
 * - Voice guidance
 * - Color-coded distance indicator
 * - Haptic feedback on arrival
 * - Robust error handling with snackbar
 */
@Composable
fun ARNavigationScreen(
    targetAnchor: AnchorData,
    userLocation: Location?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    
    // Error handling state
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage by viewModel?.errorMessage?.collectAsState() ?: remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    // Show errors via snackbar
    LaunchedEffect(errorMessage, cameraError) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel?.clearError()
        }
        cameraError?.let {
            snackbarHostState.showSnackbar("Camera Error: $it", duration = SnackbarDuration.Long)
            cameraError = null
        }
    }
    
    // Voice guidance
    val voiceManager = remember { VoiceGuidanceManager(context) }
    
    // Sensor for device heading (compass)
    var deviceHeading by remember { mutableFloatStateOf(0f) }
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    
    // Smooth rotation state
    var displayRotation by remember { mutableFloatStateOf(0f) }
    
    // Navigation state
    var hasSpokenStart by remember { mutableStateOf(false) }
    var hasArrived by remember { mutableStateOf(false) }
    
    // Camera executor
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // Initialize voice on first composition
    LaunchedEffect(Unit) {
        voiceManager.initialize {
            Logger.d(Logger.Category.AR, "Voice guidance ready")
        }
    }
    
    // Compass sensor listener
    DisposableEffect(sensorManager) {
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        
        val sensorListener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientationAngles = FloatArray(3)
            
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                
                // Convert to degrees (0-360)
                val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                deviceHeading = (azimuth + 360) % 360
            }
            
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        if (rotationSensor != null) {
            sensorManager.registerListener(
                sensorListener,
                rotationSensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        
        onDispose {
            sensorManager.unregisterListener(sensorListener)
            cameraExecutor.shutdown()
            voiceManager.shutdown()
        }
    }

    // Calculate navigation data
    val distance = if (userLocation != null) {
        val results = FloatArray(1)
        Location.distanceBetween(
            userLocation.latitude, userLocation.longitude,
            targetAnchor.latitude, targetAnchor.longitude,
            results
        )
        results[0]
    } else 0f
    
    val targetBearing = if (userLocation != null) {
        BearingCalculator.calculateBearing(
            userLocation.latitude, userLocation.longitude,
            targetAnchor.latitude, targetAnchor.longitude
        ).toFloat()
    } else 0f
    
    // Arrow rotation = target bearing - device heading
    val targetRotation = (targetBearing - deviceHeading + 360) % 360
    
    // Smooth rotation with lerp
    LaunchedEffect(targetRotation) {
        while (true) {
            displayRotation = lerpAngle(displayRotation, targetRotation, 0.15f)
            delay(16) // ~60 FPS
        }
    }

    // Pulse animation for close proximity
    val pulseTransition = rememberInfiniteTransition(label = "Pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (distance <= 50f) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    
    // Voice guidance logic
    LaunchedEffect(distance, hasSpokenStart) {
        if (userLocation != null && distance > 0 && !hasSpokenStart) {
            voiceManager.speakNavigationStart(distance.toInt())
            hasSpokenStart = true
        }
    }
    
    // Check milestones and arrival
    LaunchedEffect(distance) {
        if (userLocation != null && hasSpokenStart) {
            voiceManager.checkAndSpeakMilestone(distance.toInt())
            
            if (distance <= 20f && !hasArrived) {
                hasArrived = true
                voiceManager.speakArrival()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }
    
    // Color based on distance
    val arrowColor = when {
        distance <= 20f -> Color(0xFF4CAF50)  // Green - Arrived!
        distance <= 50f -> Color(0xFF8BC34A)  // Light Green - Very close
        distance <= 100f -> Color(0xFFFFEB3B) // Yellow - Close
        else -> Color(0xFFFF5252)              // Red - Far
    }
    
    val distanceText = when {
        distance >= 1000 -> "${String.format("%.1f", distance / 1000)}km"
        else -> "${distance.toInt()}m"
    }
    
    val directionText = BearingCalculator.bearingToCardinal(targetBearing.toDouble())
    
    Box(modifier = modifier.fillMaxSize()) {
        // Camera Background using CameraX
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    
                    // Start camera with error callback
                    startCamera(ctx, this, lifecycleOwner, cameraExecutor) { error ->
                        Logger.e(Logger.Category.AR, "Camera error: $error")
                        cameraError = error
                    }
                }
            }
        )
        
        // Gradient overlay for better text visibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f)
                        )
                    )
                )
        )
        
        // Close button (top right) with haptic feedback
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                voiceManager.stop()
                onClose()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close AR Navigation",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        
        // Target info (top center)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📍 ${targetAnchor.category.uppercase()}",
                fontSize = 12.sp,
                color = arrowColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = targetAnchor.messageText.take(40) + if (targetAnchor.messageText.length > 40) "..." else "",
                fontSize = 14.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
        
        // MAIN ARROW (Center) with Pulse Animation
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            // Glow effect background
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(arrowColor.copy(alpha = 0.2f))
            )
            
            // Arrow icon with pulse when close
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Direction Arrow",
                modifier = Modifier
                    .size(160.dp)
                    .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                    .rotate(displayRotation),
                tint = arrowColor
            )
        }
        
        // Distance display (bottom center)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = distanceText,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = arrowColor
            )
            Text(
                text = directionText,
                fontSize = 24.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
            
            // Arrival indicator
            if (hasArrived) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "🎉 YOU'VE ARRIVED! 🎉",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
        }
        
        // GPS status (if no location)
        if (userLocation == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Waiting for GPS...",
                        color = Color.White,
                        fontSize = 18.sp
                    )

                    // Retry button for GPS errors
                    if (errorMessage?.contains("GPS", ignoreCase = true) == true ||
                        errorMessage?.contains("location", ignoreCase = true) == true) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel?.updateLocation() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }

        // Snackbar for error messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

/**
 * Start CameraX preview with error callback
 */
private fun startCamera(
    context: Context,
    previewView: PreviewView,
    lifecycleOwner: LifecycleOwner,
    executor: ExecutorService,
    onError: (String) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    
    cameraProviderFuture.addListener({
        try {
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
            
            Logger.d(Logger.Category.AR, "CameraX started successfully")
        } catch (e: Exception) {
            Logger.e(Logger.Category.AR, "CameraX start failed", e)
            onError(e.message ?: "Camera initialization failed")
        }
    }, ContextCompat.getMainExecutor(context))
}

/**
 * Interpolate between angles (handles wraparound at 360°)
 */
private fun lerpAngle(from: Float, to: Float, fraction: Float): Float {
    var delta = (to - from) % 360
    if (delta > 180) delta -= 360
    if (delta < -180) delta += 360
    return (from + delta * fraction) % 360
}
