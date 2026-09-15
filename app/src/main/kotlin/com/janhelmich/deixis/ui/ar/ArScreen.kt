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
import io.github.sceneview.node.ViewNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberViewNodeManager

private const val TAG = "DeixisAR"

/** How wide the floating card should appear in the room, in metres. */
private const val CARD_WIDTH_METRES = 0.30f

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
            onSessionUpdated = { _, frame ->
                latestFrame = frame
                tracking = frame.camera.trackingState == TrackingState.TRACKING
            },
            onTrackingFailureChanged = { trackingFailure = it },
            onGestureListener = rememberOnGestureListener(
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
                key(placement.id) {
                    PlacedDevice(
                        placement = placement,
                        device = placement.deviceId?.let { id -> devices.firstOrNull { it.id == id } },
                        showCard = mode == ArMode.USE && placement.id == selectedId,
                        editing = mode == ArMode.EDIT,
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

        ArOverlay(
            mode = mode,
            onMode = viewModel::setMode,
            backendName = backendName,
            placing = placing,
            onTogglePlacing = viewModel::togglePlacing,
            hint = hintFor(mode, placing, placements.isEmpty(), trackingFailure),
            error = error,
        )

        // Edit-mode configuration for the selected marker.
        val selected = placements.firstOrNull { it.id == selectedId }
        if (mode == ArMode.EDIT && selected != null) {
            PlacementSheet(
                placement = selected,
                devices = devices,
                onRename = { viewModel.rename(selected.id, it) },
                onBind = { viewModel.bind(selected.id, it) },
                onRemove = { viewModel.remove(selected.id) },
                onDismiss = viewModel::deselect,
            )
        }
    }
}

@Composable
private fun ARSceneScope.PlacedDevice(
    placement: Placement,
    device: Device?,
    showCard: Boolean,
    editing: Boolean,
    viewModel: ArViewModel,
    cameraNode: ARCameraNode,
    viewNodeManager: ViewNode.WindowManager,
    cardScale: Float,
) {
    val states by viewModel.states.collectAsState()
    val state = device?.let { states[it.id] } ?: DeviceState.Unavailable
    val label = placement.label.ifBlank { device?.name ?: "marker" }
    val handle = remember { AnchorHandle() }

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
        },
    ) {
        // `apply` runs once; editability follows the mode from then on.
        LaunchedEffect(editing) { handle.anchor?.isEditable = editing }

        // The body owns rotation and scale. Drags on it bubble up to the anchor because it is
        // not position-editable; twists and pinches stop here.
        Node(
            isEditable = editing,
            apply = {
                isPositionEditable = false
                editableScaleRange = 0.5f..3f
            },
        ) {
            DeviceGeometry(device?.kind, state)
        }

        if (showCard) {
            ViewNode(
                windowManager = viewNodeManager,
                unlit = true,
                position = Position(y = device?.kind.cardLiftMetres),
                scale = Scale(cardScale),
                apply = {
                    onFrame = { _ ->
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
    }
}

/** Lets composition-time effects reach the node that `apply` created. */
private class AnchorHandle {
    var anchor: AnchorNode? = null
}

private val DeviceKind?.cardLiftMetres: Float
    get() = when (this) {
        DeviceKind.PLUG -> 0.16f
        DeviceKind.LIGHT -> 0.36f
        DeviceKind.SENSOR -> 0.16f
        null -> 0.24f
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
    mode == ArMode.EDIT -> "Tap a marker to name it, assign a device, or remove it"
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

        if (mode == ArMode.EDIT) {
            FilterChip(
                selected = placing,
                onClick = onTogglePlacing,
                label = { Text(if (placing) "Cancel" else "Add marker") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
            )
        }
    }
}
