package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import dev.romainguy.kotlin.math.length
import dev.romainguy.kotlin.math.rotation
import dev.romainguy.kotlin.math.translation
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The feedback loop under simulated conditions: ARCore's session frame drifts, relocalization
 * arrives noisily and only sometimes, and occasionally lies. The markers must stay put.
 */
class RelocalizationControllerTest {

    private val intrinsics = CameraIntrinsics(500f, 500f, 320f, 240f)
    private val noFeatures = ExtractedFeatures.EMPTY
    private val marker = MarkerPose("lamp", "Lamp", "light.desk", translation(Float3(1f, 0f, -2f)))
    private val map = WorldMap("room", "Room", 0, emptyList(), listOf(marker))

    /** A relocalizer driven by a script instead of images. */
    private class Scripted(var next: () -> RelocalizationResult) : Relocalizer {
        override fun relocalize(map: WorldMap, liveFeatures: ExtractedFeatures, intrinsics: CameraIntrinsics, cameraInSession: Mat4) = next()
    }

    private fun located(t: Mat4, inliers: Int) =
        RelocalizationResult.Located(sessionFromMap = t, cameraInMap = Mat4.identity(), inlierCount = inliers)

    private val Mat4.pos get() = translation

    @Test
    fun `status goes Searching to Locked to Coasting to Locked`() {
        var script: RelocalizationResult = RelocalizationResult.NotFound
        val c = RelocalizationController(map, Scripted { script },
            ControllerConfig(attemptIntervalNanos = 100_000_000L, coastAfterNanos = 1_000_000_000L))
        val s = 1_000_000_000L
        c.onFrame(noFeatures, intrinsics, Mat4.identity(), 0); assertEquals(AlignmentStatus.Searching, c.status)
        script = located(Mat4.identity(), 40)
        c.onFrame(noFeatures, intrinsics, Mat4.identity(), s)                 // first observation: pending
        assertEquals(AlignmentStatus.Searching, c.status)
        c.onFrame(noFeatures, intrinsics, Mat4.identity(), s + s / 5)         // confirmed
        assertIs<AlignmentStatus.Locked>(c.status)
        script = RelocalizationResult.NotFound
        c.onFrame(noFeatures, intrinsics, Mat4.identity(), s + 3 * s); assertIs<AlignmentStatus.Coasting>(c.status)
        script = located(Mat4.identity(), 40)
        c.onFrame(noFeatures, intrinsics, Mat4.identity(), s + 4 * s); assertIs<AlignmentStatus.Locked>(c.status)
    }

    @Test
    fun `honours the attempt cadence`() {
        var calls = 0
        val c = RelocalizationController(map, Scripted { calls++; RelocalizationResult.NotFound },
            ControllerConfig(attemptIntervalNanos = 500_000_000L))
        for (i in 0 until 30) c.onFrame(noFeatures, intrinsics, Mat4.identity(), i * 33_000_000L) // 30 frames ≈ 1 s
        assertTrue(calls in 2..3, "expected ~2 attempts per second, got $calls")
    }

    @Test
    fun `markers stay within tolerance while the session drifts and wrong locks are refused`() {
        val rnd = Random(3)
        val frameNs = 33_000_000L
        val totalFrames = 60 * 30 // 60 s
        // Ground truth: the session frame drifts 2 cm/s sideways and 0.5°/s in yaw.
        fun truth(t: Float): Mat4 = translation(Float3(0.02f * t, 0f, 0f)) * rotation(Float3(0f, 1f, 0f), 0.5f * t)

        var t = 0f
        val scripted = Scripted { RelocalizationResult.NotFound }
        val c = RelocalizationController(map, scripted,
            ControllerConfig(attemptIntervalNanos = 500_000_000L, coastAfterNanos = 3_000_000_000L))

        var worstErr = 0f; var bootstrappedAt = -1
        var wrongLocksOffered = 0; var wrongLocksAccepted = 0
        var coastingFrames = 0
        for (i in 0 until totalFrames) {
            val now = i * frameNs; t = now / 1e9f
            val d = truth(t)
            scripted.next = {
                when {
                    // A 4-second blackout: looking at a blank wall.
                    t in 20f..24f -> RelocalizationResult.NotFound
                    // 1 in 12 attempts is a confident-looking WRONG lock across the room.
                    rnd.nextInt(12) == 0 -> { wrongLocksOffered++; located(translation(Float3(3f, 0f, 1f)) * rotation(Float3(0f, 1f, 0f), 40f), 30) }
                    // Otherwise a noisy but honest measurement of the truth.
                    else -> located(d * translation(Float3(rnd.nextFloat() * 0.02f - 0.01f, rnd.nextFloat() * 0.02f - 0.01f, 0f)) *
                        rotation(Float3(0f, 1f, 0f), rnd.nextFloat() - 0.5f), 40 + rnd.nextInt(50))
                }
            }
            val decision = c.onFrame(noFeatures, intrinsics, Mat4.identity(), now)
            if (decision is AlignmentDecision.Bootstrapped && bootstrappedAt < 0) bootstrappedAt = i
            if (decision is AlignmentDecision.Rebootstrapped) wrongLocksAccepted++ // would only happen if the wrong lock won
            if (c.status is AlignmentStatus.Coasting) coastingFrames++
            // Where we render the marker vs where it truly is.
            c.alignment.current?.let { a ->
                val err = length((a * marker.pose).pos - (d * marker.pose).pos)
                if (i > bootstrappedAt + 30) { // 1 s to settle after bootstrap
                    assertTrue(err < 0.5f, "frame $i: marker rendered ${"%.2f".format(err)} m from truth — a wrong lock got through")
                    worstErr = maxOf(worstErr, err)
                }
            }
        }
        println("drift sim: bootstrapped at frame $bootstrappedAt, worst marker error after settling %.3f m, wrong locks offered %d accepted %d, coasting frames %d".format(worstErr, wrongLocksOffered, wrongLocksAccepted, coastingFrames))
        assertTrue(bootstrappedAt in 0..90, "should lock within the first few attempts, locked at frame $bootstrappedAt")
        assertTrue(wrongLocksOffered >= 5, "the script should have offered wrong locks, offered $wrongLocksOffered")
        assertEquals(0, wrongLocksAccepted, "a wrong lock must never move the markers")
        // Drift is 2 cm/s with corrections every 0.5 s and a 4 s blackout (8 cm) — the constellation
        // must stay within a hand's width of the truth throughout.
        assertTrue(worstErr < 0.15f, "worst marker error ${worstErr} m")
        assertTrue(coastingFrames > 0, "the blackout should have shown as Coasting")
    }
}
