package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import dev.romainguy.kotlin.math.rotation
import dev.romainguy.kotlin.math.translation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorldMapCodecTest {

    @Test
    fun `a map survives a write then read unchanged`() {
        val map = sampleMap()
        val bytes = ByteArrayOutputStream().also { WorldMapCodec.write(map, it) }.toByteArray()
        val back = WorldMapCodec.read(ByteArrayInputStream(bytes))

        assertEquals(map.id, back.id)
        assertEquals(map.label, back.label)
        assertEquals(map.createdAtMillis, back.createdAtMillis)

        assertEquals(map.markers.size, back.markers.size)
        map.markers.zip(back.markers).forEach { (a, b) ->
            assertEquals(a.placementId, b.placementId)
            assertEquals(a.deviceId, b.deviceId)
            assertContentEquals(a.pose.toFloatArray(), b.pose.toFloatArray())
        }

        assertEquals(map.keyframes.size, back.keyframes.size)
        map.keyframes.zip(back.keyframes).forEach { (a, b) ->
            assertEquals(a.id, b.id)
            assertContentEquals(a.cameraPose.toFloatArray(), b.cameraPose.toFloatArray())
            assertEquals(a.features.count, b.features.count)
            assertContentEquals(a.features.keypoints, b.features.keypoints)
            assertContentEquals(a.features.descriptors, b.features.descriptors)
            assertEquals(a.features.worldPoints, b.features.worldPoints)
        }
    }

    @Test
    fun `a foreign or truncated blob is rejected, not misread`() {
        assertFailsWith<IllegalArgumentException> {
            WorldMapCodec.read(ByteArrayInputStream("not a map".toByteArray()))
        }
        // A real map cut short must also fail cleanly rather than return a partial map.
        val full = ByteArrayOutputStream().also { WorldMapCodec.write(sampleMap(), it) }.toByteArray()
        assertFailsWith<IllegalArgumentException> {
            WorldMapCodec.read(ByteArrayInputStream(full.copyOf(full.size / 2)))
        }
    }

    private fun sampleMap(seed: Int = 7): WorldMap {
        val rnd = Random(seed)
        fun keyframe(id: Int, n: Int): Keyframe {
            val desc = ByteArray(n * 32).also(rnd::nextBytes)
            return Keyframe(
                id = id,
                cameraPose = translation(Float3(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat())) *
                    rotation(Float3(0f, 1f, 0f), rnd.nextFloat() * 180f),
                gravity = Float3(0f, -1f, 0f),
                features = ObservedFeatures(
                    count = n,
                    keypoints = FloatArray(n * 2) { rnd.nextFloat() * 640f },
                    worldPoints = List(n) { Float3(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) },
                    descriptors = desc,
                ),
            )
        }
        return WorldMap(
            id = "living-room",
            label = "Living room",
            createdAtMillis = 1_789_000_000_000L,
            keyframes = listOf(keyframe(0, 40), keyframe(1, 25)),
            markers = listOf(
                MarkerPose("p1", "Desk lamp", "light.desk", translation(Float3(1f, 0f, -1f))),
                MarkerPose("p2", "Fan", null, Mat4.identity()),
            ),
        )
    }
}
