package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4

/**
 * An offline visual map of one area — the on-device equivalent of what ARCore Cloud Anchors
 * keep on Google's servers, minus the cloud.
 *
 * A map is a set of [Keyframe]s captured as the user looks around while placing devices. Each
 * keyframe pins ORB feature descriptors to 3D points expressed in the map's own coordinate
 * frame (the ARCore world frame of the session that first created the map). On a later run a
 * live frame's features are matched against these, `solvePnP` recovers where the live camera
 * sits in the map frame, and every marker — stored as [MarkerPose] in the same frame — is
 * rebuilt at the corresponding place in the new session. See docs/persistence.md.
 *
 * The descriptors, not the images, are what persist: like Cloud Anchors, the map is a sparse
 * point cloud with appearance, never a photo of the room.
 */
data class WorldMap(
    val id: String,
    val label: String,
    val createdAtMillis: Long,
    val keyframes: List<Keyframe>,
    val markers: List<MarkerPose>,
) {
    /** Every 3D map point across all keyframes, for a matcher that ignores keyframe boundaries. */
    val pointCount: Int get() = keyframes.sumOf { it.points.size }

    companion object {
        /** Bump when [WorldMapCodec]'s on-disk layout changes incompatibly. */
        const val FORMAT_VERSION = 1
    }
}

/**
 * One captured viewpoint: the camera pose it was taken from, and a bundle of features each
 * carrying its 2D pixel location, its 3D position in the map frame, and its ORB descriptor.
 *
 * The three per-feature lists are parallel and equal length; [ObservedFeatures] keeps them
 * together so nothing can index one against another by mistake.
 */
data class Keyframe(
    val id: Int,
    /** Camera→map transform: where this shot was taken, in the map's coordinate frame. */
    val cameraPose: Mat4,
    /** Gravity (the map frame's down) at capture, for gating matches by orientation later. */
    val gravity: Float3,
    val features: ObservedFeatures,
) {
    val points: List<Float3> get() = features.worldPoints
}

/**
 * The parallel arrays of one keyframe's features. [descriptors] is row-major: [count] rows of
 * [descriptorBytes] bytes, an ORB binary descriptor per row.
 */
data class ObservedFeatures(
    val count: Int,
    /** Pixel coordinates in the captured image, `count` pairs of (x, y). */
    val keypoints: FloatArray,
    /** 3D positions in the map frame, one per keypoint. */
    val worldPoints: List<Float3>,
    /** `count * descriptorBytes` bytes, row-major. */
    val descriptors: ByteArray,
    val descriptorBytes: Int = ORB_DESCRIPTOR_BYTES,
) {
    init {
        require(keypoints.size == count * 2) { "expected ${count * 2} keypoint coords, got ${keypoints.size}" }
        require(worldPoints.size == count) { "expected $count world points, got ${worldPoints.size}" }
        require(descriptors.size == count * descriptorBytes) {
            "expected ${count * descriptorBytes} descriptor bytes, got ${descriptors.size}"
        }
    }

    // Value-based equality is not meaningful for these large arrays; identity is what callers want.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)

    companion object {
        /** ORB descriptors are 256-bit → 32 bytes. */
        const val ORB_DESCRIPTOR_BYTES = 32
    }
}

/** A placed marker's pose in the map frame, carried alongside the visual data it is anchored to. */
data class MarkerPose(
    val placementId: String,
    val label: String,
    val deviceId: String?,
    /** Marker→map transform. */
    val pose: Mat4,
)
