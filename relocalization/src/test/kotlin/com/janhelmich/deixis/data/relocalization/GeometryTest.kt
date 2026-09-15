package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import dev.romainguy.kotlin.math.rotation
import dev.romainguy.kotlin.math.translation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeometryTest {

    private val intrinsics = CameraIntrinsics(fx = 500f, fy = 500f, cx = 320f, cy = 240f)

    @Test
    fun `a pixel at the principal point unprojects straight down camera -Z`() {
        val p = intrinsics.unproject(320f, 240f, 2f)
        assertClose(Float3(0f, 0f, -2f), p)
    }

    @Test
    fun `off-centre pixels unproject with the right sign`() {
        // Right of centre → +X; below centre (larger pixel y) → -Y.
        val p = intrinsics.unproject(420f, 340f, 5f)
        assertEquals((100f / 500f) * 5f, p.x, EPS)
        assertEquals(-(100f / 500f) * 5f, p.y, EPS)
        assertEquals(-5f, p.z, EPS)
    }

    @Test
    fun `back-projection then camera pose lands the point in the map frame`() {
        // Camera sits at (1,0,0) in the map, unrotated. A point 2 m ahead is at (1,0,-2).
        val cameraPose = translation(Float3(1f, 0f, 0f))
        val world = cameraPose.transformPoint(intrinsics.unproject(320f, 240f, 2f))
        assertClose(Float3(1f, 0f, -2f), world)
    }

    @Test
    fun `relocalization transform maps a marker from the old frame into the new session`() {
        // The map was built in a frame rotated 90 deg and shifted from this session's frame.
        val sessionFromMapTruth = translation(Float3(3f, 0f, -1f)) * rotation(Float3(0f, 1f, 0f), 90f)

        // The live camera, seen in both frames.
        val cameraInMap = translation(Float3(0.2f, 0f, 0.5f)) * rotation(Float3(0f, 1f, 0f), 10f)
        val cameraInSession = sessionFromMapTruth * cameraInMap

        val recovered = relocalizationTransform(cameraInMap, cameraInSession)

        // A marker stored in the map frame should come back where the ground truth puts it.
        val marker = MarkerPose("m", "Lamp", "light.desk", translation(Float3(-1f, 0f, -2f)))
        assertClose(
            (sessionFromMapTruth * marker.pose).position,
            marker.inSession(recovered).position,
        )
    }

    private val Mat4.position get() = Float3(w.x, w.y, w.z)

    private fun assertClose(expected: Float3, actual: Float3) {
        assertTrue(
            kotlin.math.abs(expected.x - actual.x) < EPS &&
                kotlin.math.abs(expected.y - actual.y) < EPS &&
                kotlin.math.abs(expected.z - actual.z) < EPS,
            "expected ~$expected, got $actual",
        )
    }

    private companion object { const val EPS = 1e-4f }
}
