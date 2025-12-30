package com.phantomcrowd.ar

import android.content.Context
import android.util.Log
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableException

class ARCoreManager(private val context: Context) {

    var session: Session? = null

    fun createSession(): Session? {
        try {
            session = Session(context)
            val config = Config(session)
            // Enable Geospatial mode
            config.geospatialMode = Config.GeospatialMode.ENABLED
            config.depthMode = Config.DepthMode.DISABLED
            config.focusMode = Config.FocusMode.AUTO
            config.cloudAnchorMode = Config.CloudAnchorMode.ENABLED
            session?.configure(config)
            
            com.phantomcrowd.utils.Constants.TAG_ARCORE.let { tag ->
                 Log.d(tag, "Session created and configured")
            }
            
            return session
        } catch (e: UnavailableException) {
            Log.e(com.phantomcrowd.utils.Constants.TAG_ARCORE, "ARCore session creation failed", e)
            return null
        } catch (e: Exception) {
            Log.e(com.phantomcrowd.utils.Constants.TAG_ARCORE, "Unexpected exception during AR session creation", e)
            return null
        }
    }

    fun resume() {
        try {
            session?.resume()
        } catch (e: Exception) {
            Log.e("ARCoreManager", "Failed to resume session", e)
        }
    }

    fun pause() {
        session?.pause()
    }

    fun destroy() {
        session?.close()
        session = null
    }
}
