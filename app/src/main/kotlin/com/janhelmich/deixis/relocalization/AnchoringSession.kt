package com.janhelmich.deixis.relocalization

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.ar.core.Frame
import com.janhelmich.deixis.data.relocalization.AlignmentDecision
import com.janhelmich.deixis.data.relocalization.AlignmentStatus
import com.janhelmich.deixis.data.relocalization.FileMapStore
import com.janhelmich.deixis.data.relocalization.KeyframeBuilder
import com.janhelmich.deixis.data.relocalization.KeyframeSelector
import com.janhelmich.deixis.data.relocalization.MapBuilder
import com.janhelmich.deixis.data.relocalization.MarkerPose
import com.janhelmich.deixis.data.relocalization.OrbFeatureExtractor
import com.janhelmich.deixis.data.relocalization.OrbRelocalizer
import com.janhelmich.deixis.data.relocalization.RelocalizationController
import com.janhelmich.deixis.data.relocalization.RelocalizationResult
import com.janhelmich.deixis.data.relocalization.poseDelta
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The on-device end of docs/anchoring.md: feeds ARCore frames into capture (while arranging the
 * room) and recognition (whenever a saved map exists), and tells the screen when to restore or
 * re-anchor markers. The render thread only ever pays for copying a frame out; extraction,
 * keyframe building and relocalization run on one worker, one frame at a time.
 *
 * One reference space for now: the map is always [MAP_ID]. If OpenCV's native library will not
 * load, [available] is false and the app behaves exactly as it did without persistence.
 */
class AnchoringSession(context: Context) {

    val available: Boolean = OpenCvLoader.ensureLoaded()

    private val store = FileMapStore(File(context.applicationContext.filesDir, "maps"))
    private val extractor = if (available) OrbFeatureExtractor(maxFeatures = 1200) else null
    private val relocalizer = if (available) OrbRelocalizer() else null
    private val selector = KeyframeSelector()
    private val builder = MapBuilder(MAP_ID, "Home")
    private var controller: RelocalizationController? = null
    private var restored = false
    /** The alignment the markers' anchors were last created at; re-anchor when it drifts away. */
    private var anchoredAlignment: Mat4? = null
    private var trackingSinceNanos: Long? = null
    private var lastFrameNanos: Long? = null
    private var loggedDims = false

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "deixis-anchoring") }
    private val busy = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()

    private val _status = MutableStateFlow<AlignmentStatus>(AlignmentStatus.Searching)
    /** Searching / Locked / Coasting, for the status pill. */
    val status: StateFlow<AlignmentStatus> = _status.asStateFlow()

    private val _keyframes = MutableStateFlow(0)
    val keyframes: StateFlow<Int> = _keyframes.asStateFlow()

    private val _hasMap = MutableStateFlow(false)
    val hasMap: StateFlow<Boolean> = _hasMap.asStateFlow()

    private val _note = MutableStateFlow<String?>(null)
    /** One-line diagnostics worth surfacing (no depth, save failed, …). */
    val note: StateFlow<String?> = _note.asStateFlow()

    /** First confirmed lock on a loaded map: every marker with its pose in the session. Main thread. */
    var onRestore: ((List<Pair<MarkerPose, Mat4>>) -> Unit)? = null
    /** An accepted correction big enough to move anchors. Main thread. */
    var onReanchor: ((List<Pair<MarkerPose, Mat4>>) -> Unit)? = null

    /** Load the saved room, if there is one, and start recognising against it. */
    fun loadIfPresent(): Boolean {
        if (!available) { _note.value = "Persistence unavailable: OpenCV did not load"; return false }
        val map = runCatching { store.load(MAP_ID) }.getOrNull() ?: return false
        synchronized(lock) {
            controller = RelocalizationController(map, relocalizer!!)
            restored = false
            anchoredAlignment = null
            _hasMap.value = true
            _status.value = AlignmentStatus.Searching
        }
        Log.i(TAG, "loaded map: ${map.keyframes.size} keyframes, ${map.pointCount} points, ${map.markers.size} markers")
        return true
    }

    /**
     * Offer a tracking frame. Cheap on the calling thread; skipped while the worker is still
     * busy, and for a short warm-up after tracking (re)starts, because ARCore's first poses are
     * still settling and a lock taken then anchors everything slightly off.
     */
    fun onFrame(frame: Frame, capturing: Boolean, nowNanos: Long) {
        if (!available || busy.get()) return
        val last = lastFrameNanos
        if (last == null || nowNanos - last > TRACKING_GAP_NANOS) trackingSinceNanos = nowNanos
        lastFrameNanos = nowNanos
        if (nowNanos - (trackingSinceNanos ?: nowNanos) < WARMUP_NANOS) return
        val captured = FrameCapture.capture(frame) ?: return
        if (!busy.compareAndSet(false, true)) return
        worker.execute {
            try { process(captured, capturing, nowNanos) }
            catch (t: Throwable) { Log.w(TAG, "frame processing failed", t) }
            finally { busy.set(false) }
        }
    }

    private fun process(f: CapturedFrame, capturing: Boolean, nowNanos: Long) {
        val features = extractor!!.extract(f.gray, f.width, f.height)
        if (!loggedDims) {
            loggedDims = true
            Log.i(TAG, "image ${f.width}x${f.height}, intrinsics for ${f.intrinsicsDims.first}x${f.intrinsicsDims.second} " +
                "fx=${f.intrinsics.fx} fy=${f.intrinsics.fy} cx=${f.intrinsics.cx} cy=${f.intrinsics.cy}, " +
                "depth=${f.depthDims?.let { "${it.first}x${it.second}" } ?: "none"}, features=${features.count}")
        }
        synchronized(lock) {
            if (capturing) {
                if (!f.hasDepth) {
                    _note.value = "No depth from ARCore yet — move the phone to let it estimate depth"
                } else if (selector.shouldCapture(f.cameraPose, features.count)) {
                    val kf = KeyframeBuilder.build(
                        id = builder.nextKeyframeId(), features = features, intrinsics = f.intrinsics,
                        cameraPose = f.cameraPose, gravity = GRAVITY, depth = f.depth,
                    )
                    Log.i(TAG, "keyframe #${builder.keyframeCount}: ${features.count} features, ${kf.features.count} with depth, " +
                        "cam=${f.cameraPose.translation}")
                    if (kf.features.count >= MIN_KEYFRAME_POINTS) {
                        builder.add(kf); selector.recordCaptured(f.cameraPose)
                        _keyframes.value = builder.keyframeCount
                        _note.value = null
                    }
                }
            }

            val c = controller ?: return
            val decision = c.onFrame(features, f.intrinsics, f.cameraPose, nowNanos)
            _status.value = c.status
            if (decision != null) {
                val r = c.lastRelocalization
                val cur = c.alignment.current
                Log.i(TAG, "reloc: " + (if (r is RelocalizationResult.Located) "inliers=${r.inlierCount}" else "not found") +
                    " -> $decision" + (cur?.let { " | T_session_map t=${it.translation} rot=${"%.1f".format(poseDelta(it, Mat4.identity()).rotationDegrees)}°" } ?: ""))
            }
            val cur = c.alignment.current ?: return
            if (!restored) {
                restored = true
                anchoredAlignment = cur
                c.markersInSession()?.let { m ->
                    Log.i(TAG, "restore ${m.size} markers: " + m.joinToString { "${it.first.label}@${it.second.translation}" })
                    main.post { onRestore?.invoke(m) }
                }
            } else {
                // Compare against where the anchors actually are, not the last step: the loop
                // converges in small blends, and their sum is what has to be corrected.
                val anchored = anchoredAlignment
                if (anchored != null && c.alignment.maxProbeDisplacement(anchored, cur) > REANCHOR_METERS) {
                    anchoredAlignment = cur
                    c.markersInSession()?.let { m ->
                        Log.i(TAG, "re-anchor (moved ${"%.3f".format(c.alignment.maxProbeDisplacement(anchored, cur))} m)")
                        main.post { onReanchor?.invoke(m) }
                    }
                }
            }
        }
    }

    /**
     * Write the current room — the keyframes captured so far plus [markers] as placed — and
     * start recognising against it. The alignment is the identity by construction (the map frame
     * *is* this session), so it is seeded rather than waited for.
     */
    fun save(markers: List<MarkerPose>, nowNanos: Long): Result<String> = runCatching {
        if (!available) error("OpenCV unavailable")
        synchronized(lock) {
            require(builder.keyframeCount >= MIN_KEYFRAMES_TO_SAVE) {
                "Only ${builder.keyframeCount} keyframes — look around the room a little more"
            }
            val map = builder.build(markers, System.currentTimeMillis())
            store.save(map)
            controller = RelocalizationController(map, relocalizer!!).also { it.seed(Mat4.identity(), nowNanos) }
            restored = true
            anchoredAlignment = Mat4.identity()
            _hasMap.value = true
            _status.value = controller!!.status
            "Saved ${map.keyframes.size} keyframes, ${map.pointCount} points, ${map.markers.size} devices"
        }
    }.onFailure { _note.value = it.message }

    fun close() {
        worker.shutdownNow()
    }

    private companion object {
        const val TAG = "DeixisAnchoring"
        const val MAP_ID = "home"
        val GRAVITY = Float3(0f, -1f, 0f) // ARCore's world Y is up
        const val MIN_KEYFRAME_POINTS = 50
        const val MIN_KEYFRAMES_TO_SAVE = 3
        /** Corrections smaller than this are absorbed by ARCore tracking; larger ones re-anchor. */
        const val REANCHOR_METERS = 0.05f
        /** Let ARCore's tracking settle before the first recognition; a lock taken too early is coarse. */
        const val WARMUP_NANOS = 1_500_000_000L
        const val TRACKING_GAP_NANOS = 1_000_000_000L
    }
}
