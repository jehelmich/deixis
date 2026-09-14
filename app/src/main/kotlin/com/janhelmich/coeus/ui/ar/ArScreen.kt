package com.janhelmich.coeus.ui.ar

import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
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
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.janhelmich.coeus.domain.Device
import com.janhelmich.coeus.domain.DeviceKind
import com.janhelmich.coeus.domain.DeviceState
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

/** How wide the floating card should appear in the room, in metres. */
private const val CARD_WIDTH_METRES = 0.30f

/** SceneView's default pixel density for a ViewNode: 250 px per scene unit (metre). */
private const val VIEW_NODE_PX_PER_METRE = 250f

@Composable
fun ArScreen(viewModel: ArViewModel) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val placed by viewModel.placedDevices.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val pendingDeviceId by viewModel.pendingDeviceId.collectAsStateWithLifecycle()
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
                    when {
                        tapped != null -> viewModel.select(tapped)
                        viewModel.pendingDeviceId.value != null -> {
                            val frame = latestFrame ?: return@rememberOnGestureListener
                            surfaceHit(frame, event)?.let { viewModel.place(it.createAnchor()) }
                        }
                        else -> viewModel.select(null)
                    }
                },
            ),
        ) {
            placed.forEach { (placement, device) ->
                key(placement.id) {
                    PlacedDevice(
                        placement = placement,
                        device = device,
                        selected = placement.id == selectedId,
                        editing = mode == ArMode.EDIT,
                        viewModel = viewModel,
                        cameraNode = cameraNode,
                        viewNodeManager = viewNodeManager,
                        cardScale = cardScale,
                    )
                }
            }
            if (mode == ArMode.EDIT && pendingDeviceId != null && tracking && viewport != IntSize.Zero) {
                PlacementReticle(xPx = viewport.width / 2f, yPx = viewport.height / 2f)
            }
        }

        ArOverlay(
            mode = mode,
            onMode = viewModel::setMode,
            backendName = backendName,
            devices = devices,
            pendingDeviceId = pendingDeviceId,
            onChoose = viewModel::choose,
            hint = hintFor(mode, pendingDeviceId?.let { id -> devices.firstOrNull { it.id == id } },
                placed.isEmpty(), trackingFailure),
            error = error,
        )
    }
}

@Composable
private fun ARSceneScope.PlacedDevice(
    placement: Placement,
    device: Device,
    selected: Boolean,
    editing: Boolean,
    viewModel: ArViewModel,
    cameraNode: ARCameraNode,
    viewNodeManager: ViewNode.WindowManager,
    cardScale: Float,
) {
    val states by viewModel.states.collectAsState()
    val state = states[device.id] ?: DeviceState.Unavailable
    val handle = remember { AnchorHandle() }

    AnchorNode(
        anchor = placement.anchor,
        apply = {
            handle.node = this
            name = PLACEMENT_NAME_PREFIX + placement.id
            // Real devices have one size; let the user move and turn them, never resize.
            isScaleEditable = false
            moveHitTest = { frame, event -> surfaceHit(frame, event) }
        },
    ) {
        // `apply` runs once; editability follows the mode from then on.
        LaunchedEffect(editing) { handle.node?.isEditable = editing }

        DeviceGeometry(device.kind, state)

        if (selected) {
            ViewNode(
                windowManager = viewNodeManager,
                unlit = true,
                position = Position(y = device.kind.cardLiftMetres),
                scale = Scale(cardScale),
                apply = {
                    // Face the camera every frame so the card is readable from anywhere.
                    onFrame = { _ ->
                        val toCard = worldPosition - cameraNode.worldPosition
                        if (toCard.x * toCard.x + toCard.y * toCard.y + toCard.z * toCard.z > 1e-6f) {
                            lookTowards(lookDirection = toCard)
                        }
                    }
                },
            ) {
                DeviceCard(device = device, viewModel = viewModel, placementId = placement.id)
            }
        }
    }
}

/** Lets composition-time effects reach the node that `apply` created. */
private class AnchorHandle {
    var node: AnchorNode? = null
}

private val DeviceKind.cardLiftMetres: Float
    get() = when (this) {
        DeviceKind.PLUG -> 0.16f
        DeviceKind.LIGHT -> 0.36f
        DeviceKind.SENSOR -> 0.16f
    }

private fun hintFor(
    mode: ArMode,
    pending: Device?,
    nothingPlaced: Boolean,
    trackingFailure: TrackingFailureReason?,
): String = when {
    trackingFailure != null -> trackingFailure.userMessage
    mode == ArMode.EDIT && pending != null -> "Tap a surface to place ${pending.name}"
    mode == ArMode.EDIT && nothingPlaced -> "Pick a device below, then tap where it lives"
    mode == ArMode.EDIT -> "Tap a device to move or remove it"
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
    devices: List<Device>,
    pendingDeviceId: String?,
    onChoose: (String) -> Unit,
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
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(devices, key = { it.id }) { device ->
                    FilterChip(
                        selected = device.id == pendingDeviceId,
                        onClick = { onChoose(device.id) },
                        label = { Text(device.name) },
                    )
                }
            }
        }
    }
}
