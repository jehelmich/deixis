package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.translation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyframeBuilderTest {

    private val intrinsics = CameraIntrinsics(500f, 500f, 320f, 240f)

    private fun features(vararg xy: Float): ExtractedFeatures {
        val count = xy.size / 2
        return ExtractedFeatures(count, xy, ByteArray(count * 32) { it.toByte() })
    }

    @Test
    fun `features without valid depth are dropped, and descriptors stay aligned`() {
        val f = features(320f, 240f, /*no depth*/ 100f, 100f, /*too far*/ 400f, 400f)
        val depths = mapOf(320f to 2f, 400f to 20f) // 100,100 missing; 400,400 out of range
        val kf = KeyframeBuilder.build(
            id = 0, features = f, intrinsics = intrinsics,
            cameraPose = translation(Float3(0f, 0f, 0f)), gravity = Float3(0f, -1f, 0f),
            depth = { x, _ -> depths[x] }, maxDepthMeters = 8f,
        )
        assertEquals(1, kf.features.count)
        assertEquals(1, kf.features.worldPoints.size)
        // The kept feature is the first one; its descriptor row is bytes 0..31.
        assertEquals(0.toByte(), kf.features.descriptors[0])
        assertEquals(32, kf.features.descriptors.size)
    }

    @Test
    fun `kept features are back-projected into the map frame`() {
        val kf = KeyframeBuilder.build(
            id = 1, features = features(320f, 240f), intrinsics = intrinsics,
            cameraPose = translation(Float3(5f, 0f, 0f)), gravity = Float3(0f, -1f, 0f),
            depth = { _, _ -> 3f },
        )
        val p = kf.features.worldPoints.single()
        assertTrue(kotlin.math.abs(p.x - 5f) < 1e-4f && kotlin.math.abs(p.z + 3f) < 1e-4f, "got $p")
    }
}
