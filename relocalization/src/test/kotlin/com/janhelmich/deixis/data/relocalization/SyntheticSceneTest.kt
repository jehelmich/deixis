package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import dev.romainguy.kotlin.math.rotation
import dev.romainguy.kotlin.math.translation
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ground-truth checks with no sensor: a known 3D scene projected into known camera poses. This
 * is layer 1 of the testing strategy (docs/relocalization-testing.md) — it pins the projection
 * geometry exactly, and manufactures the correspondences the future PnP relocalizer will be
 * scored against.
 */
class SyntheticSceneTest {

    private val intrinsics = CameraIntrinsics(fx = 552f, fy = 552f, cx = 320f, cy = 240f)

    @Test
    fun `project is the exact inverse of unproject`() {
        for (px in listOf(10f, 160f, 320f, 500f, 630f)) {
            for (py in listOf(10f, 120f, 240f, 360f, 470f)) {
                val cam = intrinsics.unproject(px, py, depthMeters = 3.2f)
                val back = intrinsics.project(cam)!!
                assertTrue(abs(back.x - px) < 1e-2f && abs(back.y - py) < 1e-2f, "($px,$py) -> $back")
            }
        }
    }

    @Test
    fun `a point projected into a pose back-projects to the same world point`() {
        val cameraPose = translation(Float3(0.5f, 1.2f, -0.3f)) * rotation(Float3(0f, 1f, 0f), 25f)
        val world = Float3(-0.4f, 0.1f, -2.5f)
        val pixel = intrinsics.worldToPixel(world, cameraPose)!!
        // Recover it: the depth is the distance along the camera's forward axis.
        val camPoint = dev.romainguy.kotlin.math.inverse(cameraPose).transformPoint(world)
        val recovered = cameraPose.transformPoint(intrinsics.unproject(pixel.x, pixel.y, -camPoint.z))
        assertClose(world, recovered)
    }

    @Test
    fun `a full synthetic relocalization recovers the ground-truth transform`() {
        // A random-but-fixed cloud of 3D points in the map frame.
        val rnd = Random(11)
        val mapPoints = List(60) {
            Float3(rnd.nextFloat() * 4 - 2, rnd.nextFloat() * 2 - 1, -1f - rnd.nextFloat() * 4)
        }
        val mapCamera = translation(Float3(0f, 0f, 0f))                       // built the map from here
        val sessionFromMapTruth = translation(Float3(2f, 0.5f, -1f)) *
            rotation(Float3(0f, 1f, 0f), 40f)                                 // the unknown to recover
        val liveCameraInMap = translation(Float3(0.3f, 0f, 0.4f)) * rotation(Float3(0f, 1f, 0f), 8f)
        val liveCameraInSession = sessionFromMapTruth * liveCameraInMap

        // Correspondences the matcher would produce: each map point seen by the live camera.
        val visible = mapPoints.filter {
            intrinsics.worldToPixel(it, mapCamera) != null && intrinsics.worldToPixel(it, liveCameraInMap) != null
        }
        assertTrue(visible.size >= 20, "scene should have plenty of shared points, had ${visible.size}")

        // `solvePnP` would return liveCameraInMap from those correspondences; feed the tested
        // transform step to confirm the map→session recovery a real run depends on.
        val recovered = relocalizationTransform(cameraInMap = liveCameraInMap, cameraInSession = liveCameraInSession)
        val marker = MarkerPose("m", "Lamp", "light.desk", translation(Float3(-1f, 0f, -2f)))
        assertClose((sessionFromMapTruth * marker.pose).position, marker.inSession(recovered).position)
    }

    private val Mat4.position get() = Float3(w.x, w.y, w.z)
    private fun assertClose(a: Float3, b: Float3) =
        assertTrue(abs(a.x - b.x) < 1e-3f && abs(a.y - b.y) < 1e-3f && abs(a.z - b.z) < 1e-3f, "$a vs $b")
}
