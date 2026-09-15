package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Mat4

/** The outcome of trying to find a saved map in the current camera view. */
sealed interface RelocalizationResult {
    /**
     * The map was recognised. [sessionFromMap] carries any [MarkerPose] into the running
     * session ([MarkerPose.inSession]); [inlierCount] is how many feature matches agreed on it,
     * a direct confidence signal.
     */
    data class Located(val sessionFromMap: Mat4, val inlierCount: Int) : RelocalizationResult

    /** Not enough agreeing matches this frame — keep looking as the user pans the room. */
    data object NotFound : RelocalizationResult
}

/**
 * Recognises a previously saved [WorldMap] in the live camera feed and returns the transform
 * that rebuilds its markers in the current session — the offline counterpart to resolving a
 * Cloud Anchor.
 *
 * A relocalizer is fed one live frame at a time (its already-extracted [ExtractedFeatures], the
 * per-pixel depth, the camera intrinsics, and where ARCore puts the camera in the current
 * session). It matches the frame's descriptors against the map, and when enough matches agree
 * on a single rigid pose it reports [RelocalizationResult.Located].
 *
 * See docs/persistence.md for the matching and `solvePnP` details. The OpenCV implementation
 * lands in the next commit on this branch; the capture side and this seam come first.
 */
interface Relocalizer {
    fun relocalize(
        map: WorldMap,
        liveFeatures: ExtractedFeatures,
        liveKeypointWorld: KeyframeBuilder.DepthSource,
        intrinsics: CameraIntrinsics,
        cameraInSession: Mat4,
    ): RelocalizationResult
}
