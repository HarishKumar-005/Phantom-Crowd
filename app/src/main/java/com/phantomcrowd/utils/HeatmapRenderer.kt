package com.phantomcrowd.utils

import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import android.graphics.Color
import com.phantomcrowd.data.AnchorData
import android.util.Log
import org.osmdroid.util.GeoPoint

class HeatmapRenderer(private val mapView: MapView) {
    
    companion object {
        private const val TAG = "HeatmapRenderer"
        private const val GRID_SIZE_DEGREES = 0.01  // ~1 km grid cell
    }
    
    private val heatmapCells = mutableListOf<Polygon>()
    
    /**
     * Render heatmap based on anchor density
     */
    fun renderHeatmap(anchors: List<AnchorData>) {
        // Clear previous cells
        heatmapCells.forEach { mapView.overlays.remove(it) }
        heatmapCells.clear()
        
        if (anchors.isEmpty()) {
            mapView.invalidate()
            return
        }
        
        // Group anchors into grid cells
        val grid = mutableMapOf<String, MutableList<AnchorData>>()
        
        anchors.forEach { anchor ->
            val cellKey = getCellKey(anchor.latitude, anchor.longitude)
            grid.getOrPut(cellKey) { mutableListOf() }.add(anchor)
        }
        
        // Create polygon for each grid cell
        grid.forEach { (cellKey, cellAnchors) ->
            val (baseLat, baseLon) = parseCellKey(cellKey)
            
            val density = cellAnchors.size
            val (color, alpha) = getColorForDensity(density)
            
            // Create polygon bounds
            val north = baseLat + GRID_SIZE_DEGREES
            val south = baseLat
            val east = baseLon + GRID_SIZE_DEGREES
            val west = baseLon
            
            val polygon = Polygon(mapView).apply {
                setPoints(listOf(
                    GeoPoint(north, west),
                    GeoPoint(north, east),
                    GeoPoint(south, east),
                    GeoPoint(south, west)
                ))
                fillPaint.color = color
                fillPaint.alpha = alpha
                outlinePaint.color = darkenColor(color)
                outlinePaint.strokeWidth = 2f
            }
            
            mapView.overlays.add(polygon)
            heatmapCells.add(polygon)
        }
        
        Log.d(TAG, "Rendered heatmap with ${heatmapCells.size} cells")
        mapView.invalidate()
    }
    
    /**
     * Get grid cell key for lat/lon
     */
    private fun getCellKey(lat: Double, lon: Double): String {
        val cellLat = kotlin.math.floor(lat / GRID_SIZE_DEGREES) * GRID_SIZE_DEGREES
        val cellLon = kotlin.math.floor(lon / GRID_SIZE_DEGREES) * GRID_SIZE_DEGREES
        return "$cellLat,$cellLon"
    }
    
    /**
     * Parse grid cell key back to coordinates
     */
    private fun parseCellKey(key: String): Pair<Double, Double> {
        val (lat, lon) = key.split(",")
        return Pair(lat.toDouble(), lon.toDouble())
    }
    
    /**
     * Color based on density
     */
    private fun getColorForDensity(density: Int): Pair<Int, Int> {
        return when {
            density >= 5 -> Pair(Color.RED, 80)          // High: Red
            density >= 2 -> Pair(Color.YELLOW, 80)       // Medium: Yellow
            else -> Pair(Color.GREEN, 80)                 // Low: Green
        }
    }
    
    /**
     * Darken color for outline
     */
    private fun darkenColor(color: Int): Int {
        val r = Color.red(color) / 2
        val g = Color.green(color) / 2
        val b = Color.blue(color) / 2
        return Color.rgb(r, g, b)
    }
    
    /**
     * Clear all heatmap cells
     */
    fun clearHeatmap() {
        heatmapCells.forEach { mapView.overlays.remove(it) }
        heatmapCells.clear()
        mapView.invalidate()
        Log.d(TAG, "Heatmap cleared")
    }
}
