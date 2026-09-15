package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4

/**
 * Turns 2D [ExtractedFeatures] into a 3D [Keyframe] by giving every feature a position in the
 * map frame: look up each keypoint's metric depth, back-project it into camera space, and carry
 * it into the map frame with the camera pose.
 *
 * Features whose depth is missing or out of range are dropped — a descriptor without a reliable
 * 3D point is useless to `solvePnP` and worse than nothing, since it invites a false match.
 * Pure and deterministic: [depthAt] is a plain function, so tests drive it with a lookup table
 * instead of a live ARCore depth image.
 */
object KeyframeBuilder {

    /** Depth in metres at a pixel, or `null`/non-positive when ARCore has none there. */
    fun interface DepthSource {
        fun depthAt(pixelX: Float, pixelY: Float): Float?
    }

    fun build(
        id: Int,
        features: ExtractedFeatures,
        intrinsics: CameraIntrinsics,
        cameraPose: Mat4,
        gravity: Float3,
        depth: DepthSource,
        minDepthMeters: Float = 0.1f,
        maxDepthMeters: Float = 8f,
    ): Keyframe {
        val keptX = ArrayList<Float>(features.count * 2)
        val worldPoints = ArrayList<Float3>(features.count)
        val descriptors = ByteArrayBuilder(features.count * features.descriptorBytes)

        for (i in 0 until features.count) {
            val px = features.keypoints[i * 2]
            val py = features.keypoints[i * 2 + 1]
            val d = depth.depthAt(px, py) ?: continue
            if (d < minDepthMeters || d > maxDepthMeters) continue
            val world = cameraPose.transformPoint(intrinsics.unproject(px, py, d))
            keptX.add(px); keptX.add(py)
            worldPoints.add(world)
            descriptors.append(features.descriptors, i * features.descriptorBytes, features.descriptorBytes)
        }

        val kept = worldPoints.size
        return Keyframe(
            id = id,
            cameraPose = cameraPose,
            gravity = gravity,
            features = ObservedFeatures(
                count = kept,
                keypoints = keptX.toFloatArray(),
                worldPoints = worldPoints,
                descriptors = descriptors.toByteArray(),
                descriptorBytes = features.descriptorBytes,
            ),
        )
    }

    private class ByteArrayBuilder(capacity: Int) {
        private var buf = ByteArray(capacity)
        private var size = 0
        fun append(src: ByteArray, from: Int, len: Int) {
            if (size + len > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + len))
            System.arraycopy(src, from, buf, size, len)
            size += len
        }
        fun toByteArray(): ByteArray = buf.copyOf(size)
    }
}
