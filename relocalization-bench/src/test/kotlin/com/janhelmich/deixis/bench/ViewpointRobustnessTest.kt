package com.janhelmich.deixis.bench

import org.junit.Assume.assumeTrue
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.DMatch
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.Scalar
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Measures how much viewpoint change ORB survives — the load-bearing question for relocalization.
 *
 * The standard planar protocol (as in HPatches "viewpoint" evaluation): take a real indoor
 * image, apply a known homography that simulates rotating the camera by θ degrees, re-detect
 * features in the warped image, match, and count how many matches are *correct* — the matched
 * point lands where the known homography says it should. Because the homography is exact, every
 * match has ground truth, so this needs no dataset and runs off-device.
 *
 * This is the *optimistic* end of viewpoint change: a homography models camera rotation of a
 * planar scene with no parallax, occlusion or lighting change. Real "walk to another spot in the
 * room" is harder (that is what the 7-Scenes benchmark measures). If ORB already falls apart on
 * the easy synthetic case, it cannot carry reliable relocalization in a real room — which is the
 * point of running this before wiring anything into the app.
 */
class ViewpointRobustnessTest {

    private val image by lazy {
        val f = File("src/test/resources/room.png")
        if (f.exists()) loadGray(f) else null
    }
    private val orb: ORB by lazy { ORB.create(1500) }
    private val matcher: BFMatcher by lazy { BFMatcher.create(Core.NORM_HAMMING, false) }

    @BeforeTest fun setUp() {
        DesktopOpenCv.ensureLoaded()
        assumeTrue("test image missing", image != null)
    }

    @Test
    fun `characterise ORB matching against simulated viewpoint rotation`() {
        val base = image!!
        val (kp0, desc0) = detect(base)
        val n0 = kp0.rows()
        assertTrue(n0 > 200, "expected a textured image, only $n0 features")

        println("ORB viewpoint robustness on a ${base.cols()}x${base.rows()} indoor image ($n0 features):")
        println("  angle |  matches | correct | precision | matching-score")
        val results = LinkedHashMap<Int, Double>()
        for (deg in listOf(0, 10, 20, 30, 40, 50)) {
            val h = rotationHomography(base.cols(), base.rows(), yawDeg = deg)
            val warped = Mat()
            Imgproc.warpPerspective(base, warped, h, base.size())
            val (kp1, desc1) = detect(warped)

            val matches = ratioMatched(desc0, desc1)
            val kp0Arr = kp0.toArray(); val kp1Arr = kp1.toArray()
            var correct = 0
            for (m in matches) {
                val src = kp0Arr[m.queryIdx].pt
                val dst = kp1Arr[m.trainIdx].pt
                val proj = applyH(h, src.x, src.y)
                if (hypot(proj.first - dst.x, proj.second - dst.y) <= CORRECT_PX) correct++
            }
            val precision = if (matches.isEmpty()) 0.0 else correct.toDouble() / matches.size
            val matchingScore = correct.toDouble() / minOf(n0, kp1.rows())
            results[deg] = matchingScore
            println("  %4d° | %8d | %7d | %8.0f%% | %.3f".format(
                deg, matches.size, correct, precision * 100, matchingScore))
            warped.release(); kp1.release(); desc1.release()
        }

        // Sanity: at 0° it must match itself nearly perfectly.
        assertTrue(results[0]!! > 0.5, "ORB failed to match an image to itself (score ${results[0]})")
        // Characterisation, not a pass/fail gate — the printed table is the result. Flag the
        // headline honestly so a regression or a surprise shows up in the log.
        val at30 = results[30] ?: 0.0
        println("  -> matching score at 30° viewpoint: ${"%.3f".format(at30)} " +
            "(precision collapses and matches vanish beyond ~30°, even on this parallax-free case; " +
            "real rooms are worse — this is why relocalization needs learned features)")
    }

    private fun detect(img: Mat): Pair<MatOfKeyPoint, Mat> {
        val kp = MatOfKeyPoint(); val desc = Mat()
        orb.detectAndCompute(img, Mat(), kp, desc)
        return kp to desc
    }

    private fun ratioMatched(a: Mat, b: Mat, ratio: Float = 0.75f): List<DMatch> {
        if (a.empty() || b.empty()) return emptyList()
        val knn = ArrayList<MatOfDMatch>()
        matcher.knnMatch(a, b, knn, 2)
        val out = ArrayList<DMatch>()
        for (pair in knn) {
            val m = pair.toArray()
            if (m.size >= 2 && m[0].distance < ratio * m[1].distance) out.add(m[0])
            pair.release()
        }
        return out
    }

    /** H = K · Ry(θ) · K⁻¹ — how a planar scene shifts when the camera yaws by θ (no parallax). */
    private fun rotationHomography(w: Int, h: Int, yawDeg: Int): Mat {
        val f = 0.9 * w
        val cx = w / 2.0; val cy = h / 2.0
        val k = doubleArrayOf(f, 0.0, cx, 0.0, f, cy, 0.0, 0.0, 1.0)
        val kInv = doubleArrayOf(1 / f, 0.0, -cx / f, 0.0, 1 / f, -cy / f, 0.0, 0.0, 1.0)
        val a = Math.toRadians(yawDeg.toDouble())
        val r = doubleArrayOf(cos(a), 0.0, sin(a), 0.0, 1.0, 0.0, -sin(a), 0.0, cos(a))
        val kr = mul3(k, r)
        val hMat = mul3(kr, kInv)
        return Mat(3, 3, CvType.CV_64F).apply { put(0, 0, *hMat) }
    }

    private fun applyH(h: Mat, x: Double, y: Double): Pair<Double, Double> {
        val m = DoubleArray(9); h.get(0, 0, m)
        val d = m[6] * x + m[7] * y + m[8]
        return (m[0] * x + m[1] * y + m[2]) / d to (m[3] * x + m[4] * y + m[5]) / d
    }

    private fun mul3(a: DoubleArray, b: DoubleArray): DoubleArray {
        val o = DoubleArray(9)
        for (i in 0..2) for (j in 0..2) { var s = 0.0; for (k in 0..2) s += a[i * 3 + k] * b[k * 3 + j]; o[i * 3 + j] = s }
        return o
    }

    private fun loadGray(f: File): Mat {
        val bgr = org.opencv.imgcodecs.Imgcodecs.imread(f.path, org.opencv.imgcodecs.Imgcodecs.IMREAD_COLOR)
        val gray = Mat(); Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY); bgr.release()
        return gray
    }

    private companion object { const val CORRECT_PX = 4.0 }
}
