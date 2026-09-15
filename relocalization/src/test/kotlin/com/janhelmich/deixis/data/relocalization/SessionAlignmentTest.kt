package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import dev.romainguy.kotlin.math.rotation
import dev.romainguy.kotlin.math.translation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionAlignmentTest {

    // Two devices a couple of metres apart — the lever arm that makes rotation error visible.
    private val constellation = listOf(translation(Float3(1f, 0f, -2f)), translation(Float3(-1.5f, 0f, -1f)))
    private fun alignment(cfg: AlignmentConfig = AlignmentConfig()) = SessionAlignment(constellation, cfg)

    /** Bootstrapping now needs two agreeing observations; most tests start from an adopted identity. */
    private fun bootstrapped(cfg: AlignmentConfig = AlignmentConfig(), inliers: Int = 40): SessionAlignment =
        alignment(cfg).also { it.propose(Mat4.identity(), inliers); it.propose(Mat4.identity(), inliers) }

    @Test
    fun `weak candidates are ignored, and the first lock needs strength AND confirmation`() {
        val a = alignment()
        assertEquals(AlignmentDecision.RejectedWeak, a.propose(Mat4.identity(), inliers = 10))
        assertEquals(AlignmentDecision.RejectedWeak, a.propose(Mat4.identity(), inliers = 20)) // ≥ min, < bootstrap
        assertNull(a.current)
        val first = translation(Float3(1f, 0f, 0f))
        // One strong observation is not enough on its own…
        assertEquals(AlignmentDecision.RejectedWeak, a.propose(first, inliers = 30))
        assertNull(a.current)
        // …a second that agrees adopts it.
        assertEquals(AlignmentDecision.Bootstrapped, a.propose(first * translation(Float3(0.02f, 0f, 0f)), inliers = 30))
        assertEquals(1.01f, a.current!!.translation.x, 1e-4f) // the average of the two
    }

    @Test
    fun `a single wrong first observation is never adopted`() {
        val a = alignment()
        assertEquals(AlignmentDecision.RejectedWeak, a.propose(translation(Float3(3f, 0f, 1f)), inliers = 40)) // wrong
        assertEquals(AlignmentDecision.RejectedWeak, a.propose(Mat4.identity(), inliers = 40))               // truth, disagrees → run restarts
        assertNull(a.current)
        assertEquals(AlignmentDecision.Bootstrapped, a.propose(Mat4.identity(), inliers = 40))               // truth again → adopted
        assertEquals(0f, a.current!!.translation.x, 1e-6f)
    }

    @Test
    fun `a consistent candidate is blended, not snapped, with gain rising with inliers`() {
        val a = bootstrapped()
        val nudge = translation(Float3(0.10f, 0f, 0f)) // 10 cm correction, within the gate
        val weak = assertIs<AlignmentDecision.Blended>(a.propose(nudge, inliers = 20))
        val xAfterWeak = a.current!!.translation.x
        assertTrue(xAfterWeak > 0f && xAfterWeak < 0.10f, "should move partway, moved to $xAfterWeak")
        // Reset and try a strong candidate: larger step.
        val b = bootstrapped()
        val strong = assertIs<AlignmentDecision.Blended>(b.propose(nudge, inliers = 100))
        assertTrue(strong.gain > weak.gain, "gain ${strong.gain} should exceed ${weak.gain}")
        assertTrue(b.current!!.translation.x > xAfterWeak)
    }

    @Test
    fun `a candidate that would scatter the constellation is rejected as a wrong lock`() {
        val a = bootstrapped()
        // Pure yaw of 20° at the camera is a >0.35 m move for a device 2 m away.
        val wrong = rotation(Float3(0f, 1f, 0f), 20f)
        val d = assertIs<AlignmentDecision.RejectedInconsistent>(a.propose(wrong, inliers = 60))
        assertTrue(d.maxMarkerJumpMeters > 0.35f, "jump ${d.maxMarkerJumpMeters}")
        assertEquals(0f, a.current!!.translation.x, 1e-6f) // untouched
    }

    @Test
    fun `a run of mutually consistent rejections re-bootstraps onto the consensus`() {
        val a = bootstrapped(AlignmentConfig(rebootstrapAfter = 3))
        val newTruth = translation(Float3(2f, 0f, 0f)) // ARCore reset its world by 2 m
        assertIs<AlignmentDecision.RejectedInconsistent>(a.propose(newTruth, 50))
        assertIs<AlignmentDecision.RejectedInconsistent>(a.propose(newTruth * translation(Float3(0.02f, 0f, 0f)), 50))
        assertEquals(AlignmentDecision.Rebootstrapped, a.propose(newTruth * translation(Float3(-0.01f, 0f, 0f)), 50))
        assertEquals(1.99f, a.current!!.translation.x, 1e-4f)
    }

    @Test
    fun `the consistency gate tightens for weak candidates`() {
        // A 25 cm disagreement at the markers: a strong candidate may correct that much, a weak
        // one may not — with few inliers it is more likely noise than drift.
        val far = translation(Float3(0.25f, 0f, 0f))
        val weak = bootstrapped()
        assertIs<AlignmentDecision.RejectedInconsistent>(weak.propose(far, inliers = 16))
        assertEquals(0f, weak.current!!.translation.x, 1e-6f)
        val strong = bootstrapped()
        assertIs<AlignmentDecision.Blended>(strong.propose(far, inliers = 90))
        assertTrue(strong.current!!.translation.x > 0.05f)
    }

    @Test
    fun `a repeatable but weak wrong lock cannot vote itself in`() {
        val a = bootstrapped(AlignmentConfig(rebootstrapAfter = 3, rebootstrapMinInliers = 35))
        val wrong = translation(Float3(3f, 0f, 1f))
        repeat(5) { assertIs<AlignmentDecision.RejectedInconsistent>(a.propose(wrong, inliers = 30)) }
        assertEquals(0f, a.current!!.translation.x, 1e-6f)
    }

    @Test
    fun `scattered rejections never re-bootstrap`() {
        val a = bootstrapped(AlignmentConfig(rebootstrapAfter = 3))
        for (x in listOf(2f, 5f, 9f, 3f, 7f)) {
            assertIs<AlignmentDecision.RejectedInconsistent>(a.propose(translation(Float3(x, 0f, 0f)), 50))
        }
        assertEquals(0f, a.current!!.translation.x, 1e-6f)
    }
}
