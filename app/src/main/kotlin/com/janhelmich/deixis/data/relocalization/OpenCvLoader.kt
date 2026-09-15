package com.janhelmich.deixis.data.relocalization

import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * Loads OpenCV's native library once, before any [OrbFeatureExtractor] is created.
 *
 * Relocalization is optional: if the native library will not load, [isAvailable] stays false and
 * the app runs exactly as before, minus persistence. Callers check it rather than crashing.
 */
object OpenCvLoader {

    @Volatile var isAvailable = false
        private set

    @Synchronized
    fun ensureLoaded(): Boolean {
        if (isAvailable) return true
        isAvailable = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
        if (!isAvailable) Log.w("OpenCvLoader", "OpenCV native library unavailable; relocalization disabled")
        return isAvailable
    }
}
