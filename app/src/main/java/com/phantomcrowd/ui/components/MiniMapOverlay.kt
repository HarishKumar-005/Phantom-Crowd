package com.phantomcrowd.ui.components

import android.location.Location
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.phantomcrowd.data.AnchorData
import com.phantomcrowd.utils.MinimapCalculator

/**
 * MiniMap overlay composable that shows user position and nearby anchors.
 * Displays as a 150x150dp canvas with:
 * - Blue dot at center = user position
 * - Colored pins = nearby message anchors
 * - Light gray 100m radius circle
 */
@Composable
fun MiniMapOverlay(
    userLocation: Location?,
    heading: Float,
    nearbyAnchors: List<AnchorData>,
    modifier: Modifier = Modifier
) {
    val mapRadiusMeters = 100f
    
    Box(
        modifier = modifier
            .size(150.dp)
            .background(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Canvas(modifier = Modifier.size(150.dp)) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerX = canvasWidth / 2
            val centerY = canvasHeight / 2
            
            // 1. Draw 100m radius circle (light gray, outer boundary)
            drawCircle(
                color = Color.LightGray.copy(alpha = 0.5f),
                radius = (canvasWidth / 2) * 0.85f,
                center = Offset(centerX, centerY),
                style = Stroke(width = 1.dp.toPx())
            )
            
            // 2. Draw 50m radius circle (inner reference)
            drawCircle(
                color = Color.LightGray.copy(alpha = 0.3f),
                radius = (canvasWidth / 2) * 0.425f,
                center = Offset(centerX, centerY),
                style = Stroke(width = 0.5.dp.toPx())
            )
            
            // 3. Draw cardinal direction lines (N-S, E-W)
            val lineColor = Color.LightGray.copy(alpha = 0.2f)
            // Vertical line (N-S)
            drawLine(
                color = lineColor,
                start = Offset(centerX, 10.dp.toPx()),
                end = Offset(centerX, canvasHeight - 10.dp.toPx()),
                strokeWidth = 0.5.dp.toPx()
            )
            // Horizontal line (E-W)
            drawLine(
                color = lineColor,
                start = Offset(10.dp.toPx(), centerY),
                end = Offset(canvasWidth - 10.dp.toPx(), centerY),
                strokeWidth = 0.5.dp.toPx()
            )
            
            // 4. Draw message markers (colored pins)
            if (userLocation != null) {
                nearbyAnchors.forEach { anchor ->
                    val pos = MinimapCalculator.gpsToCanvas(
                        userLocation.latitude,
                        userLocation.longitude,
                        anchor.latitude,
                        anchor.longitude,
                        canvasWidth,
                        canvasHeight,
                        mapRadiusMeters
                    )
                    
                    // Category color mapping
                    val pinColor = when (anchor.category.lowercase()) {
                        "safety" -> Color(0xFFFF5252) // Red
                        "facility" -> Color(0xFFFFD740) // Amber
                        else -> Color(0xFF40C4FF) // Cyan (general)
                    }
                    
                    // Draw pin with outline for visibility
                    drawCircle(
                        color = Color.White,
                        radius = 7.dp.toPx(),
                        center = pos
                    )
                    drawCircle(
                        color = pinColor,
                        radius = 5.dp.toPx(),
                        center = pos
                    )
                }
                
                Log.d("MiniMapOverlay", "Rendered ${nearbyAnchors.size} pins on minimap")
            }
            
            // 5. Draw user position (blue dot at center) - drawn last to be on top
            // Outer glow
            drawCircle(
                color = Color.Blue.copy(alpha = 0.3f),
                radius = 12.dp.toPx(),
                center = Offset(centerX, centerY)
            )
            // Inner dot
            drawCircle(
                color = Color.Blue,
                radius = 6.dp.toPx(),
                center = Offset(centerX, centerY)
            )
            // White center for visibility
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = Offset(centerX, centerY)
            )
        }
    }
    
    if (userLocation != null) {
        Log.d("MiniMapOverlay", "User location: ${String.format("%.6f", userLocation.latitude)}, ${String.format("%.6f", userLocation.longitude)}")
    } else {
        Log.d("MiniMapOverlay", "User location: null, waiting for GPS...")
    }
}
