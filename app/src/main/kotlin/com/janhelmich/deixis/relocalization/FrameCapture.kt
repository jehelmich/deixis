package com.janhelmich.deixis.relocalization

import android.media.Image
import com.google.ar.core.Frame
import com.google.ar.core.exceptions.DeadlineExceededException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.ResourceExhaustedException
import com.janhelmich.deixis.data.relocalization.KeyframeBuilder
import com.janhelmich.deixis.data.relocalization.CameraIntrinsics as DeixisIntrinsics
import dev.romainguy.kotlin.math.Mat4

/**
 * One ARCore frame, copied out into plain arrays so it can leave the render thread. Cheap to
 * take (a couple of memcpys); everything expensive happens later on a worker.
 */
class CapturedFrame(
    val gray: ByteArray,
    val width: Int,
    val height: Int,
    val intrinsics: DeixisIntrinsics,
    /** Camera → session, ARCore convention. */
    val cameraPose: Mat4,
    /** Metres at a CPU-image pixel, or `null` where ARCore has no depth. */
    val depth: KeyframeBuilder.DepthSource,
    val hasDepth: Boolean,
)

object FrameCapture {

    /**
     * Grab the CPU image (as luminance), its intrinsics, the camera pose and, if available, the
     * depth image. Returns `null` when ARCore has no image for this frame yet — normal in the
     * first frames and under load.
     */
    fun capture(frame: Frame): CapturedFrame? {
        val image = try {
            frame.acquireCameraImage()
        } catch (_: NotYetAvailableException) { return null }
          catch (_: DeadlineExceededException) { return null }
          catch (_: ResourceExhaustedException) { return null }
        val gray: ByteArray; val w: Int; val h: Int
        try {
            w = image.width; h = image.height
            gray = lumaOf(image)
        } finally { image.close() }

        val ci = frame.camera.imageIntrinsics
        val f = ci.focalLength; val c = ci.principalPoint
        val intrinsics = DeixisIntrinsics(fx = f[0], fy = f[1], cx = c[0], cy = c[1])
        val pose = frame.camera.pose.toMat4()

        val depth = depthOf(frame, w, h)
        return CapturedFrame(gray, w, h, intrinsics, pose, depth ?: KeyframeBuilder.DepthSource { _, _ -> null }, depth != null)
    }

    /** The Y plane of a YUV_420_888 image, packed into a tight row-major buffer. */
    private fun lumaOf(image: Image): ByteArray {
        val plane = image.planes[0]
        val buf = plane.buffer
        val w = image.width; val h = image.height
        val out = ByteArray(w * h)
        if (plane.pixelStride == 1 && plane.rowStride == w) {
            buf.get(out)
        } else {
            val row = ByteArray(plane.rowStride)
            for (y in 0 until h) {
                buf.position(y * plane.rowStride)
                buf.get(row, 0, minOf(plane.rowStride, buf.remaining()))
                if (plane.pixelStride == 1) System.arraycopy(row, 0, out, y * w, w)
                else for (x in 0 until w) out[y * w + x] = row[x * plane.pixelStride]
            }
        }
        return out
    }

    /**
     * ARCore's 16-bit depth image (millimetres) covers the same field of view as the camera
     * image at a lower resolution; a camera pixel maps to a depth pixel by scaling.
     */
    private fun depthOf(frame: Frame, camW: Int, camH: Int): KeyframeBuilder.DepthSource? {
        val image = try {
            frame.acquireDepthImage16Bits()
        } catch (_: NotYetAvailableException) { return null }
          catch (_: Exception) { return null }
        try {
            val dw = image.width; val dh = image.height
            val plane = image.planes[0]
            val buf = plane.buffer.order(java.nio.ByteOrder.nativeOrder())
            val rowStride = plane.rowStride
            val mm = ShortArray(dw * dh)
            for (y in 0 until dh) {
                buf.position(y * rowStride)
                buf.asShortBuffer().get(mm, y * dw, dw)
            }
            val sx = dw.toFloat() / camW; val sy = dh.toFloat() / camH
            return KeyframeBuilder.DepthSource { px, py ->
                val x = (px * sx).toInt(); val y = (py * sy).toInt()
                if (x < 0 || y < 0 || x >= dw || y >= dh) return@DepthSource null
                val v = mm[y * dw + x].toInt() and 0xFFFF
                if (v == 0) null else v / 1000f
            }
        } finally { image.close() }
    }
}
