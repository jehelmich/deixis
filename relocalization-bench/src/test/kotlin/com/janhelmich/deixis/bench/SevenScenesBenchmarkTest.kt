package com.janhelmich.deixis.bench

import com.janhelmich.deixis.data.relocalization.Keyframe
import com.janhelmich.deixis.data.relocalization.KeyframeBuilder
import com.janhelmich.deixis.data.relocalization.OrbFeatureExtractor
import com.janhelmich.deixis.data.relocalization.OrbRelocalizer
import com.janhelmich.deixis.data.relocalization.RelocalizationResult
import com.janhelmich.deixis.data.relocalization.WorldMap
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import dev.romainguy.kotlin.math.length
import java.io.File
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.min
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.Assume.assumeTrue

/**
 * Layer 2: the real relocalizer scored against a public RGB-D relocalization benchmark, off
 * device. Builds a [WorldMap] from one 7-Scenes training sequence (ORB + depth back-projection,
 * placed with the dataset's ground-truth poses), then localizes frames from a test sequence and
 * reports translation/rotation error against ground truth — the standard 7-Scenes protocol.
 *
 * Skips itself unless `DEIXIS_7SCENES_DIR` points at a scene directory (containing `seq-01`,
 * `seq-02`, …), so CI and a fresh clone stay green; see docs/relocalization-testing.md for the
 * download and the numbers to expect. Once the data is present it prints median translation
 * (cm), median rotation (°) and the success rate at the canonical 5 cm / 5° threshold.
 */
class SevenScenesBenchmarkTest {

    private val sceneDir = System.getenv("DEIXIS_7SCENES_DIR")?.let(::File)
    private val extractor by lazy { OrbFeatureExtractor(maxFeatures = 2000) }
    private val relocalizer by lazy { OrbRelocalizer(minInliers = 15) }

    @BeforeTest fun setUp() {
        assumeTrue(
            "Set DEIXIS_7SCENES_DIR to a 7-Scenes scene directory to run the RGB-D benchmark",
            sceneDir != null && sceneDir.isDirectory,
        )
        DesktopOpenCv.ensureLoaded()
    }

    @Test
    fun `relocalizes 7-Scenes test frames against a map built from the training sequence`() {
        val seqs = sceneDir!!.listFiles { f -> f.isDirectory && f.name.startsWith("seq-") }
            .orEmpty().sortedBy { it.name }
        assumeTrue("Need at least two sequences (train + test)", seqs.size >= 2)

        val map = buildMap(seqs.first(), sampleEvery = TRAIN_STRIDE)
        println("Built map from ${seqs.first().name}: ${map.keyframes.size} keyframes, ${map.pointCount} points")

        val test = SevenScenes.sequence(seqs[1])
        val translationErrors = ArrayList<Float>()
        val rotationErrors = ArrayList<Float>()
        var localized = 0
        var attempted = 0

        for (frame in test.filterIndexed { i, _ -> i % TEST_STRIDE == 0 }.take(MAX_TEST_FRAMES)) {
            attempted++
            val (gray, w, h) = SevenScenes.readGray(frame.color)
            val features = extractor.extract(gray, w, h)
            val result = relocalizer.relocalize(map, features, SevenScenes.intrinsics, Mat4.identity())
            if (result !is RelocalizationResult.Located) continue
            localized++
            val truth = SevenScenes.poseArcFromKinect(SevenScenes.readPose(frame.pose))
            translationErrors += length(truth.position - result.cameraInMap.position)
            rotationErrors += rotationErrorDegrees(truth, result.cameraInMap)
        }

        report(attempted, localized, translationErrors, rotationErrors)
    }

    private fun buildMap(seqDir: File, sampleEvery: Int): WorldMap {
        val frames = SevenScenes.sequence(seqDir).filterIndexed { i, _ -> i % sampleEvery == 0 }.take(MAX_MAP_FRAMES)
        val keyframes = frames.mapIndexed { id, frame ->
            val (gray, w, h) = SevenScenes.readGray(frame.color)
            val features = extractor.extract(gray, w, h)
            val poseArc = SevenScenes.poseArcFromKinect(SevenScenes.readPose(frame.pose))
            KeyframeBuilder.build(
                id = id, features = features, intrinsics = SevenScenes.intrinsics,
                cameraPose = poseArc, gravity = Float3(0f, -1f, 0f),
                depth = KeyframeBuilder.DepthSource(SevenScenes.readDepthMeters(frame.depth)),
                maxDepthMeters = 6f,
            )
        }
        return WorldMap("7scenes-${seqDir.name}", seqDir.name, 0, keyframes, emptyList())
    }

    private fun report(attempted: Int, localized: Int, tErr: List<Float>, rErr: List<Float>) {
        val rate = if (attempted == 0) 0f else localized.toFloat() / attempted
        println("7-Scenes: localized $localized/$attempted (${pct(rate)})")
        if (tErr.isEmpty()) { println("  no frames localized"); return }
        val success = tErr.indices.count { tErr[it] <= 0.05f && rErr[it] <= 5f }
        println("  median translation error: ${cm(median(tErr))} cm")
        println("  median rotation error:    ${"%.1f".format(median(rErr))}°")
        println("  success @ 5cm/5°:          $success/${tErr.size} (${pct(success.toFloat() / tErr.size)})")
    }

    private val Mat4.position get() = Float3(w.x, w.y, w.z)

    /** Angle between two rotations, in degrees, from the trace of Rᵀ·R'. */
    private fun rotationErrorDegrees(a: Mat4, b: Mat4): Float {
        var trace = 0f
        for (i in 0..2) for (j in 0..2) trace += a[i][j] * b[i][j] // sum of elementwise = trace(Aᵀ... ) see note
        // a[i] is column i; a[i][j] is column i row j. trace(RaᵀRb) = Σ_ij Ra[j][i]*Rb[j][i] = Σ over cols·rows.
        val cos = ((trace - 1f) / 2f).coerceIn(-1f, 1f)
        return (acos(cos) * 180f / PI).toFloat()
    }

    private fun median(xs: List<Float>): Float {
        val s = xs.sorted(); val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2f
    }
    private fun cm(m: Float) = "%.1f".format(m * 100)
    private fun pct(f: Float) = "%.0f%%".format(f * 100)

    private companion object {
        const val TRAIN_STRIDE = 10   // one keyframe per 10 training frames
        const val TEST_STRIDE = 20
        const val MAX_MAP_FRAMES = 100
        const val MAX_TEST_FRAMES = 100
    }
}
