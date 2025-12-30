package com.phantomcrowd.ui.tabs

import android.content.Context
import android.graphics.drawable.Drawable
import android.location.Location
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.phantomcrowd.data.AnchorData
import com.phantomcrowd.utils.HeatmapRenderer
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Map Discovery Tab using OpenStreetMap (osmdroid).
 * Shows user location and nearby message anchors on a full-screen map.
 * No API key required - fully free and open source.
 */
@Composable
fun MapDiscoveryTab(
    userLocation: Location?,
    nearbyAnchors: List<AnchorData>,
    modifier: Modifier = Modifier,
    showHeatmap: Boolean = false,
    onNavigateTo: (AnchorData) -> Unit
) {
    val context = LocalContext.current
    
    // Initialize osmdroid configuration
    val mapView = remember {
        // Set user agent for tile requests (required by osmdroid)
        Configuration.getInstance().userAgentValue = context.packageName
        
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(17.0)
            Log.d("MapDiscoveryTab", "MapView created with MAPNIK tiles")
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            mapView.onDetach()
            Log.d("MapDiscoveryTab", "MapView detached")
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        if (userLocation == null) {
            // Show loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "📍 Waiting for GPS location...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Make sure location is enabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Log.w("MapDiscoveryTab", "User location is null, showing loading state")
        } else {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
                update = { map ->
                    // Center map on user location
                    val userGeoPoint = GeoPoint(userLocation.latitude, userLocation.longitude)
                    map.controller.setCenter(userGeoPoint)
                    Log.d("MapDiscoveryTab", "Map centered on: ${userLocation.latitude}, ${userLocation.longitude}")
                    
                    // Clear existing overlays
                    map.overlays.clear()
                    
                    // Add user location marker (blue dot)
                    val userMarker = Marker(map).apply {
                        position = userGeoPoint
                        title = "You are here"
                        snippet = "Your current location"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        // Use a blue color indicator
                        try {
                            icon = createColoredMarkerDrawable(context, android.graphics.Color.BLUE)
                        } catch (e: Exception) {
                            Log.w("MapDiscoveryTab", "Could not set custom icon: ${e.message}")
                        }
                    }
                    map.overlays.add(userMarker)
                    
                    // Add nearby anchor markers
                    nearbyAnchors.forEach { anchor ->
                        val distance = calculateDistance(
                            userLocation.latitude, userLocation.longitude,
                            anchor.latitude, anchor.longitude
                        )
                        
                        // Category color: Red=Safety, Amber=Facility, Cyan=General
                        val markerColor = when (anchor.category.lowercase()) {
                            "safety" -> android.graphics.Color.RED
                            "facility" -> android.graphics.Color.parseColor("#FFA500") // Orange/Amber
                            else -> android.graphics.Color.CYAN
                        }
                        
                        val marker = Marker(map).apply {
                            position = GeoPoint(anchor.latitude, anchor.longitude)
                            title = anchor.messageText
                            snippet = "${distance.toInt()}m away • ${anchor.category}"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            try {
                                icon = createColoredMarkerDrawable(context, markerColor)
                            } catch (e: Exception) {
                                Log.w("MapDiscoveryTab", "Could not set marker icon: ${e.message}")
                            }
                        }
                        
                        marker.setOnMarkerClickListener { _, _ ->
                            Log.d("MapDiscoveryTab", "Marker tapped: ${anchor.messageText}")
                            onNavigateTo(anchor)
                            true
                        }
                        
                        map.overlays.add(marker)
                    }
                    
                    Log.d("MapDiscoveryTab", "Added ${nearbyAnchors.size} message markers")
                    
                    // Heatmap rendering
                    if (showHeatmap && nearbyAnchors.isNotEmpty()) {
                        val heatmapRenderer = HeatmapRenderer(map)
                        heatmapRenderer.renderHeatmap(nearbyAnchors)
                    } else {
                        // Clear heatmap if toggled off
                        val heatmapRenderer = HeatmapRenderer(map)
                        heatmapRenderer.clearHeatmap()
                    }
                    
                    map.invalidate()
                }
            )
            
            // Info overlay
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = "📍 ${String.format("%.5f", userLocation.latitude)}, ${String.format("%.5f", userLocation.longitude)}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "🗺️ ${nearbyAnchors.size} messages nearby",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
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

/**
 * Create a simple colored circle drawable for markers.
 */
private fun createColoredMarkerDrawable(context: Context, color: Int): Drawable? {
    return try {
        val drawable = android.graphics.drawable.GradientDrawable()
        drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
        drawable.setColor(color)
        drawable.setStroke(4, android.graphics.Color.WHITE)
        drawable.setSize(40, 40)
        drawable
    } catch (e: Exception) {
        Log.e("MapDiscoveryTab", "Error creating marker drawable", e)
        null
    }
}
