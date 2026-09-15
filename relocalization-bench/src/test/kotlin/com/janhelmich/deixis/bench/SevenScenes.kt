package com.janhelmich.deixis.bench

import com.janhelmich.deixis.data.relocalization.CameraIntrinsics
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Float4
import dev.romainguy.kotlin.math.Mat4
import java.io.File
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

/**
 * Reader for Microsoft's [7-Scenes](https://www.microsoft.com/en-us/research/project/rgb-d-dataset-7-scenes/)
 * RGB-D relocalization dataset: per frame a `.color.png`, a 16-bit `.depth.png` (millimetres,
 * 65535 = invalid) and a `.pose.txt` holding a 4×4 camera-to-world matrix. All handheld Kinect
 * at 640×480.
 *
 * Poses arrive in the Kinect/OpenCV camera convention (X right, Y down, Z into the scene);
 * [poseArcFromKinect] flips them to the ARCore convention the pipeline uses, so a recovered
 * pose can be compared to ground truth in one frame.
 */
object SevenScenes {

    /** 7-Scenes' Kinect intrinsics for the 640×480 stream. */
    val intrinsics = CameraIntrinsics(fx = 585f, fy = 585f, cx = 320f, cy = 240f)

    /** Millimetre value the depth PNG uses for "no reading". */
    const val INVALID_DEPTH = 65535

    data class Frame(val color: File, val depth: File, val pose: File) {
        val name: String get() = color.name.removeSuffix(".color.png")
    }

    /** The frames of one `seq-XX` directory, in order. */
    fun sequence(seqDir: File): List<Frame> =
        seqDir.listFiles { f -> f.name.endsWith(".color.png") }.orEmpty()
            .sortedBy { it.name }
            .map { color ->
                val base = color.path.removeSuffix(".color.png")
                Frame(color, File("$base.depth.png"), File("$base.pose.txt"))
            }
            .filter { it.depth.exists() && it.pose.exists() }

    /** Parse a `.pose.txt`: four rows of four whitespace-separated floats, camera→world. */
    fun readPose(text: String): Mat4 {
        val v = text.trim().split(Regex("\\s+")).map { it.toFloat() }
        require(v.size == 16) { "expected 16 numbers in a pose, got ${v.size}" }
        // Row-major in the file; Mat4.of also reads row-major.
        return Mat4.of(*v.toFloatArray())
    }

    fun readPose(file: File): Mat4 = readPose(file.readText())

    /** arc-camera→world from the file's OpenCV-camera→world: post-multiply by F = diag(1,−1,−1). */
    fun poseArcFromKinect(kinectPose: Mat4): Mat4 = kinectPose * F

    /** Greyscale image bytes for the feature extractor, plus its dimensions. */
    fun readGray(colorFile: File): Triple<ByteArray, Int, Int> {
        val bgr = Imgcodecs.imread(colorFile.path, Imgcodecs.IMREAD_COLOR)
        val gray = Mat()
        try {
            Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
            val bytes = ByteArray((gray.total() * gray.channels()).toInt())
            gray.get(0, 0, bytes)
            return Triple(bytes, gray.cols(), gray.rows())
        } finally { bgr.release(); gray.release() }
    }

    /** A depth lookup in metres for the back-projector; `null` where the reading is invalid. */
    fun readDepthMeters(depthFile: File): (Float, Float) -> Float? {
        val depth = Imgcodecs.imread(depthFile.path, Imgcodecs.IMREAD_UNCHANGED) // CV_16U, mm
        require(depth.type() == CvType.CV_16U) { "expected 16-bit depth, got type ${depth.type()}" }
        val w = depth.cols(); val h = depth.rows()
        val raw = ShortArray((depth.total()).toInt())
        depth.get(0, 0, raw)
        depth.release()
        return fun(px: Float, py: Float): Float? {
            val x = px.toInt(); val y = py.toInt()
            if (x < 0 || y < 0 || x >= w || y >= h) return null
            val mm = raw[y * w + x].toInt() and 0xFFFF
            if (mm == 0 || mm == INVALID_DEPTH) return null
            return mm / 1000f
        }
    }

    private val F = Mat4(
        Float4(1f, 0f, 0f, 0f),
        Float4(0f, -1f, 0f, 0f),
        Float4(0f, 0f, -1f, 0f),
        Float4(0f, 0f, 0f, 1f),
    )
}
