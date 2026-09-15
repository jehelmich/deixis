package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Mat4

data class ControllerConfig(
    /** Relocalization is not free; attempt it at most this often. */
    val attemptIntervalNanos: Long = 500_000_000L,
    /** Without an accepted correction for this long the status turns to [AlignmentStatus.Coasting]. */
    val coastAfterNanos: Long = 3_000_000_000L,
)

/** What the app should show and do right now. */
sealed interface AlignmentStatus {
    /** No lock yet — keep the camera moving over the room. */
    data object Searching : AlignmentStatus

    /** Aligned and recently confirmed. Render markers through [alignment]. */
    data class Locked(val alignment: Mat4, val inliers: Int) : AlignmentStatus

    /** Aligned, but no confirmation for a while — riding on ARCore tracking alone. */
    data class Coasting(val alignment: Mat4, val sinceLockNanos: Long) : AlignmentStatus
}

/**
 * The runtime feedback loop, as a pure state machine: feed it each frame's features and the
 * camera's session pose, and it drives a [Relocalizer] on a cadence, passes every result through
 * [SessionAlignment]'s gates, and reports what the app should do. No Android, no threads — the
 * app wraps it and the tests drive it with a scripted relocalizer and a fake clock.
 *
 * Lifecycle it implements (docs/anchoring.md):
 *  Searching ──first confident lock──▶ Locked ──no lock for a while──▶ Coasting ──lock──▶ Locked
 * and, at any time, a run of consistent candidates that disagree with the current alignment
 * re-bootstraps it (ARCore reset its world, or the first lock was wrong).
 */
class RelocalizationController(
    val map: WorldMap,
    private val relocalizer: Relocalizer,
    private val config: ControllerConfig = ControllerConfig(),
    alignmentConfig: AlignmentConfig = AlignmentConfig(),
) {
    val alignment = SessionAlignment(map.markers.map { it.pose }, alignmentConfig)

    var status: AlignmentStatus = AlignmentStatus.Searching
        private set

    var onStatusChanged: ((AlignmentStatus) -> Unit)? = null

    private var lastAttemptNanos: Long? = null
    private var lastLockNanos: Long? = null
    private var lastResult: RelocalizationResult = RelocalizationResult.NotFound
    val lastRelocalization: RelocalizationResult get() = lastResult

    /** Total recognition attempts so far, so callers can tell a skipped frame from a miss. */
    var attempts: Int = 0
        private set

    /**
     * Offer a frame. Returns the alignment decision if a relocalization attempt ran this frame,
     * `null` if it was skipped for cadence.
     */
    fun onFrame(
        features: ExtractedFeatures,
        intrinsics: CameraIntrinsics,
        cameraInSession: Mat4,
        nowNanos: Long,
    ): AlignmentDecision? {
        var decision: AlignmentDecision? = null
        val last = lastAttemptNanos
        if (last == null || nowNanos - last >= config.attemptIntervalNanos) {
            lastAttemptNanos = nowNanos
            attempts++
            val result = relocalizer.relocalize(map, features, intrinsics, cameraInSession)
            lastResult = result
            if (result is RelocalizationResult.Located) {
                decision = alignment.propose(result.sessionFromMap, result.inlierCount)
                if (decision.isAccepted()) lastLockNanos = nowNanos
            }
        }
        refreshStatus(nowNanos)
        return decision
    }

    /** Adopt a known alignment now (map built in this session) and report Locked. */
    fun seed(alignment: Mat4, nowNanos: Long) {
        this.alignment.seed(alignment)
        lastLockNanos = nowNanos
        refreshStatus(nowNanos)
    }

    /** Every marker's pose in the session under the current alignment, or `null` if unlocked. */
    fun markersInSession(): List<Pair<MarkerPose, Mat4>>? =
        alignment.current?.let { a -> map.markers.map { it to (a * it.pose) } }

    private fun refreshStatus(nowNanos: Long) {
        val a = alignment.current
        val lock = lastLockNanos
        val next = when {
            a == null || lock == null -> AlignmentStatus.Searching
            nowNanos - lock <= config.coastAfterNanos ->
                AlignmentStatus.Locked(a, alignment.lastAcceptedInliers)
            else -> AlignmentStatus.Coasting(a, nowNanos - lock)
        }
        if (next != status) {
            status = next
            onStatusChanged?.invoke(next)
        }
    }

    private fun AlignmentDecision.isAccepted() =
        this is AlignmentDecision.Bootstrapped || this is AlignmentDecision.Blended ||
            this is AlignmentDecision.Rebootstrapped
}
