package com.phantomcrowd.ui

import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.phantomcrowd.utils.Logger
import kotlinx.coroutines.launch

@Composable
fun ARViewScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val anchors by viewModel.allAnchors.collectAsState()
    
    // Permission check state
    var hasCameraPermission by remember { mutableStateOf(false) }
    
    // Check permission on composition
    LaunchedEffect(Unit) {
        android.util.Log.d("ARCore", "ARViewScreen: Checking camera permission...")
        val permission = android.Manifest.permission.CAMERA
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
             hasCameraPermission = true
             android.util.Log.d("ARCore", "ARViewScreen: Camera permission GRANTED")
        } else {
             android.util.Log.e("ARCore", "ARViewScreen: Camera permission DENIED")
             Toast.makeText(context, "Camera permission needed for AR", Toast.LENGTH_LONG).show()
        }
    }
    
    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text("Camera Permission Required\nPlease grant camera permission and reopen this tab", 
                modifier = Modifier.align(Alignment.Center))
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
    val startTime = remember { System.currentTimeMillis() }

    var arSceneView: ArSceneView? by remember { mutableStateOf(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    try {
                        arSceneView?.resume()
                    } catch (e: Exception) {
                        Logger.e("Error resuming AR view", e)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> arSceneView?.pause()
                Lifecycle.Event.ON_DESTROY -> {
                    arSceneView?.destroy()
                    arCoreManager.destroy()
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
                Logger.e("Error destroying AR view", e)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                android.util.Log.d("ARCore", "Creating ArSceneView...")
                ArSceneView(ctx).apply {
                    arSceneView = this
                    android.util.Log.d("ARCore", "ArSceneView created, adding update listener...")
                    
                    // Handle lifecycle manually using View attachment
                    // We need to create and set the session before calling resume()
                    this.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: android.view.View) {
                            android.util.Log.d("ARCore", "ArSceneView attached to window")
                            // Defer session setup to ensure surface is created first
                            v.postDelayed({
                                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                    android.util.Log.d("ARCore", "Setting up AR session...")
                                    try {
                                        val arView = v as ArSceneView
                                        
                                        // Step 1: Create the ARCore session manually
                                        android.util.Log.d("ARCore", "Creating ARCore Session...")
                                        val session = Session(ctx)
                                        android.util.Log.d("ARCore", "Session created: $session")
                                        
                                        // Step 2: Configure the session with Geospatial
                                        val config = Config(session)
                                        config.geospatialMode = Config.GeospatialMode.ENABLED
                                        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                                        session.configure(config)
                                        android.util.Log.d("ARCore", "Session configured with geospatial mode")
                                        
                                        // Step 3: Set the session on ArSceneView
                                        arView.setSession(session)
                                        android.util.Log.d("ARCore", "Session set on ArSceneView")
                                        
                                        // Step 4: Resume the view
                                        arView.resume()
                                        android.util.Log.d("ARCore", "ArSceneView resumed!")
                                        android.util.Log.d("ARCore", "Session after setup: ${arView.session}")
                                        
                                    } catch (e: com.google.ar.core.exceptions.UnavailableException) {
                                        android.util.Log.e("ARCore", "ARCore not available: ${e.message}", e)
                                        statusText = "ARCore unavailable: ${e.message}"
                                    } catch (e: Exception) {
                                        android.util.Log.e("ARCore", "Error setting up AR: ${e.message}", e)
                                        statusText = "AR Error: ${e.message}"
                                    }
                                } else {
                                    android.util.Log.w("ARCore", "Lifecycle not RESUMED, state: ${lifecycleOwner.lifecycle.currentState}")
                                }
                            }, 200) // Small delay to ensure surface is ready
                        }

                        override fun onViewDetachedFromWindow(v: android.view.View) {
                            android.util.Log.d("ARCore", "ArSceneView detached from window")
                        }
                    })

                    // Track if we've logged session info (to avoid spam)
                    var hasLoggedSessionInfo = false
                    var lastLogTime = 0L
                    
                    this.scene.addOnUpdateListener { _ ->
                        val session = this.session
                        val now = System.currentTimeMillis()
                        
                        // Log every 2 seconds to reduce spam
                        val shouldLog = (now - lastLogTime) > 2000
                        
                        if (session == null) {
                            if (shouldLog) {
                                android.util.Log.w("ARCore", "UPDATE: Session is NULL - AR not initialized yet")
                                lastLogTime = now
                            }
                            return@addOnUpdateListener
                        }
                        
                        // Log session info once
                        if (!hasLoggedSessionInfo) {
                            android.util.Log.d("ARCore", "UPDATE: Session created! Config: geospatialMode=${session.config.geospatialMode}")
                            hasLoggedSessionInfo = true
                        }
                        
                        val earth = session.earth
                        
                        if (shouldLog) {
                            android.util.Log.d("ARCore", "UPDATE: Earth=${earth != null}, EarthState=${earth?.earthState}, TrackingState=${earth?.trackingState}")
                            lastLogTime = now
                        }
                        
                        if (earth?.trackingState == TrackingState.TRACKING) {
                            val pose = earth.cameraGeospatialPose
                            statusText = "GPS: ${pose.latitude}, ${pose.longitude}\nAcc: ${pose.horizontalAccuracy}m"
                            
                            if (shouldLog) {
                                android.util.Log.d("ARCore", "TRACKING: Lat=${pose.latitude}, Lon=${pose.longitude}, Acc=${pose.horizontalAccuracy}m")
                            }
                            
                            // Check for new anchors to render
                            val currentList = anchors
                            currentList.forEach { anchorData ->
                                if (!renderedAnchorIds.contains(anchorData.id)) {
                                    val arAnchor = geospatialManager.createAnchor(
                                        session,
                                        anchorData.latitude,
                                        anchorData.longitude,
                                        anchorData.altitude,
                                        0.0 // Rotation
                                    )
                                    if (arAnchor != null) {
                                        renderAnchorNode(this, arAnchor, anchorData, renderer)
                                        renderedAnchorIds.add(anchorData.id)
                                    }
                                }
                            }
                        } else {
                            val elapsed = System.currentTimeMillis() - startTime
                            if (elapsed > 10000) { // 10s timeout
                                 statusText = "Initialization Timeout. \nCheck Location/Internet.\nState: ${earth?.earthState}"
                            } else {
                                 statusText = "Waiting for Earth tracking... \nState: ${earth?.earthState ?: "Null"}"
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
        
        Text(
            text = statusText,
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )
    }
}

// Helper to bridge the Renderer which expected ArFragment
fun renderAnchorNode(sceneView: ArSceneView, anchor: com.google.ar.core.Anchor, data: AnchorData, renderer: ARModelRenderer) {
    val anchorNode = com.google.ar.sceneform.AnchorNode(anchor)
    anchorNode.setParent(sceneView.scene)
    
    com.google.ar.sceneform.rendering.ViewRenderable.builder()
        .setView(sceneView.context, com.phantomcrowd.R.layout.view_ar_label)
        .build()
        .thenAccept { renderable ->
             val view = renderable.view
             val tv = view.findViewById<android.widget.TextView>(com.phantomcrowd.R.id.ar_text_message)
             tv.text = data.messageText
             
             // Simple color
             val color = when(data.category) {
                 "Safety" -> android.graphics.Color.RED
                 "Facility" -> android.graphics.Color.YELLOW
                 else -> android.graphics.Color.CYAN
             }
             view.setBackgroundColor(color)

             val node = com.google.ar.sceneform.Node()
             node.renderable = renderable
             node.setParent(anchorNode)
             node.localPosition = com.google.ar.sceneform.math.Vector3(0f, 1.5f, 0f) // Floating high
        }
        .exceptionally { 
            it.printStackTrace()
            null
        }
}
