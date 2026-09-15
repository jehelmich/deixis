package com.janhelmich.deixis.bench

import com.janhelmich.deixis.data.relocalization.CameraIntrinsics
import com.janhelmich.deixis.data.relocalization.Keyframe
import com.janhelmich.deixis.data.relocalization.MarkerPose
import com.janhelmich.deixis.data.relocalization.ObservedFeatures
import com.janhelmich.deixis.data.relocalization.OrbRelocalizer
import com.janhelmich.deixis.data.relocalization.RelocalizationResult
import com.janhelmich.deixis.data.relocalization.WorldMap
import com.janhelmich.deixis.data.relocalization.inSession
import com.janhelmich.deixis.data.relocalization.worldToPixel
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import dev.romainguy.kotlin.math.length
import dev.romainguy.kotlin.math.rotation
import dev.romainguy.kotlin.math.translation
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Layer 1.5 of the testing strategy: the whole [OrbRelocalizer] — real OpenCV descriptor
 * matching and `solvePnPRansac` — against synthetic ground truth, off-device.
 *
 * A cloud of 3D points each gets a unique random ORB descriptor. A "live" view of the same
 * points reuses those descriptors (so matching is unambiguous) at pixels projected from a known
 * camera pose, mixed with outliers. The relocalizer must recover the map→session transform, and
 * a marker carried through it must land back within a millimetre of the truth — proving the
 * matching plumbing, the PnP call and the OpenCV↔ARCore convention conversion are all correct.
 */
class SyntheticRelocalizationTest {

    private val intrinsics = CameraIntrinsics(fx = 552f, fy = 552f, cx = 320f, cy = 240f)
    private val relocalizer by lazy { OrbRelocalizer(minInliers = 15) }

    @BeforeTest fun setUp() = DesktopOpenCv.ensureLoaded()

    @Test
    fun `relocalizes a synthetic scene and recovers the transform`() {
        val rnd = Random(20260915)
        val n = 120
        val mapPoints = List(n) {
            Float3(rnd.nextFloat() * 4 - 2, rnd.nextFloat() * 3 - 1.5f, -1f - rnd.nextFloat() * 4)
        }
        val descriptors = ByteArray(n * 32).also(rnd::nextBytes)

        // The map was built from a camera at the origin (map frame == that session's world).
        val mapCamera = Mat4.identity()
        val mapKeypoints = FloatArray(n * 2)
        mapPoints.forEachIndexed { i, p ->
            val px = intrinsics.worldToPixel(p, mapCamera)!!
            mapKeypoints[i * 2] = px.x; mapKeypoints[i * 2 + 1] = px.y
        }
        val map = WorldMap(
            id = "synthetic", label = "Synthetic", createdAtMillis = 0,
            keyframes = listOf(Keyframe(0, mapCamera, Float3(0f, -1f, 0f),
                ObservedFeatures(n, mapKeypoints, mapPoints, descriptors))),
            markers = listOf(MarkerPose("m", "Lamp", "light.desk", translation(Float3(-0.6f, 0f, -2.2f)))),
        )

        // A different viewpoint, and the unknown transform between map and this session.
        val sessionFromMapTruth = translation(Float3(1.5f, 0.3f, -0.8f)) * rotation(Float3(0f, 1f, 0f), 35f)
        val liveCameraInMap = translation(Float3(0.4f, 0.1f, 0.6f)) * rotation(Float3(0f, 1f, 0f), 12f)
        val liveCameraInSession = sessionFromMapTruth * liveCameraInMap

        // Live features: matched inliers (same descriptor, projected pixel) + random outliers.
        val liveKp = ArrayList<Float>()
        val liveDesc = ArrayList<Byte>()
        var inliers = 0
        mapPoints.forEachIndexed { i, p ->
            val px = intrinsics.worldToPixel(p, liveCameraInMap) ?: return@forEachIndexed
            if (px.x < 0 || px.x > 640 || px.y < 0 || px.y > 480) return@forEachIndexed
            liveKp.add(px.x); liveKp.add(px.y)
            for (b in 0 until 32) liveDesc.add(descriptors[i * 32 + b])
            inliers++
        }
        repeat(40) { // outliers: random descriptors at random pixels
            liveKp.add(rnd.nextFloat() * 640); liveKp.add(rnd.nextFloat() * 480)
            repeat(32) { liveDesc.add(rnd.nextInt(256).toByte()) }
        }
        assertTrue(inliers >= 30, "scene should share plenty of points, shared $inliers")

        val live = com.janhelmich.deixis.data.relocalization.ExtractedFeatures(
            count = liveKp.size / 2, keypoints = liveKp.toFloatArray(), descriptors = liveDesc.toByteArray(),
        )

        val result = relocalizer.relocalize(map, live, intrinsics, liveCameraInSession)
        val located = assertIs<RelocalizationResult.Located>(result, "should relocalize a clean synthetic scene")
        assertTrue(located.inlierCount >= 30, "expected many inliers, got ${located.inlierCount}")

        // The recovered camera pose (what the RGB-D benchmark scores) matches the truth.
        val camError = length(located.cameraInMap.position - liveCameraInMap.position)
        assertTrue(camError < 0.01f, "recovered camera off by $camError m")

        // The marker, carried through the recovered transform, lands where the truth puts it.
        val marker = map.markers.single()
        val truth = (sessionFromMapTruth * marker.pose).position
        val got = marker.inSession(located.sessionFromMap).position
        val errorMeters = length(truth - got)
        println("Synthetic relocalization: ${located.inlierCount} inliers, marker error ${"%.4f".format(errorMeters)} m")
        assertTrue(errorMeters < 0.01f, "marker off by $errorMeters m (truth $truth, got $got)")
    }

    @Test
    fun `reports NotFound when the live view shares nothing with the map`() {
        val rnd = Random(7)
        val n = 60
        val map = WorldMap(
            id = "a", label = "A", createdAtMillis = 0,
            keyframes = listOf(Keyframe(0, Mat4.identity(), Float3(0f, -1f, 0f),
                ObservedFeatures(n, FloatArray(n * 2) { rnd.nextFloat() * 640 },
                    List(n) { Float3(rnd.nextFloat(), rnd.nextFloat(), -2f) },
                    ByteArray(n * 32).also(rnd::nextBytes)))),
            markers = emptyList(),
        )
        // Completely unrelated live descriptors.
        val live = com.janhelmich.deixis.data.relocalization.ExtractedFeatures(
            count = n, keypoints = FloatArray(n * 2) { rnd.nextFloat() * 640 },
            descriptors = ByteArray(n * 32).also(rnd::nextBytes),
        )
        val result = relocalizer.relocalize(map, live, intrinsics, Mat4.identity())
        assertTrue(result is RelocalizationResult.NotFound, "unrelated view must not fabricate a lock")
    }

    private val Mat4.position get() = Float3(w.x, w.y, w.z)
}
