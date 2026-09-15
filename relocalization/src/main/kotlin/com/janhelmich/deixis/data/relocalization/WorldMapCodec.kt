package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Reads and writes a [WorldMap] as a compact binary blob.
 *
 * The bulk of a map is descriptor bytes and float coordinates, so a binary layout keeps files
 * small and load fast. The format is versioned ([WorldMap.FORMAT_VERSION]); an unknown version
 * is rejected rather than misread.
 *
 * Layout (big-endian, [DataOutputStream] defaults):
 *   magic "DXMAP" · version:int · id · label · createdAt:long
 *   markerCount:int · { placementId · label · hasDevice:bool [· deviceId] · 16 floats }
 *   keyframeCount:int · { id:int · 16 floats pose · 3 floats gravity · featureBlock }
 *   featureBlock: count:int · descriptorBytes:int · keypoints[count*2 floats]
 *                 · worldPoints[count*3 floats] · descriptors[count*descriptorBytes bytes]
 */
object WorldMapCodec {

    private const val MAGIC = "DXMAP"

    fun write(map: WorldMap, out: OutputStream) {
        val w = DataOutputStream(out.buffered())
        w.writeUTF(MAGIC)
        w.writeInt(WorldMap.FORMAT_VERSION)
        w.writeUTF(map.id)
        w.writeUTF(map.label)
        w.writeLong(map.createdAtMillis)

        w.writeInt(map.markers.size)
        for (m in map.markers) {
            w.writeUTF(m.placementId)
            w.writeUTF(m.label)
            w.writeBoolean(m.deviceId != null)
            m.deviceId?.let(w::writeUTF)
            w.writeMat4(m.pose)
        }

        w.writeInt(map.keyframes.size)
        for (k in map.keyframes) {
            w.writeInt(k.id)
            w.writeMat4(k.cameraPose)
            w.writeFloat3(k.gravity)
            val f = k.features
            w.writeInt(f.count)
            w.writeInt(f.descriptorBytes)
            for (v in f.keypoints) w.writeFloat(v)
            for (p in f.worldPoints) w.writeFloat3(p)
            w.write(f.descriptors)
        }
        w.flush()
    }

    /** @throws IllegalArgumentException if the stream is not a readable map of this format. */
    fun read(input: InputStream): WorldMap = try {
        readUnchecked(input)
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: Exception) {
        // A truncated or foreign blob surfaces as EOFException/IOException from the stream;
        // present it as one typed failure so callers catch a single thing.
        throw IllegalArgumentException("not a readable Deixis map file", e)
    }

    private fun readUnchecked(input: InputStream): WorldMap {
        val r = DataInputStream(input.buffered())
        require(r.readUTF() == MAGIC) { "not a Deixis map file" }
        val version = r.readInt()
        require(version == WorldMap.FORMAT_VERSION) {
            "unsupported map format $version (this build reads ${WorldMap.FORMAT_VERSION})"
        }
        val id = r.readUTF()
        val label = r.readUTF()
        val createdAt = r.readLong()

        val markers = List(r.readInt()) {
            val placementId = r.readUTF()
            val markerLabel = r.readUTF()
            val deviceId = if (r.readBoolean()) r.readUTF() else null
            MarkerPose(placementId, markerLabel, deviceId, r.readMat4())
        }

        val keyframes = List(r.readInt()) {
            val kfId = r.readInt()
            val pose = r.readMat4()
            val gravity = r.readFloat3()
            val count = r.readInt()
            val descriptorBytes = r.readInt()
            val keypoints = FloatArray(count * 2) { r.readFloat() }
            val worldPoints = List(count) { r.readFloat3() }
            val descriptors = ByteArray(count * descriptorBytes).also(r::readFully)
            Keyframe(kfId, pose, gravity, ObservedFeatures(count, keypoints, worldPoints, descriptors, descriptorBytes))
        }
        return WorldMap(id, label, createdAt, keyframes, markers)
    }

    // kotlin-math Mat4 is column-major; write the 16 floats in that order and read them back.
    private fun DataOutputStream.writeMat4(m: Mat4) { for (v in m.toFloatArray()) writeFloat(v) }
    private fun DataInputStream.readMat4(): Mat4 = Mat4.of(*FloatArray(16) { readFloat() })
    private fun DataOutputStream.writeFloat3(p: Float3) { writeFloat(p.x); writeFloat(p.y); writeFloat(p.z) }
    private fun DataInputStream.readFloat3(): Float3 = Float3(readFloat(), readFloat(), readFloat())
}
