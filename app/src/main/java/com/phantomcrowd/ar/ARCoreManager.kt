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
            session?.configure(config)
            return session
        } catch (e: UnavailableException) {
            Log.e("ARCoreManager", "ARCore session creation failed", e)
            return null
        } catch (e: Exception) {
            Log.e("ARCoreManager", "Unexpected exception", e)
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
