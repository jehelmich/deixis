package com.janhelmich.deixis.bench

/** Loads desktop OpenCV's native library once for the whole bench JVM. */
object DesktopOpenCv {
    @Volatile private var loaded = false

    @Synchronized fun ensureLoaded() {
        if (loaded) return
        nu.pattern.OpenCV.loadLocally()
        loaded = true
    }
}
