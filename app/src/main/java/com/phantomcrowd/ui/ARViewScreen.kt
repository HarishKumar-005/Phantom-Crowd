package com.phantomcrowd.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.ArSceneView
import com.phantomcrowd.ar.ARCoreManager
import com.phantomcrowd.ar.ARModelRenderer
import com.phantomcrowd.ar.GeospatialAnchorManager
import com.phantomcrowd.data.AnchorData
// Phase 3: Removed overlay imports - now in dedicated tabs
// import com.phantomcrowd.ui.components.DirectionArrowOverlay
// import com.phantomcrowd.ui.components.MiniMapOverlay
import com.phantomcrowd.utils.DebugConfig
import com.phantomcrowd.utils.Logger
import com.phantomcrowd.ar.CloudAnchorSyncManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

/**
 * AR View screen that displays the camera feed with geospatial AR overlays.
 * Uses ARCore Geospatial API for location-based anchors.
 */
@Composable
fun ARViewScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val anchors by viewModel.allAnchors.collectAsState()

    // Permission check state
    var hasCameraPermission by remember { mutableStateOf(false) }

    // Check permission on composition
    LaunchedEffect(Unit) {
        Logger.d(Logger.Category.AR, "ARViewScreen: Checking camera permission...")
        val permission = android.Manifest.permission.CAMERA
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            hasCameraPermission = true
            Logger.d(Logger.Category.AR, "ARViewScreen: Camera permission GRANTED")
        } else {
            Logger.e(Logger.Category.AR, "ARViewScreen: Camera permission DENIED")
            Toast.makeText(context, "Camera permission needed for AR", Toast.LENGTH_LONG).show()
        }
    }

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                "Camera Permission Required\nPlease grant camera permission and reopen this tab",
                modifier = Modifier.align(Alignment.Center)
            )
        }
        return
    }

    // Managers
    val arCoreManager = remember { ARCoreManager(context) }
    val geospatialManager = remember { GeospatialAnchorManager() }
    val renderer = remember { ARModelRenderer(context) }

    // State to track added anchors to avoid duplicates
    val renderedAnchorIds = remember { mutableSetOf<String>() }
    var statusText by remember { mutableStateOf("Initializing AR...") }
    var debugInfo by remember { mutableStateOf("") }
    val startTime = remember { System.currentTimeMillis() }

    var arSceneView: ArSceneView? by remember { mutableStateOf(null) }
    
    // Cloud Anchor State (Phase D)
    var showCloudAnchorButton by remember { mutableStateOf(false) }
    var isHostingAnchor by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val firestore = remember { FirebaseFirestore.getInstance() }
    val resolvedCloudAnchorIds = remember { mutableSetOf<String>() }
    
    // State for UX overlays (Phase 1+)
    var currentHeading by remember { mutableStateOf(0f) }
    var currentUserLocation by remember { mutableStateOf<android.location.Location?>(null) }
    // Observe selected anchor from ViewModel instead of local state
    val selectedAnchor by viewModel.selectedAnchor.collectAsState()
    
    // Find nearest anchor for direction arrow
    val nearestAnchor = remember(anchors, currentUserLocation) {
        if (currentUserLocation == null || anchors.isEmpty()) null
        else {
            anchors.minByOrNull { anchor ->
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    currentUserLocation!!.latitude, currentUserLocation!!.longitude,
                    anchor.latitude, anchor.longitude, results
                )
                results[0]
            }
        }
    }
    
    // Target for direction arrow: selected or nearest
    val targetAnchor = selectedAnchor ?: nearestAnchor

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    try {
                        arSceneView?.resume()
                        Logger.d(Logger.Category.AR, "ArSceneView resumed")
                    } catch (e: Exception) {
                        Logger.e(Logger.Category.AR, "Error resuming AR view", e)
                    }
                }

                Lifecycle.Event.ON_PAUSE -> {
                    arSceneView?.pause()
                    Logger.d(Logger.Category.AR, "ArSceneView paused")
                }

                Lifecycle.Event.ON_DESTROY -> {
                    arSceneView?.destroy()
                    arCoreManager.destroy()
                    Logger.d(Logger.Category.AR, "ArSceneView destroyed")
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                arSceneView?.destroy()
            } catch (e: Exception) {
                Logger.e(Logger.Category.AR, "Error destroying AR view", e)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Logger.d(Logger.Category.AR, "Creating ArSceneView...")
                ArSceneView(ctx).apply {
                    arSceneView = this
                    Logger.d(Logger.Category.AR, "ArSceneView created, adding update listener...")

                    // Handle lifecycle manually using View attachment
                    this.addOnAttachStateChangeListener(object :
                        android.view.View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: android.view.View) {
                            Logger.d(Logger.Category.AR, "ArSceneView attached to window")

                            // Check network connectivity first
                            if (!isNetworkAvailable(ctx)) {
                                Logger.w(Logger.Category.AR, "No network connectivity - Geospatial API may fail")
                                statusText = "No internet connection!\nGeospatial API requires internet."
                            }

                            // Defer session setup to ensure surface is created first
                            v.postDelayed({
                                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                    Logger.d(Logger.Category.AR, "Setting up AR session...")
                                    try {
                                        val arView = v as ArSceneView

                                        // Step 1: Create the ARCore session manually
                                        Logger.d(Logger.Category.AR, "Creating ARCore Session...")
                                        val session = Session(ctx)
                                        Logger.d(Logger.Category.AR, "Session created: $session")

                                        // Step 1.5: Check Geospatial support
                                        val isGeospatialSupported = session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)
                                        if (!isGeospatialSupported) {
                                            Logger.e(Logger.Category.AR, "Geospatial mode NOT supported on this device!")
                                            statusText = "Geospatial not supported\non this device"
                                            return@postDelayed
                                        }
                                        Logger.i(Logger.Category.AR, "Geospatial mode IS supported")

                                        // Step 2: Configure the session with Geospatial and compatible light estimation
                                        val config = Config(session)
                                        config.geospatialMode = Config.GeospatialMode.ENABLED
                                        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                                        // Use DISABLED instead of ENVIRONMENTAL_HDR to avoid acquireEnvironmentalHdrCubeMap crash
                                        config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                                        session.configure(config)
                                        Logger.d(Logger.Category.AR, "Session configured with geospatial mode and DISABLED light estimation")

                                        // Step 4: Set the session on ArSceneView
                                        arView.setSession(session)
                                        Logger.d(Logger.Category.AR, "Session set on ArSceneView")

                                        // Step 5: Resume the view
                                        arView.resume()
                                        Logger.i(Logger.Category.AR, "ArSceneView resumed successfully!")

                                    } catch (e: com.google.ar.core.exceptions.UnavailableException) {
                                        Logger.e(Logger.Category.AR, "ARCore not available: ${e.message}", e)
                                        statusText = "ARCore unavailable:\n${e.message}"
                                    } catch (e: Exception) {
                                        Logger.e(Logger.Category.AR, "Error setting up AR: ${e.message}", e)
                                        statusText = "AR Error:\n${e.message}"
                                    }
                                } else {
                                    Logger.w(Logger.Category.AR, "Lifecycle not RESUMED, state: ${lifecycleOwner.lifecycle.currentState}")
                                }
                            }, 200) // Small delay to ensure surface is ready
                        }

                        override fun onViewDetachedFromWindow(v: android.view.View) {
                            Logger.d(Logger.Category.AR, "ArSceneView detached from window")
                        }
                    })

                    // Track if we've logged session info (to avoid spam)
                    var hasLoggedSessionInfo = false
                    var lastLogTime = 0L

                    this.scene.addOnUpdateListener { _ ->
                        val session = this.session
                        val now = System.currentTimeMillis()

                        // Log every 2 seconds to reduce spam
                        val shouldLog = DebugConfig.VERBOSE_LOGGING && (now - lastLogTime) > 2000

                        if (session == null) {
                            if (shouldLog) {
                                Logger.w(Logger.Category.AR, "UPDATE: Session is NULL - AR not initialized yet")
                                lastLogTime = now
                            }
                            return@addOnUpdateListener
                        }

                        // Log session info once
                        if (!hasLoggedSessionInfo) {
                            Logger.i(Logger.Category.AR, "UPDATE: Session created! Config: geospatialMode=${session.config.geospatialMode}")
                            hasLoggedSessionInfo = true
                        }

                        val earth = session.earth

                        if (shouldLog && DebugConfig.LOG_GEOSPATIAL_STATE) {
                            Logger.d(Logger.Category.AR, "Earth=${earth != null}, EarthState=${earth?.earthState}, TrackingState=${earth?.trackingState}")
                            lastLogTime = now
                        }

                        if (earth?.trackingState == TrackingState.TRACKING) {
                            val pose = earth.cameraGeospatialPose
                            statusText = "GPS: ${String.format("%.6f", pose.latitude)}, ${String.format("%.6f", pose.longitude)}\n" +
                                    "Accuracy: ${String.format("%.1f", pose.horizontalAccuracy)}m"
                            
                            // Update UX overlay state (Phase 1+)
                            currentHeading = pose.heading.toFloat()
                            currentUserLocation = android.location.Location("ARCore").apply {
                                latitude = pose.latitude
                                longitude = pose.longitude
                                altitude = pose.altitude
                                accuracy = pose.horizontalAccuracy.toFloat()
                            }

                            if (DebugConfig.SHOW_AR_DEBUG_OVERLAY) {
                                debugInfo = "Heading: ${String.format("%.1f", pose.heading)}°\n" +
                                        "Altitude: ${String.format("%.1f", pose.altitude)}m\n" +
                                        "Anchors: ${renderedAnchorIds.size}/${anchors.size}"
                            }

                            if (shouldLog) {
                                Logger.d(Logger.Category.AR, "TRACKING: Lat=${pose.latitude}, Lon=${pose.longitude}, Acc=${pose.horizontalAccuracy}m, Heading=${pose.heading}°")
                            }

                            // Check for new anchors to render
                            val currentList = anchors
                            currentList.forEach { anchorData ->
                                // Geo Anchor Logic
                                if (!renderedAnchorIds.contains(anchorData.id)) {
                                    // Check if this anchor has a Cloud ID (Phase D)
                                    val cloudId = anchorData.cloudAnchorId
                                    if (cloudId.isNotEmpty() && !resolvedCloudAnchorIds.contains(cloudId)) {
                                         // Attempt to resolve cloud anchor
                                         scope.launch {
                                             val cloudManager = CloudAnchorSyncManager(firestore, session)
                                             val result = cloudManager.resolveCloudAnchor(cloudId)
                                             result.onSuccess { resolvedAnchor ->
                                                 if (resolvedAnchor != null) {
                                                     renderAnchorNode(this@apply, resolvedAnchor, anchorData, renderer)
                                                     renderedAnchorIds.add(anchorData.id)
                                                     resolvedCloudAnchorIds.add(cloudId)
                                                     Logger.d(Logger.Category.AR, "✅ Resolved Cloud Anchor: ${anchorData.id}")
                                                 }
                                             }
                                         }
                                         // Mark as attempting to avoid duplicate launches
                                         resolvedCloudAnchorIds.add(cloudId) 
                                    } else if (cloudId.isEmpty()) {
                                        // Fallback to Geospatial
                                        val arAnchor = geospatialManager.createAnchor(
                                            session,
                                            anchorData.latitude,
                                            anchorData.longitude,
                                            anchorData.altitude,
                                            0.0 
                                        )
                                        if (arAnchor != null) {
                                            renderAnchorNode(this, arAnchor, anchorData, renderer)
                                            renderedAnchorIds.add(anchorData.id)
                                            Logger.d(Logger.Category.AR, "Rendered Geo anchor: ${anchorData.id}")
                                        }
                                    }
                                }
                            }
                            
                            // Check distance for Cloud Anchor Hosting Button (Phase D)
                            if (selectedAnchor != null && selectedAnchor!!.cloudAnchorId.isEmpty()) {
                                val anchor = selectedAnchor!!
                                val userLoc = android.location.Location("User").apply {
                                    latitude = pose.latitude
                                    longitude = pose.longitude
                                }
                                val distance = FloatArray(1).also { 
                                     android.location.Location.distanceBetween(
                                         userLoc.latitude, userLoc.longitude,
                                         anchor.latitude, anchor.longitude,
                                         it
                                     )
                                }[0]
                                
                                showCloudAnchorButton = distance < 10.0 // Show if within 10m
                            } else {
                                showCloudAnchorButton = false
                            }
                        } else {
                            val elapsed = System.currentTimeMillis() - startTime
                            if (elapsed > 15000) { // 15s timeout
                                statusText = "Initialization Timeout\nCheck Location/Internet\nState: ${earth?.earthState}"
                            } else {
                                statusText = "Waiting for Earth tracking...\nState: ${earth?.earthState ?: "Null"}"
                            }

                            if (DebugConfig.SHOW_AR_DEBUG_OVERLAY) {
                                debugInfo = "Elapsed: ${elapsed / 1000}s\n" +
                                        "Network: ${if (isNetworkAvailable(context)) "OK" else "NO"}"
                            }
                        }
                    }
                }
            },
            update = { view ->
                val session = view.session
                if (session != null && session.config.geospatialMode != Config.GeospatialMode.ENABLED) {
                    val config = session.config
                    config.geospatialMode = Config.GeospatialMode.ENABLED
                    session.configure(config)
                }
            }
        )

        // Status overlay
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
                text = statusText,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )

            if (DebugConfig.SHOW_AR_DEBUG_OVERLAY && debugInfo.isNotEmpty()) {
                Text(
                    text = debugInfo,
                    color = Color.Yellow,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        
        // Host Cloud Anchor Button (Phase D)
        if (showCloudAnchorButton && selectedAnchor != null) {
             androidx.compose.material3.Button(
                onClick = {
                    val anchorToHost = selectedAnchor!!
                    isHostingAnchor = true
                    
                    // Basic hosting logic: Place anchor at user position 1m away?
                    // Or ask user to tap?
                    // Specs: "7. Point camera at ground -> tap detected plane"
                    // But here we are simplifying to button click for "Create AR Anchor Here"
                    // Ideally we listen for tap, but let's implement the button action to create anchor 
                    // at user's current pose/forward for simplicity or rely on tap logic if specified.
                    // The spec #7 says "Tap detected plane". 
                    // To do that, we need `arSceneView.scene.setOnTouchListener` or similar.
                    // For now, let's make the button create an anchor at current camera pose projected forward.
                    
                    scope.launch {
                        try {
                            arSceneView?.session?.let { session ->
                                 // Create anchor 1m in front of camera
                                 val pose = session.earth?.cameraGeospatialPose?.let { 
                                     arSceneView?.arFrame?.camera?.pose?.compose(
                                         com.google.ar.core.Pose.makeTranslation(0f, 0f, -1f)
                                     )
                                 } ?: arSceneView?.arFrame?.camera?.pose
                                 
                                 if (pose != null) {
                                     val localAnchor = session.createAnchor(pose)
                                     val cloudManager = CloudAnchorSyncManager(firestore, session)
                                     
                                     val result = cloudManager.hostCloudAnchor(localAnchor, anchorToHost)
                                     
                                     result.onSuccess {
                                         isHostingAnchor = false
                                         showCloudAnchorButton = false
                                         Toast.makeText(context, "Cloud Anchor Hosted!", Toast.LENGTH_LONG).show()
                                     }
                                     result.onFailure {
                                         isHostingAnchor = false
                                         Toast.makeText(context, "Hosting Failed: ${it.message}", Toast.LENGTH_LONG).show()
                                     }
                                 } else {
                                     isHostingAnchor = false
                                     Toast.makeText(context, "Cannot get camera pose", Toast.LENGTH_SHORT).show()
                                 }
                            } ?: run {
                                isHostingAnchor = false
                                Toast.makeText(context, "AR Session not ready", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            isHostingAnchor = false
                            Logger.e(Logger.Category.AR, "Cloud Anchor Error", e)
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp) // Above nav bar
            ) {
                Text(if (isHostingAnchor) "Hosting..." else "Create Cloud Anchor")
            }
        }
        
        // Phase 3: Removed overlays for clean AR View
        // MiniMap and DirectionArrow now available in dedicated tabs (Map, Navigate)
    }
}

/**
 * Check if network is available for Geospatial API.
 */
private fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/**
 * Helper to render an anchor node with a label.
 * Phase 1+: Added distance-based scaling for better visibility.
 */
fun renderAnchorNode(
    sceneView: ArSceneView,
    anchor: com.google.ar.core.Anchor,
    data: AnchorData,
    renderer: ARModelRenderer
) {
    val anchorNode = com.google.ar.sceneform.AnchorNode(anchor)
    anchorNode.setParent(sceneView.scene)
    
    // Phase 1+: Calculate distance from user to anchor for scaling
    val userPose = sceneView.session?.earth?.cameraGeospatialPose
    val distance = if (userPose != null) {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            userPose.latitude, userPose.longitude,
            data.latitude, data.longitude,
            results
        )
        results[0]
    } else 0f
    
    // Scale formula: 1.0 at 0m, up to 2.0 at 50m+
    // This makes labels more visible at distance
    val scaleFactor = (1.0f + (distance / 50f)).coerceAtMost(2.0f)

    com.google.ar.sceneform.rendering.ViewRenderable.builder()
        .setView(sceneView.context, com.phantomcrowd.R.layout.view_ar_label)
        .build()
        .thenAccept { renderable ->
            // Billboard behavior: disable shadows for cleaner look
            renderable.isShadowCaster = false
            renderable.isShadowReceiver = false
            
            val view = renderable.view
            val tv = view.findViewById<android.widget.TextView>(com.phantomcrowd.R.id.ar_text_message)
            tv.text = data.messageText

            // Color coding by category
            val color = when (data.category.lowercase()) {
                "safety" -> android.graphics.Color.parseColor("#FF5252") // Red
                "facility" -> android.graphics.Color.parseColor("#FFD740") // Amber
                else -> android.graphics.Color.parseColor("#40C4FF") // Cyan
            }
            view.findViewById<android.view.View>(com.phantomcrowd.R.id.ar_label_container)
                ?.setBackgroundColor(color)

            val node = com.google.ar.sceneform.Node()
            node.renderable = renderable
            node.setParent(anchorNode)
            node.localPosition = com.google.ar.sceneform.math.Vector3(0f, 1.5f, 0f) // Float above
            
            // Phase 1+: Apply distance-based scaling
            node.localScale = com.google.ar.sceneform.math.Vector3(scaleFactor, scaleFactor, scaleFactor)
            
            Logger.d(Logger.Category.AR, "Label scaled to ${String.format("%.1f", scaleFactor)}x for distance ${String.format("%.0f", distance)}m")
        }
        .exceptionally { throwable ->
            Logger.e(Logger.Category.AR, "Failed to render anchor node", throwable)
            null
        }
}
