package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import dev.romainguy.kotlin.math.translation
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileMapStoreTest {

    private val dir = Files.createTempDirectory("deixis-maps").toFile()
    private val store = FileMapStore(dir)

    @AfterTest fun cleanup() { dir.deleteRecursively() }

    private fun map(id: String, markers: Int) = WorldMap(
        id = id, label = id.replaceFirstChar(Char::uppercase), createdAtMillis = markers.toLong(),
        keyframes = listOf(
            Keyframe(0, Mat4.identity(), Float3(0f, -1f, 0f),
                ObservedFeatures(1, floatArrayOf(1f, 2f), listOf(Float3(0f, 0f, -1f)), ByteArray(32))),
        ),
        markers = List(markers) { MarkerPose("p$it", "M$it", null, translation(Float3(it.toFloat(), 0f, 0f))) },
    )

    @Test
    fun `save then load returns the same map`() {
        store.save(map("kitchen", markers = 3))
        val back = store.load("kitchen")!!
        assertEquals("Kitchen", back.label)
        assertEquals(3, back.markers.size)
    }

    @Test
    fun `list summarises every saved map, newest first, without loading payloads`() {
        store.save(map("a", markers = 1))
        store.save(map("b", markers = 2))
        val summaries = store.list()
        assertEquals(listOf("b", "a"), summaries.map { it.id }) // createdAt 2 then 1
        assertEquals(2, summaries.first().markerCount)
        assertTrue(summaries.first().sizeBytes > 0)
    }

    @Test
    fun `delete removes a map`() {
        store.save(map("gone", markers = 1))
        assertTrue(store.delete("gone"))
        assertNull(store.load("gone"))
    }

    @Test
    fun `an id with awkward characters is stored safely and still round-trips`() {
        store.save(map("../weird id", markers = 1))
        assertEquals(1, store.load("../weird id")!!.markers.size)
        assertEquals(1, store.list().size)
    }
}
