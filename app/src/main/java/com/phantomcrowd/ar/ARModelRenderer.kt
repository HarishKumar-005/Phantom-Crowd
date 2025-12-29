package com.phantomcrowd.ar

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.ar.core.Anchor
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.phantomcrowd.data.AnchorData
import java.util.concurrent.CompletableFuture
import com.phantomcrowd.R // Assuming R is generated after build

class ARModelRenderer(private val context: Context) {

    fun renderAnchor(
        arFragment: ArFragment,
        anchor: Anchor,
        anchorData: AnchorData
    ) {
        val anchorNode = AnchorNode(anchor)
        anchorNode.setParent(arFragment.arSceneView.scene)

        ViewRenderable.builder()
            .setView(context, R.layout.view_ar_label) // We need to create this layout
            .build()
            .thenAccept { renderable ->
                val node = validNode(renderable, anchorData)
                node.setParent(anchorNode)
                node.localPosition = com.google.ar.sceneform.math.Vector3(0f, 0.5f, 0f) // float slightly above
                
                // Make it face the camera
                // sceneform-ux 1.23.0 might have automatic methods or we can use a billboard node
                // For MVP, just simple text is fine.
            }
            .exceptionally {
                it.printStackTrace()
                null
            }
    }
    
    private fun validNode(renderable: ViewRenderable, data: AnchorData): Node {
        val node = Node()
        node.renderable = renderable
        
        val view = renderable.view
        val textView = view.findViewById<TextView>(R.id.ar_text_message)
        val container = view.findViewById<View>(R.id.ar_label_container)
        
        textView.text = data.messageText
        
        // Color coding
        val color = when(data.category.lowercase()) {
            "safety" -> Color.RED
            "facility" -> Color.YELLOW
            else -> Color.CYAN
        }
        container.setBackgroundColor(color)
        
        return node
    }
}
