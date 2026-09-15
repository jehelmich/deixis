package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Mat4

data class KeyframeSelectorConfig(
    /** A viewpoint counts as new once the camera has moved this far from every stored keyframe… */
    val minTranslationMeters: Float = 0.25f,
    /** …or turned this much from the nearest one. */
    val minRotationDegrees: Float = 15f,
    /** Frames with too little texture make thin, unreliable keyframes. */
    val minFeatures: Int = 150,
    val maxKeyframes: Int = 60,
)

/**
 * Decides when the capture loop should store a keyframe: only from a viewpoint the map does not
 * already have. This is what makes the map multi-viewpoint — each physical feature ends up
 * described from several angles, so a later query is always within the descriptor's viewpoint
 * tolerance of *some* stored view (docs/relocalization-testing.md measured that tolerance at
 * roughly ±30° for XFeat, less for ORB). Pure, so the policy is unit-tested without a camera.
 */
class KeyframeSelector(private val config: KeyframeSelectorConfig = KeyframeSelectorConfig()) {

    private val poses = ArrayList<Mat4>()
    val count: Int get() = poses.size

    fun shouldCapture(cameraPose: Mat4, featureCount: Int): Boolean {
        if (featureCount < config.minFeatures || poses.size >= config.maxKeyframes) return false
        return poses.all { kept ->
            val d = poseDelta(cameraPose, kept)
            d.translationMeters >= config.minTranslationMeters || d.rotationDegrees >= config.minRotationDegrees
        }
    }

    fun recordCaptured(cameraPose: Mat4) { poses += cameraPose }

    fun reset() = poses.clear()
}
