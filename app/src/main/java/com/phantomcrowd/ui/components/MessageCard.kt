package com.phantomcrowd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcrowd.data.SurfaceAnchor

/**
 * MessageCard - Compose overlay for displaying surface anchor messages.
 * 
 * Features:
 * - Color-coded by category
 * - Distance-based scaling
 * - Displays message text and distance
 * - Tap interaction for details
 */
@Composable
fun MessageCard(
    anchor: SurfaceAnchor,
    distance: Float,
    xOffset: Float,
    yPosition: Float,
    scale: Float,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val categoryColor = getCategoryColor(anchor.category)
    val planeIcon = getPlaneTypeIcon(anchor.planeType)
    
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
                    scaleY = scale,
                    alpha = if (distance > 80) 0.7f else 1f
                )
                .background(categoryColor.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Plane type icon
            Text(
                text = planeIcon,
                fontSize = 16.sp
            )
            
            // Message text
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
            
            // Category badge
            Text(
                text = anchor.category.replaceFirstChar { it.uppercase() },
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 9.sp
            )
            
            // Distance
            Text(
                text = formatDistance(distance),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Get color based on category.
 */
fun getCategoryColor(category: String): Color {
    return when (category.lowercase()) {
        "safety" -> Color(0xFFE53935)      // Red
        "facility" -> Color(0xFF1E88E5)    // Blue
        "event" -> Color(0xFF8E24AA)       // Purple
        "social" -> Color(0xFF43A047)      // Green
        "warning" -> Color(0xFFFF9800)     // Orange
        else -> Color(0xFF607D8B)           // Gray
    }
}

/**
 * Get icon based on plane type.
 */
fun getPlaneTypeIcon(planeType: String): String {
    return when (planeType) {
        "HORIZONTAL_UPWARD_FACING" -> "🏠"  // Floor/table
        "HORIZONTAL_DOWNWARD_FACING" -> "⬇️" // Ceiling
        "VERTICAL" -> "🧱"                   // Wall
        else -> "📍"
    }
}

/**
 * Format distance for display.
 */
fun formatDistance(distance: Float): String {
    return when {
        distance < 1 -> "<1m"
        distance < 10 -> "${distance.toInt()}m"
        distance < 100 -> "${distance.toInt()}m"
        distance < 1000 -> "${(distance / 10).toInt() * 10}m"
        else -> "${String.format("%.1f", distance / 1000)}km"
    }
}

/**
 * Calculate scale based on distance.
 * Closer = bigger, farther = smaller.
 */
fun calculateScale(distance: Float): Float {
    return when {
        distance < 10 -> 1.4f
        distance < 20 -> 1.2f
        distance < 30 -> 1.1f
        distance < 50 -> 1.0f
        distance < 75 -> 0.9f
        else -> 0.8f
    }
}
