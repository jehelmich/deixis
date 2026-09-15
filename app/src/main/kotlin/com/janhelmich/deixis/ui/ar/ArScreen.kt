package com.janhelmich.deixis.ui.ar

import android.util.Log
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.janhelmich.deixis.data.relocalization.AlignmentStatus
import com.janhelmich.deixis.relocalization.AnchoringSession
import com.janhelmich.deixis.relocalization.toPose
import com.janhelmich.deixis.relocalization.toMat4
import dev.romainguy.kotlin.math.inverse
import dev.romainguy.kotlin.math.Mat4
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.janhelmich.deixis.domain.Device
import com.janhelmich.deixis.domain.DeviceKind
import com.janhelmich.deixis.domain.DeviceState
import io.github.sceneview.ar.ARSceneScope
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.ARCameraNode
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import dev.romainguy.kotlin.math.degrees
import io.github.sceneview.components.PRIORITY_LAST
import io.github.sceneview.node.Node
import io.github.sceneview.node.ViewNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberViewNodeManager

private const val TAG = "DeixisAR"

/** How wide the floating card should appear in the room, in metres. */
private const val CARD_WIDTH_METRES = 0.30f

/** How far in front of the device (towards the viewer) the card floats, at body scale 1. */
private const val CARD_STANDOFF_METRES = 0.12f

/** Clearance between the top of the body and the bottom edge of the card. */
private const val CARD_GAP_METRES = 0.03f

/** SceneView's default pixel density for a ViewNode: 250 px per scene unit (metre). */
private const val VIEW_NODE_PX_PER_METRE = 250f

@Composable
fun ArScreen(viewModel: ArViewModel) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val placements by viewModel.placements.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val placing by viewModel.placing.collectAsStateWithLifecycle()
    val backendName by viewModel.backendName.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    // One handle per placement so scene-level gestures can reach the selected marker's nodes.
    val handles = remember { mutableMapOf<String, AnchorHandle>() }
    LaunchedEffect(placements) { handles.keys.retainAll(placements.mapTo(HashSet()) { it.id }) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val cameraNode = rememberARCameraNode(engine)
    val viewNodeManager = rememberViewNodeManager()

    // Written on every AR frame but only read inside gesture callbacks, so nothing recomposes.
    var latestFrame by remember { mutableStateOf<Frame?>(null) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var tracking by remember { mutableStateOf(false) }
    var trackingFailure by remember { mutableStateOf<TrackingFailureReason?>(null) }

    // Long-lived anchoring (docs/anchoring.md): capture while editing, recognise whenever a saved
    // map exists, restore/re-anchor markers through the view model.
    val context = LocalContext.current
    val anchoring = remember { AnchoringSession(context) }
    var arSession by remember { mutableStateOf<Session?>(null) }
    val anchoringStatus by anchoring.status.collectAsStateWithLifecycle()
    val keyframes by anchoring.keyframes.collectAsStateWithLifecycle()
    val hasMap by anchoring.hasMap.collectAsStateWithLifecycle()
    val anchoringNote by anchoring.note.collectAsStateWithLifecycle()
    var saveMessage by remember { mutableStateOf<String?>(null) }
    DisposableEffect(anchoring) {
        anchoring.onRestore = { markers -> viewModel.restore(markers) { pose -> arSession?.createAnchor(pose.toPose()) } }
        anchoring.onCorrection = { markers -> viewModel.applyCorrections(markers) }
        anchoring.onReanchor = { markers -> viewModel.reanchor(markers) { pose -> arSession?.createAnchor(pose.toPose()) } }
        anchoring.loadIfPresent()
        onDispose { anchoring.close() }
    }

    // A ViewNode is laid out in pixels; scale it so the card is CARD_WIDTH_METRES wide in the
    // room regardless of the phone's density.
    val density = LocalDensity.current
    val cardScale = remember(density) {
        with(density) { CARD_WIDTH_METRES / (DeviceCardWidth.toPx() / VIEW_NODE_PX_PER_METRE) }
    }

    Box(Modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier.fillMaxSize().onSizeChanged { viewport = it },
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            cameraNode = cameraNode,
            viewNodeWindowManager = viewNodeManager,
            planeRenderer = mode == ArMode.EDIT,
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
            // Depth gives every captured feature a metric 3-D point; SceneView downgrades cleanly
            // on devices without it (then maps cannot be built, and the status says so).
            depthMode = Config.DepthMode.AUTOMATIC,
            onSessionCreated = { arSession = it },
            onSessionUpdated = { _, frame ->
                latestFrame = frame
                tracking = frame.camera.trackingState == TrackingState.TRACKING
                if (tracking) anchoring.onFrame(frame, capturing = viewModel.mode.value == ArMode.EDIT, nowNanos = System.nanoTime())
            },
            onTrackingFailureChanged = { trackingFailure = it },
            onGestureListener = rememberOnGestureListener(
                // Pinch and twist act on the selected marker from anywhere on the screen. When a
                // finger is on the selected marker itself SceneView already applies the gesture
                // to it, so only handle the case where it is not.
                onScale = { detector, _, node ->
                    selectedBodyFor(node, viewModel, handles)?.let { body ->
                        viewModel.selectedId.value?.let(viewModel::markMoved)
                        val damped = 1f + (detector.scaleFactor - 1f) * body.scaleGestureSensitivity
                        body.scale = Scale((body.scale.x * damped).coerceIn(body.editableScaleRange))
                    }
                },
                onRotate = { detector, _, node ->
                    selectedBodyFor(node, viewModel, handles)?.let { body ->
                        viewModel.selectedId.value?.let(viewModel::markMoved)
                        val delta = detector.currentAngle - detector.lastAngle
                        body.quaternion *= Quaternion.fromAxisAngle(Float3(y = 1f), degrees(-delta))
                    }
                },
                onSingleTapConfirmed = { event: MotionEvent, node ->
                    val tapped = node?.placementId()
                    Log.d(TAG, "tap: node=${node?.let { it::class.simpleName }} placement=$tapped")
                    when {
                        tapped != null -> viewModel.select(tapped)
                        viewModel.placing.value -> {
                            val frame = latestFrame ?: return@rememberOnGestureListener
                            val hit = surfaceHit(frame, event)
                            Log.d(TAG, "place: " + (hit?.let { "${(it.trackable as Plane).type} at %.2f m".format(it.distance) } ?: "no surface"))
                            hit?.let { viewModel.place(it.createAnchor()) }
                        }
                        else -> viewModel.select(null)
                    }
                },
            ),
        ) {
            placements.forEach { placement ->
                // Keyed on the anchor too: a re-anchor must rebuild the whole node subtree, or
                // the remembered child nodes stay attached to the destroyed parent.
                key(placement.id, placement.anchor) {
                    PlacedDevice(
                        placement = placement,
                        device = placement.deviceId?.let { id -> devices.firstOrNull { it.id == id } },
                        selected = placement.id == selectedId,
                        mode = mode,
                        handle = handles.getOrPut(placement.id) { AnchorHandle() },
                        viewModel = viewModel,
                        cameraNode = cameraNode,
                        viewNodeManager = viewNodeManager,
                        cardScale = cardScale,
                    )
                }
            }
            if (placing && tracking && viewport != IntSize.Zero) {
                PlacementReticle(xPx = viewport.width / 2f, yPx = viewport.height / 2f)
            }
        }

        val selected = placements.firstOrNull { it.id == selectedId }
        ArOverlay(
            mode = mode,
            onMode = viewModel::setMode,
            backendName = backendName,
            placing = placing,
            onTogglePlacing = viewModel::togglePlacing,
            hint = hintFor(mode, placing, placements.isEmpty(), trackingFailure),
            error = error ?: anchoringNote,
            anchoring = anchoringLabel(anchoring.available, hasMap, anchoringStatus, keyframes, mode),
            saveRoom = if (mode == ArMode.EDIT && anchoring.available && placements.isNotEmpty()) {
                {
                    saveMessage = anchoring.save(viewModel.markerPoses(), System.nanoTime())
                        .onSuccess { viewModel.markSaved() }
                        .fold({ it }, { "Could not save: ${it.message}" })
                }
            } else null,
            saveMessage = saveMessage,
            panel = if (mode == ArMode.EDIT && selected != null) {
                {
                    // Configuration for the selected marker; the scene stays live behind it.
                    PlacementPanel(
                        placement = selected,
                        devices = devices,
                        onRename = { viewModel.rename(selected.id, it) },
                        onBind = { viewModel.bind(selected.id, it) },
                        onRemove = { viewModel.remove(selected.id) },
                        onClose = viewModel::deselect,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            } else null,
        )
    }
}

@Composable
private fun ARSceneScope.PlacedDevice(
    placement: Placement,
    device: Device?,
    selected: Boolean,
    mode: ArMode,
    handle: AnchorHandle,
    viewModel: ArViewModel,
    cameraNode: ARCameraNode,
    viewNodeManager: ViewNode.WindowManager,
    cardScale: Float,
) {
    val states by viewModel.states.collectAsState()
    val corrections by viewModel.corrections.collectAsState()
    val desiredPose = corrections[placement.id]
    val state = device?.let { states[it.id] } ?: DeviceState.Unavailable
    val label = placement.label.ifBlank { device?.name ?: "marker" }
    // Only the selected marker takes gestures, and only while arranging the room.
    val editable = mode == ArMode.EDIT && selected

    AnchorNode(
        anchor = placement.anchor,
        // ARCore pauses anchors for a few seconds whenever it re-evaluates their plane; keep
        // the device where it was last seen rather than blinking it out.
        visibleTrackingStates = setOf(TrackingState.TRACKING, TrackingState.PAUSED),
        onTrackingStateChanged = { Log.d(TAG, "$label: anchor $it") },
        apply = {
            handle.anchor = this
            name = PLACEMENT_NAME_PREFIX + placement.id
            // The anchor owns only the position: ARCore rewrites its pose (rotation included)
            // on every tracked frame, so a twist applied here would be undone a frame later.
            isRotationEditable = false
            isScaleEditable = false
            moveHitTest = { frame, event -> surfaceHit(frame, event) }
            onMoveEnd = { _, _ -> viewModel.markMoved(placement.id) }
        },
    ) {
        // `apply` runs once; editability follows selection and mode from then on.
        LaunchedEffect(editable) { handle.anchor?.isEditable = editable }

        // The alignment loop's corrections are applied here as a smoothed offset from the
        // anchor, so a restored device glides into its corrected place instead of jumping —
        // the anchor (and ARCore's tracking of it) stays put. In Edit mode the offset is zero so
        // the user's drags are not fought.
        LaunchedEffect(desiredPose, mode) {
            val node = handle.correction ?: return@LaunchedEffect
            val local = if (desiredPose != null && mode == ArMode.USE) {
                inverse(placement.anchor.pose.toMat4()) * desiredPose
            } else Mat4.identity()
            node.transform(local, smooth = true)
        }
        Node(
            apply = {
                handle.correction = this
                isSmoothTransformEnabled = true
                smoothTransformSpeed = 3f
            },
        ) {
            if (selected) SelectionRing()

            // The body owns rotation and scale. Drags on it bubble up to the anchor because it is
            // not position-editable; twists and pinches stop here.
            Node(
                isEditable = editable,
                apply = {
                    handle.body = this
                    isPositionEditable = false
                    editableScaleRange = 0.5f..3f
                    // A quarter of the pinch delta per event; the default half felt jumpy.
                    scaleGestureSensitivity = 0.25f
                    onRotateEnd = { _, _ -> viewModel.markMoved(placement.id) }
                    onScaleEnd = { _, _ -> viewModel.markMoved(placement.id) }
                },
            ) {
                DeviceGeometry(device?.kind, state)
            }

        if (selected && mode == ArMode.USE) {
            ViewNode(
                windowManager = viewNodeManager,
                unlit = true,
                scale = Scale(cardScale),
                apply = {
                    // Nothing in the room may hide the controls: skip the depth test and
                    // draw in Filament's last priority bucket, after every other renderable.
                    materialInstance.setDepthCulling(false)
                    setPriority(PRIORITY_LAST)
                    onFrame = frame@{ _ ->
                        val body = handle.body
                        val anchorPos = parent?.worldPosition ?: return@frame
                        // Sit above the body — which may have been pinched larger — and
                        // step towards the camera so the card floats in front of it.
                        val bodyScale = body?.scale?.y ?: 1f
                        // The card is opaque over anything behind it (no depth test), so its
                        // bottom edge must clear the top of the body, whatever the pinch scale.
                        val cardHalfHeight = if (viewSize.y > 0f) {
                            viewSize.y / pxPerUnits * cardScale / 2f
                        } else {
                            0.14f
                        }
                        val lift = device?.kind.bodyTopMetres * bodyScale + cardHalfHeight + CARD_GAP_METRES
                        val toCamera = cameraNode.worldPosition - anchorPos
                        val flat = Position(toCamera.x, 0f, toCamera.z)
                        val length = kotlin.math.sqrt(flat.x * flat.x + flat.z * flat.z)
                        val forward = if (length > 1e-4f) flat / length else Position(0f, 0f, 1f)
                        val standoff = CARD_STANDOFF_METRES * bodyScale
                        worldPosition = Position(
                            anchorPos.x + forward.x * standoff,
                            anchorPos.y + lift,
                            anchorPos.z + forward.z * standoff,
                        )
                        // Face the camera every frame so the card is readable from anywhere.
                        val toCard = worldPosition - cameraNode.worldPosition
                        if (toCard.x * toCard.x + toCard.y * toCard.y + toCard.z * toCard.z > 1e-6f) {
                            lookTowards(lookDirection = toCard)
                        }
                        // The card is drawn into its texture from the hidden window's
                        // dispatchDraw, which a hardware-accelerated window only re-runs when
                        // the container itself is dirty — a child recomposing is not enough, so
                        // a flipped switch would never show. Invalidate it every frame.
                        layout.invalidate()
                    }
                },
            ) {
                DeviceCard(placementId = placement.id, viewModel = viewModel)
            }
        }
        } // correction node
    }
}

/**
 * The body node a scene-level pinch or twist should act on: the selected marker's, in Edit
 * mode, unless the gesture started on that marker (then SceneView handles it itself).
 */
private fun selectedBodyFor(
    touched: Node?,
    viewModel: ArViewModel,
    handles: Map<String, AnchorHandle>,
): Node? {
    if (viewModel.mode.value != ArMode.EDIT) return null
    val selectedId = viewModel.selectedId.value ?: return null
    if (touched?.placementId() == selectedId) return null
    return handles[selectedId]?.body
}

/** Lets composition-time effects and the card reach the nodes that `apply` created. */
private class AnchorHandle {
    var anchor: AnchorNode? = null
    var correction: Node? = null
    var body: Node? = null
}

/** Height of each body's highest point at scale 1 — see [DeviceGeometry]. */
private val DeviceKind?.bodyTopMetres: Float
    get() = when (this) {
        DeviceKind.PLUG -> 0.051f
        DeviceKind.LIGHT -> 0.235f
        DeviceKind.SENSOR -> 0.07f
        null -> 0.12f
    }

/** The anchoring status pill: what the loop is doing, in the user's terms. */
private fun anchoringLabel(available: Boolean, hasMap: Boolean, status: AlignmentStatus, keyframes: Int, mode: ArMode): String {
    if (!available) return "Anchoring unavailable"
    val capture = if (mode == ArMode.EDIT) " · $keyframes views captured" else ""
    val recog = when {
        !hasMap -> "No saved room"
        status is AlignmentStatus.Searching -> "Looking for the room…"
        status is AlignmentStatus.Locked -> "Room recognised (${status.inliers} matches)"
        status is AlignmentStatus.Coasting -> "Room recognised · re-checking"
        else -> ""
    }
    return recog + capture
}

private fun hintFor(
    mode: ArMode,
    placing: Boolean,
    nothingPlaced: Boolean,
    trackingFailure: TrackingFailureReason?,
): String = when {
    trackingFailure != null -> trackingFailure.userMessage
    placing -> "Tap a surface to put a marker there"
    mode == ArMode.EDIT && nothingPlaced -> "Add a marker where a device lives, then tell it which one"
    mode == ArMode.EDIT -> "Tap a marker to select it"
    nothingPlaced -> "Switch to Edit to place devices"
    else -> "Tap a device to control it"
}

private val TrackingFailureReason.userMessage: String
    get() = when (this) {
        TrackingFailureReason.NONE -> ""
        TrackingFailureReason.BAD_STATE -> "Tracking lost — restarting"
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "Too dark — find more light"
        TrackingFailureReason.EXCESSIVE_MOTION -> "Moving too fast — slow down"
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "Point at a surface with some texture"
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera unavailable"
    }

@Composable
private fun ArOverlay(
    mode: ArMode,
    onMode: (ArMode) -> Unit,
    backendName: String,
    placing: Boolean,
    onTogglePlacing: () -> Unit,
    hint: String,
    error: String?,
    anchoring: String,
    saveRoom: (() -> Unit)? = null,
    saveMessage: String? = null,
    panel: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SingleChoiceSegmentedButtonRow {
            ArMode.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == mode,
                    onClick = { onMode(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, ArMode.entries.size),
                ) {
                    Text(option.name.lowercase().replaceFirstChar(Char::uppercase))
                }
            }
        }
        Text(
            backendName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            anchoring,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        saveMessage?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp))
        }

        Box(Modifier.weight(1f))

        (error ?: hint).takeIf { it.isNotBlank() }?.let { message ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (error != null) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(message, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }

        when {
            panel != null -> panel()
            mode == ArMode.EDIT -> androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = placing,
                    onClick = onTogglePlacing,
                    label = { Text(if (placing) "Cancel" else "Add marker") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                )
                if (saveRoom != null) {
                    FilterChip(selected = false, onClick = saveRoom, label = { Text("Save room") })
                }
            }
        }
    }
}
