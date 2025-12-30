package com.phantomcrowd.ui.components

import android.content.Context
import android.location.Location
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcrowd.data.AnchorData
import com.phantomcrowd.utils.BearingCalculator

/**
 * Direction arrow overlay that points toward the target anchor.
 * Shows distance and cardinal direction, with color coding based on proximity.
 */
@Composable
fun DirectionArrowOverlay(
    userLocation: Location?,
    deviceHeading: Float,
    targetAnchor: AnchorData?,
    modifier: Modifier = Modifier,
    onArrived: (AnchorData) -> Unit = {}
) {
    val context = LocalContext.current
    var hasVibratedForArrival by remember { mutableStateOf(false) }
    
    // Don't render if no location or target
    if (userLocation == null || targetAnchor == null) {
        Log.d("DirectionArrow", "Not rendering: userLocation=$userLocation, targetAnchor=$targetAnchor")
        return
    }
    
    // Calculate bearing to target
    val targetBearing = BearingCalculator.calculateBearing(
        userLocation.latitude,
        userLocation.longitude,
        targetAnchor.latitude,
        targetAnchor.longitude
    ).toFloat()
    
    // Calculate distance to target
    val results = FloatArray(1)
    Location.distanceBetween(
        userLocation.latitude,
        userLocation.longitude,
        targetAnchor.latitude,
        targetAnchor.longitude,
        results
    )
    val distance = results[0]
    
    // Get cardinal direction
    val cardinalDirection = BearingCalculator.bearingToCardinal(targetBearing.toDouble())
    
    // Check if arrived (within 5m)
    val isArrived = distance < 5f
    
    // Trigger arrival callback and haptic feedback
    LaunchedEffect(isArrived) {
        if (isArrived && !hasVibratedForArrival) {
            hasVibratedForArrival = true
            onArrived(targetAnchor)
            triggerHapticFeedback(context)
            Log.d("DirectionArrow", "ARRIVED at anchor: ${targetAnchor.messageText}")
        } else if (!isArrived) {
            hasVibratedForArrival = false
        }
    }
    
    // Arrow rotation = target bearing - device heading
    // deviceHeading is 0-360 where 0=North
    // We need the arrow to point in the direction of the target relative to device orientation
    val arrowRotation = (targetBearing - deviceHeading + 360) % 360
    
    // Distance-based color
    val arrowColor = when {
        distance < 5f -> Color(0xFF4CAF50)   // Green - Arrived
        distance < 20f -> Color(0xFF8BC34A)  // Light Green - Very close
        distance < 50f -> Color(0xFFFFEB3B)  // Yellow - Close
        else -> Color(0xFFFF5722)            // Orange-Red - Far
    }
    
    Log.d("DirectionArrow", "Bearing: ${String.format("%.1f", targetBearing)}°, Distance: ${String.format("%.1f", distance)}m, Arrow rotation: ${String.format("%.1f", arrowRotation)}°")
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Arrow canvas
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(40.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(60.dp)) {
                val centerX = size.width / 2
                val centerY = size.height / 2
                
                // Draw glow if arrived
                if (isArrived) {
                    drawCircle(
                        color = Color.Green.copy(alpha = 0.4f),
                        radius = 28.dp.toPx(),
                        center = center
                    )
                }
                
                // Draw rotated arrow
                rotate(degrees = arrowRotation, pivot = center) {
                    val arrowPath = Path().apply {
                        // Arrow pointing UP by default
                        moveTo(centerX, centerY - 22.dp.toPx()) // Tip (top)
                        lineTo(centerX - 12.dp.toPx(), centerY + 8.dp.toPx()) // Left base
                        lineTo(centerX - 4.dp.toPx(), centerY + 4.dp.toPx()) // Left inner
                        lineTo(centerX - 4.dp.toPx(), centerY + 18.dp.toPx()) // Left tail
                        lineTo(centerX + 4.dp.toPx(), centerY + 18.dp.toPx()) // Right tail
                        lineTo(centerX + 4.dp.toPx(), centerY + 4.dp.toPx()) // Right inner
                        lineTo(centerX + 12.dp.toPx(), centerY + 8.dp.toPx()) // Right base
                        close()
                    }
                    
                    // Draw arrow shadow for depth
                    drawPath(
                        path = arrowPath,
                        color = Color.Black.copy(alpha = 0.3f)
                    )
                    
                    // Draw main arrow
                    drawPath(
                        path = arrowPath,
                        color = arrowColor
                    )
                }
            }
        }
        
        // Distance + direction text
        Text(
            text = "${distance.toInt()}m $cardinalDirection",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        
        // Arrived message
        if (isArrived) {
            Text(
                text = "🎉 Arrived!",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Green,
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Trigger haptic feedback when user arrives at destination.
 */
private fun triggerHapticFeedback(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        }
        Log.d("DirectionArrow", "Haptic feedback triggered")
    } catch (e: Exception) {
        Log.e("DirectionArrow", "Failed to trigger haptic feedback", e)
    }
}
