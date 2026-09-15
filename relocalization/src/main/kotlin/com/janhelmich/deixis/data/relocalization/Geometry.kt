package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import dev.romainguy.kotlin.math.inverse

/** Pinhole camera intrinsics in pixels, from `Camera.getImageIntrinsics()`. */
data class CameraIntrinsics(
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float,
) {
    /**
     * Back-project a pixel with a known metric depth to a point in **camera space**.
     *
     * ARCore's camera looks down its local −Z, with +X right and +Y up, so a positive depth in
     * front of the camera lands at negative Z. Image y grows downward, hence the sign flip.
     */
    fun unproject(pixelX: Float, pixelY: Float, depthMeters: Float): Float3 = Float3(
        x = (pixelX - cx) / fx * depthMeters,
        y = -(pixelY - cy) / fy * depthMeters,
        z = -depthMeters,
    )
}

/** Camera-space point → world using the camera→world transform. */
fun Mat4.transformPoint(point: Float3): Float3 {
    val v = this * dev.romainguy.kotlin.math.Float4(point, 1f)
    return Float3(v.x, v.y, v.z)
}

/**
 * Project a **camera-space** point back to a pixel — the exact inverse of [CameraIntrinsics.unproject].
 * Returns `null` for points at or behind the image plane (camera-space z ≥ 0). Its main use is
 * testing: with a known 3D scene and known poses it manufactures ground-truth 2D↔3D
 * correspondences with no sensor at all, so the matcher and `solvePnP` can be measured against
 * an answer that is exactly right. See docs/relocalization-testing.md.
 */
fun CameraIntrinsics.project(cameraSpacePoint: Float3): Pixel? {
    val (x, y, z) = cameraSpacePoint
    if (z >= 0f) return null
    return Pixel(cx - fx * x / z, cy + fy * y / z)
}

/** A world point projected into a camera at [cameraPose] (camera→world), or `null` if behind it. */
fun CameraIntrinsics.worldToPixel(world: Float3, cameraPose: Mat4): Pixel? =
    project(inverse(cameraPose).transformPoint(world))

data class Pixel(val x: Float, val y: Float)

/**
 * The heart of relocalization: given where the live camera sits in the **old map** frame (from
 * `solvePnP` against the map) and where ARCore puts that same camera in the **new session**
 * frame, recover the transform that carries anything stored in the map into the new session.
 *
 *     T_session_map = T_session_camera · (T_map_camera)⁻¹
 *
 * Applying it to a [MarkerPose] gives the marker's pose in the running session, ready to become
 * a fresh ARCore anchor. This is exactly what a Cloud Anchor `resolve` does — only the map it
 * matches against lives on the phone.
 */
fun relocalizationTransform(cameraInMap: Mat4, cameraInSession: Mat4): Mat4 =
    cameraInSession * inverse(cameraInMap)

/** A marker's pose brought from the map frame into the current session frame. */
fun MarkerPose.inSession(sessionFromMap: Mat4): Mat4 = sessionFromMap * pose
