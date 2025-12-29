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
    
    // Managers
    val arCoreManager = remember { ARCoreManager(context) }
    val geospatialManager = remember { GeospatialAnchorManager() }
    val renderer = remember { ARModelRenderer(context) }
    
    // State to track added anchors to avoid duplicates
    val renderedAnchorIds = remember { mutableSetOf<String>() }
    var statusText by remember { mutableStateOf("Initializing AR...") }

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
                ArSceneView(ctx).apply {
                    arSceneView = this
                    // Manually configure session for Geospatial
                    // Note: ArSceneView usually creates its own session on resume if null.
                    // We want to intercept or configure it.
                    // Ideally check if session exists, if not create and set.
                    
                    this.scene.addOnUpdateListener { _ ->
                        val session = this.session ?: return@addOnUpdateListener
                        val earth = session.earth
                        
                        if (earth?.trackingState == TrackingState.TRACKING) {
                            val pose = earth.cameraGeospatialPose
                            statusText = "GPS: ${pose.latitude}, ${pose.longitude}\nAcc: ${pose.horizontalAccuracy}m"
                            
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
                                        // Creating a fake ArFragment reference is hard here since we aren't using one.
                                        // We need to adapt the renderer to work with Scene directly.
                                        // Refactoring renderer slightly inside the usage here or passing scene.
                                        
                                        // We'll reproduce the renderer logic here for simplicity or adapt the class.
                                        // Let's assume we can attach to scene.
                                        renderAnchorNode(this, arAnchor, anchorData, renderer)
                                        renderedAnchorIds.add(anchorData.id)
                                    }
                                }
                            }
                        } else {
                            statusText = "Waiting for Earth tracking... \nState: ${earth?.earthState}"
                        }
                    }
                }
            },
           update = { view ->
               // Ensure session is configured if created by View
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
