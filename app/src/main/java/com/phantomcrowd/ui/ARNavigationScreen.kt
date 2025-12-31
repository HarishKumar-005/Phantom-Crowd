package com.phantomcrowd.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcrowd.ar.VoiceGuidanceManager
import com.phantomcrowd.data.AnchorData
import com.phantomcrowd.utils.BearingCalculator
import com.phantomcrowd.utils.Logger
import com.google.ar.core.Config
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.ArNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ViewNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberView
import kotlinx.coroutines.delay

/**
 * ARNavigationScreen - The WOW MOMENT feature!
 * 
 * Displays camera feed with floating arrow pointing to destination.
 * Features:
 * - AR Camera via SceneView 2.0.3
 * - 3D Arrow Node (ViewNode in 3D space) + 2D Overlay fallback
 * - Real-time distance display
 * - Voice guidance
 * - Color-coded distance indicator
 * - Pulse animation when close
 * - Haptic feedback
 */
@Composable
fun ARNavigationScreen(
    targetAnchor: AnchorData,
    userLocation: Location?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
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

    // Setup AR Engine and Nodes
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val view = rememberView(engine)

    // Create arrow node
    val arrowNode = remember(engine) {
        ArNode(engine).apply {
             position = Position(0.0f, 0.0f, -2.0f) // 2 meters ahead
        }
    }

    // Update rotation of the 3D node
    LaunchedEffect(displayRotation) {
        arrowNode.rotation = Rotation(0.0f, 0.0f, -displayRotation)
    }

    // Visual for the arrow using ViewNode
    LaunchedEffect(engine, arrowColor) {
        val viewNode = ViewNode(engine)
        viewNode.isFocusable = false
        // Create a TextView with a large arrow character as a simple visual representation
        val textView = TextView(context).apply {
            text = "⬆" // Unicode Up Arrow
            textSize = 100f
            setTextColor(arrowColor.toArgb())
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        viewNode.loadView(context, textView)
        viewNode.position = Position(0.0f, 0.0f, 0.0f)

        // Remove existing children to avoid duplicates if color changes
        arrowNode.destroyChildren()
        arrowNode.addChild(viewNode)
    }
    
    // Make sure the arrow node is rooted in the scene
    val childNodes = rememberNodes {
        add(arrowNode)
    }

    Box(modifier = modifier.fillMaxSize()) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            view = view,
            planeRenderer = true,
            childNodes = childNodes,
            sessionConfiguration = { session, config ->
                config.depthMode = when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    true -> Config.DepthMode.AUTOMATIC
                    else -> Config.DepthMode.DISABLED
                }
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            },
            onSessionUpdated = { session, frame ->
                // Future enhancement: Update arrowNode position to real-world anchor
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
        
        // 2D OVERLAY ARROW (Fallback + Always Visible)
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
            
            // Arrow icon with pulse animation
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
                }
            }
        }
    }
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
