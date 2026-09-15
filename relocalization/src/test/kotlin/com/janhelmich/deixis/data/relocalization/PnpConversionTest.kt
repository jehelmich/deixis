package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import dev.romainguy.kotlin.math.inverse
import dev.romainguy.kotlin.math.normalize
import dev.romainguy.kotlin.math.rotation
import dev.romainguy.kotlin.math.translation
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins the OpenCV→ARCore convention flip in [cameraInMapFromPnp] with no OpenCV: pick a known
 * camera pose, derive the exact `R`/`t` that `solvePnP` would return for it, and check the
 * function reconstructs the pose. This is the one step where a wrong sign silently ruins
 * relocalization, so it is tested against ground truth directly.
 */
class PnpConversionTest {

    // arc-camera → cv-camera: X_cv = F · X_arc, F = diag(1,-1,-1).
    private val F = arrayOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, -1f, 0f), floatArrayOf(0f, 0f, -1f))

    @Test fun `identity camera at the origin`() = roundTrip(Mat4.identity())

    @Test fun `translated only`() = roundTrip(translation(Float3(1.5f, -0.4f, 2.2f)))

    @Test fun `rotated and translated`() =
        roundTrip(translation(Float3(0.3f, 1.1f, -0.7f)) * rotation(normalize(Float3(0.2f, 1f, 0.1f)), 37f))

    @Test fun `yaw about vertical, the common case`() =
        roundTrip(translation(Float3(-2f, 0f, 1f)) * rotation(Float3(0f, 1f, 0f), 115f))

    /** Given a target arc-camera→map pose, synthesise solvePnP's R,t and check the inverse map. */
    private fun roundTrip(cameraInMap: Mat4) {
        // map → arc-camera
        val mapToArc = inverse(cameraInMap)
        // map → cv-camera = F · (map → arc-camera); R is its rotation, t its translation.
        val a = mapToArc
        val rotArc = arrayOf(
            floatArrayOf(a.x.x, a.y.x, a.z.x),
            floatArrayOf(a.x.y, a.y.y, a.z.y),
            floatArrayOf(a.x.z, a.y.z, a.z.z),
        )
        val transArc = Float3(a.w.x, a.w.y, a.w.z)
        val R = matMul(F, rotArc)
        val t = matVec(F, transArc)
        val rRowMajor = floatArrayOf(R[0][0], R[0][1], R[0][2], R[1][0], R[1][1], R[1][2], R[2][0], R[2][1], R[2][2])

        val recovered = cameraInMapFromPnp(rRowMajor, t)
        assertMat4Close(cameraInMap, recovered)
    }

    private fun matMul(a: Array<FloatArray>, b: Array<FloatArray>) = Array(3) { i ->
        FloatArray(3) { j -> (0..2).sumOf { k -> (a[i][k] * b[k][j]).toDouble() }.toFloat() }
    }
    private fun matVec(a: Array<FloatArray>, v: Float3): Float3 {
        val x = floatArrayOf(v.x, v.y, v.z)
        val o = FloatArray(3) { i -> (0..2).sumOf { k -> (a[i][k] * x[k]).toDouble() }.toFloat() }
        return Float3(o[0], o[1], o[2])
    }
    private fun assertMat4Close(a: Mat4, b: Mat4) {
        a.toFloatArray().zip(b.toFloatArray()).forEach { (x, y) ->
            assertTrue(abs(x - y) < 1e-4f, "expected\n$a\ngot\n$b")
        }
    }
}
