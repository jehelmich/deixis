package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Mat4

/**
 * Accumulates keyframes during a capture session and turns them, plus the markers as placed,
 * into a [WorldMap]. The map frame *is* the capture session's ARCore world frame, so keyframe
 * camera poses and marker anchor poses go in as they are — no conversion — and every marker's
 * position relative to every other one is preserved because they share that frame.
 */
class MapBuilder(private val id: String, private val label: String) {

    private val keyframes = ArrayList<Keyframe>()
    val keyframeCount: Int get() = keyframes.size
    val pointCount: Int get() = keyframes.sumOf { it.points.size }

    fun add(keyframe: Keyframe) { keyframes += keyframe }

    fun nextKeyframeId(): Int = keyframes.size

    /** @param markers each marker's label, device and anchor pose in the capture session frame. */
    fun build(markers: List<MarkerPose>, createdAtMillis: Long): WorldMap =
        WorldMap(id, label, createdAtMillis, keyframes.toList(), markers)

    fun reset() = keyframes.clear()
}

/** Convenience for the app: a marker as placed, straight from its anchor's session pose. */
fun markerPose(placementId: String, label: String, deviceId: String?, anchorPoseInSession: Mat4) =
    MarkerPose(placementId, label, deviceId, anchorPoseInSession)
