package com.janhelmich.deixis.ui.ar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janhelmich.deixis.domain.DeviceState
import com.janhelmich.deixis.ui.common.CardPadding
import com.janhelmich.deixis.ui.common.DeviceControls
import com.janhelmich.deixis.ui.theme.DeixisTheme

/** Fixed width so the card's size in metres is predictable — see [ArScreen]. */
val DeviceCardWidth = 240.dp

/**
 * The floating card above a selected marker in Use mode. Rendered inside a SceneView
 * `ViewNode`, which hosts its own composition: no `CompositionLocal` from the screen reaches
 * here, so the theme is re-applied and everything is read from the view model's flows rather
 * than passed in as values (the content lambda is captured once).
 */
@Composable
fun DeviceCard(placementId: String, viewModel: ArViewModel) {
    val placements by viewModel.placements.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val states by viewModel.states.collectAsState()

    val placement = placements.firstOrNull { it.id == placementId } ?: return
    val device = placement.deviceId?.let { id -> devices.firstOrNull { it.id == id } }

    DeixisTheme {
        Card(Modifier.width(DeviceCardWidth)) {
            Column(Modifier.padding(CardPadding)) {
                if (device == null) {
                    Text(
                        placement.label.ifBlank { "Unassigned" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Switch to Edit to choose which device this is.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    DeviceControls(
                        device = device,
                        state = states[device.id] ?: DeviceState.Unavailable,
                        title = placement.label.ifBlank { device.name },
                        onToggle = { viewModel.toggle(device.id) },
                        onBrightness = { viewModel.setBrightness(device.id, it) },
                    )
                }
            }
        }
    }
}
