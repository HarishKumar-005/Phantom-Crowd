package com.phantomcrowd.ui.tabs

import android.location.Location
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcrowd.data.AnchorData
import com.phantomcrowd.ui.theme.DesignSystem
import com.phantomcrowd.utils.BearingCalculator

/**
 * Navigation Guidance Tab with huge directional arrow.
 * Shows direction to target anchor, distance, progress, and arrival notification.
 * Now includes AR Navigation button for camera-based arrow view!
 */
@Composable
fun NavigationTab(
    userLocation: Location?,
    deviceHeading: Float = 0f,
    targetAnchor: AnchorData?,
    modifier: Modifier = Modifier,
    startLocationLat: Double = 0.0,
    startLocationLon: Double = 0.0,
    onOpenARNavigation: () -> Unit = {} // NEW: Callback to open AR view
) {
    // No target selected - show instructions
    if (targetAnchor == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DesignSystem.Colors.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🗺️",
                    fontSize = 64.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "No Target Selected",
                    style = DesignSystem.Typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = DesignSystem.Colors.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Go to Map tab and tap a marker\nto start navigation.",
                    style = DesignSystem.Typography.bodyMedium,
                    color = DesignSystem.Colors.neutralMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }
    
    // Waiting for location
    if (userLocation == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "📍 Waiting for GPS...",
                    fontSize = 20.sp,
                    color = Color.White
                )
                Text(
                    text = "Make sure location is enabled",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        Log.w("NavigationTab", "Waiting for user location")
        return
    }

    // Calculate distance
    val distance = calculateDistance(
        userLocation.latitude, userLocation.longitude,
        targetAnchor.latitude, targetAnchor.longitude
    )

    // Calculate bearing to target
    val bearing = BearingCalculator.calculateBearing(
        userLocation.latitude, userLocation.longitude,
        targetAnchor.latitude, targetAnchor.longitude
    ).toFloat()

    // Arrow rotation = bearing - device heading (so arrow points toward target)
    val arrowRotation = (bearing - deviceHeading + 360) % 360

    // Color based on distance: Green (<20m), Yellow (20-50m), Red (>50m)
    val arrowColor = when {
        distance < 5 -> DesignSystem.Colors.success      // Arrived!
        distance < 20 -> DesignSystem.Colors.severityLow  // Very close
        distance < 50 -> DesignSystem.Colors.warning       // Close
        else -> DesignSystem.Colors.severityHigh           // Far
    }

    // Calculate progress (from start location to target)
    val initialDistance = if (startLocationLat != 0.0 && startLocationLon != 0.0) {
        calculateDistance(startLocationLat, startLocationLon, targetAnchor.latitude, targetAnchor.longitude)
    } else {
        distance // Use current distance as initial if no start recorded
    }
    
    val progress = if (initialDistance > 0) {
        ((initialDistance - distance) / initialDistance).coerceIn(0f, 1f)
    } else {
        0f
    }

    val isArrived = distance < 5f

    Log.d("NavigationTab", "Distance: ${String.format("%.1f", distance)}m, Bearing: ${String.format("%.1f", bearing)}°, Progress: ${(progress * 100).toInt()}%")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DesignSystem.Colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Huge directional arrow
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Direction arrow",
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer(rotationZ = arrowRotation),
                tint = arrowColor
            )

            Spacer(modifier = Modifier.height(20.dp))
            
            // AR Navigation Button - THE WOW MOMENT!
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            Button(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onOpenARNavigation()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "🎯 Open AR Navigation",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))


            // Distance + Direction text
            Text(
                text = "${distance.toInt()}m ${BearingCalculator.bearingToCardinal(bearing.toDouble())}",
                style = DesignSystem.Typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = DesignSystem.Colors.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .width(280.dp)
                    .height(12.dp),
                color = DesignSystem.Colors.success,
                trackColor = DesignSystem.Colors.outline
            )

            Text(
                text = "${(progress * 100).toInt()}% complete",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Message preview
            Text(
                text = "📍 ${targetAnchor.category.uppercase()}",
                fontSize = 12.sp,
                color = when (targetAnchor.category.lowercase()) {
                    "safety" -> Color(0xFFFF5252)
                    "facility" -> Color(0xFFFFD740)
                    else -> Color(0xFF40C4FF)
                },
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = targetAnchor.messageText,
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                    .background(
                        Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            )

            // Arrived notification
            if (isArrived) {
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "✅ You've arrived!",
                    style = DesignSystem.Typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = DesignSystem.Colors.success,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "Switch to AR View to see the report.",
                    style = DesignSystem.Typography.bodyMedium,
                    color = DesignSystem.Colors.neutralMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/**
 * Calculate distance between two GPS points in meters.
 */
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val results = FloatArray(1)
    Location.distanceBetween(lat1, lon1, lat2, lon2, results)
    return results[0]
}
