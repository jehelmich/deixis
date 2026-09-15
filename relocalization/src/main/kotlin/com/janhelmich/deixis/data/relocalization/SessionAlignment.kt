package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import dev.romainguy.kotlin.math.length

/**
 * Tuning for [SessionAlignment]. Defaults are deliberately conservative: a wrong lock that
 * moves every device is far worse than a missed correction.
 */
data class AlignmentConfig(
    /** Candidates with fewer PnP inliers are ignored outright. */
    val minInliers: Int = 15,
    /** The very first lock must be stronger — nothing exists yet to sanity-check it against. */
    val bootstrapInliers: Int = 25,
    /**
     * …and it must be *confirmed*: this many consecutive candidates have to agree (within
     * [rebootstrapAgreementMeters] at the probes) before the first alignment is adopted. Never
     * move every device on the strength of a single observation.
     */
    val bootstrapConfirmations: Int = 2,
    /** Inlier count at which a candidate earns [maxGain]; below it the gain scales down. */
    val saturationInliers: Int = 80,
    val minGain: Float = 0.12f,
    val maxGain: Float = 0.4f,
    /**
     * Constellation gate: a candidate that would move any marker further than this from where
     * the current alignment puts it is treated as a wrong lock and rejected. This is the gate
     * for a candidate at [saturationInliers]; weaker candidates get a tighter one, down to
     * [weakGateFraction] of it at [minInliers] — a noisy, low-confidence observation is not
     * allowed to drag the constellation far, which is what jitter is.
     */
    val maxMarkerJumpMeters: Float = 0.35f,
    val weakGateFraction: Float = 0.3f,
    /**
     * Re-bootstrap: if this many consecutive rejected candidates agree with *each other* (to
     * within [rebootstrapAgreementMeters] at the markers), the current alignment is the one
     * that is wrong — e.g. a bad first lock, or ARCore reset its world — and the consensus wins.
     */
    val rebootstrapAfter: Int = 3,
    val rebootstrapAgreementMeters: Float = 0.15f,
    /**
     * Overturning an existing alignment takes stronger evidence than establishing one: only
     * candidates at least this strong count toward a re-bootstrap. Wrong locks are
     * characteristically weaker than honest ones, so a repeatable wrong lock cannot vote.
     */
    val rebootstrapMinInliers: Int = 35,
    /** Lever arm used to give rotation error a distance when there are no markers yet. */
    val probeRadiusMeters: Float = 1.5f,
)

/** What [SessionAlignment.propose] did with a candidate, for logs, UI and tests. */
sealed interface AlignmentDecision {
    data object Bootstrapped : AlignmentDecision
    data class Blended(val gain: Float, val correctionMeters: Float) : AlignmentDecision
    data object RejectedWeak : AlignmentDecision
    data class RejectedInconsistent(val maxMarkerJumpMeters: Float) : AlignmentDecision
    data object Rebootstrapped : AlignmentDecision
}

/**
 * The self-correcting alignment between ARCore's drifting session frame and the stored map —
 * the "alignment grid" every restored marker is rendered through.
 *
 * Holds one smoothed transform `T_session_map`. Each confident relocalization proposes a new
 * estimate; instead of snapping, the current estimate is blended toward it with a gain that
 * grows with inlier count. Before blending, the **constellation gate** asks where the candidate
 * would put every marker compared to the current alignment: markers form a rigid set, so a
 * candidate that scatters them is a wrong lock and is refused. Because the check is done at the
 * markers rather than at the camera, rotation error is weighted by its lever arm — a small
 * angular error that would move a device across the room counts as the large jump it is.
 *
 * Between accepted corrections the caller keeps rendering through [current] while ARCore's own
 * tracking carries the markers; each accepted correction pulls accumulated drift back out.
 * See docs/anchoring.md.
 */
class SessionAlignment(
    constellation: List<Mat4>,
    private val config: AlignmentConfig = AlignmentConfig(),
) {
    /** Marker positions in the map frame, or probe points on the axes when there are none yet. */
    private val probes: List<Float3> = constellation.map { it.translation }.ifEmpty {
        val r = config.probeRadiusMeters
        listOf(Float3(r, 0f, 0f), Float3(-r, 0f, 0f), Float3(0f, 0f, r), Float3(0f, 0f, -r), Float3(0f, r, 0f))
    }

    /** `T_session_map`, or `null` until the first confident lock. */
    var current: Mat4? = null
        private set

    /** Inlier count of the last accepted candidate — a plain confidence readout for the UI. */
    var lastAcceptedInliers: Int = 0
        private set

    private val rejectedRun = ArrayList<Mat4>()
    private val bootstrapRun = ArrayList<Mat4>()

    fun propose(candidate: Mat4, inliers: Int): AlignmentDecision {
        if (inliers < config.minInliers) return AlignmentDecision.RejectedWeak

        val cur = current
        if (cur == null) {
            if (inliers < config.bootstrapInliers) return AlignmentDecision.RejectedWeak
            // Keep only a run that agrees with this candidate; adopt once it is long enough.
            if (bootstrapRun.any { maxProbeDisplacement(it, candidate) > config.rebootstrapAgreementMeters }) {
                bootstrapRun.clear()
            }
            bootstrapRun += candidate
            if (bootstrapRun.size < config.bootstrapConfirmations) return AlignmentDecision.RejectedWeak
            // Adopt the average of the agreeing observations, not the last one raw: halves the
            // noise of the first placement, which is the one the user sees first.
            accept(blendPose(bootstrapRun.first(), candidate, 0.5f), inliers)
            bootstrapRun.clear()
            return AlignmentDecision.Bootstrapped
        }

        val jump = maxProbeDisplacement(cur, candidate)
        val strength = strengthOf(inliers)
        val gate = config.maxMarkerJumpMeters * (config.weakGateFraction + (1f - config.weakGateFraction) * strength)
        if (jump > gate) {
            if (inliers >= config.rebootstrapMinInliers) rejectedRun += candidate else rejectedRun.clear()
            if (rejectedRun.size >= config.rebootstrapAfter && rejectedRunAgrees()) {
                accept(candidate, inliers)
                return AlignmentDecision.Rebootstrapped
            }
            return AlignmentDecision.RejectedInconsistent(jump)
        }

        val gain = config.minGain + (config.maxGain - config.minGain) * strength
        current = blendPose(cur, candidate, gain)
        lastAcceptedInliers = inliers
        rejectedRun.clear()
        return AlignmentDecision.Blended(gain, jump)
    }

    /**
     * Adopt a known alignment outright — for a map that was just built in *this* session, where
     * `T_session_map` is the identity by construction. Later corrections blend from here.
     */
    fun seed(alignment: Mat4) {
        current = alignment
        lastAcceptedInliers = 0
        rejectedRun.clear(); bootstrapRun.clear()
    }

    /** Where the current alignment puts a map-frame pose in the session, or `null` if unlocked. */
    fun toSession(poseInMap: Mat4): Mat4? = current?.let { it * poseInMap }

    private fun accept(candidate: Mat4, inliers: Int) {
        current = candidate
        lastAcceptedInliers = inliers
        rejectedRun.clear()
    }

    /** 0 at [AlignmentConfig.minInliers], 1 at [AlignmentConfig.saturationInliers]. */
    private fun strengthOf(inliers: Int): Float =
        ((inliers - config.minInliers).toFloat() /
            (config.saturationInliers - config.minInliers).coerceAtLeast(1)).coerceIn(0f, 1f)

    /** Largest distance any probe point moves between two alignments — the constellation metric. */
    fun maxProbeDisplacement(a: Mat4, b: Mat4): Float =
        probes.maxOf { p -> length(a.transformPoint(p) - b.transformPoint(p)) }

    private fun rejectedRunAgrees(): Boolean {
        val recent = rejectedRun.takeLast(config.rebootstrapAfter)
        for (i in recent.indices) for (j in i + 1 until recent.size) {
            if (maxProbeDisplacement(recent[i], recent[j]) > config.rebootstrapAgreementMeters) return false
        }
        return true
    }
}
