package com.phantomcrowd.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Helper object for permission checks.
 * Runtime permission requests are handled via ActivityResultContracts in the UI layer.
 */
object PermissionHelper {

    private const val TAG = "Permission"

    fun hasCameraPermission(context: Context): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Camera permission check: $granted")
        return granted
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val granted = fineGranted || coarseGranted
        Log.d(TAG, "Location permission check: fine=$fineGranted, coarse=$coarseGranted")
        return granted
    }

    fun hasAllPermissions(context: Context): Boolean {
        return hasCameraPermission(context) && hasLocationPermission(context)
    }
}
