package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Mat4

/** The outcome of trying to find a saved map in the current camera view. */
sealed interface RelocalizationResult {
    /**
     * The map was recognised. [sessionFromMap] carries any [MarkerPose] into the running
     * session ([MarkerPose.inSession]); [cameraInMap] is the live camera's recovered pose in the
     * map frame (what an accuracy benchmark scores against); [inlierCount] is how many feature
     * matches agreed on the pose, a direct confidence signal.
     */
    data class Located(
        val sessionFromMap: Mat4,
        val cameraInMap: Mat4,
        val inlierCount: Int,
    ) : RelocalizationResult

    /** Not enough agreeing matches this frame — keep looking as the user pans the room. */
    data object NotFound : RelocalizationResult
}

/**
 * Recognises a previously saved [WorldMap] in the live camera feed and returns the transform
 * that rebuilds its markers in the current session — the offline counterpart to resolving a
 * Cloud Anchor.
 *
 * A relocalizer is fed one live frame at a time (its already-extracted [ExtractedFeatures], the
 * camera intrinsics, and where ARCore puts the camera in the current session). It matches the
 * frame's descriptors against the map's, and when enough matches agree on a single rigid pose
 * (`solvePnPRansac`) it reports [RelocalizationResult.Located]. Live depth is not needed: the 3D
 * side of each correspondence comes from the stored map, the 2D side from the live pixels.
 *
 * See docs/persistence.md for the matching and PnP details, and docs/relocalization-testing.md
 * for how it is measured.
 */
interface Relocalizer {
    fun relocalize(
        map: WorldMap,
        liveFeatures: ExtractedFeatures,
        intrinsics: CameraIntrinsics,
        cameraInSession: Mat4,
    ): RelocalizationResult
}
