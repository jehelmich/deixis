package com.janhelmich.coeus.ui.ar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janhelmich.coeus.domain.Device
import com.janhelmich.coeus.domain.DeviceState
import com.janhelmich.coeus.ui.common.CardPadding
import com.janhelmich.coeus.ui.common.DeviceControls
import com.janhelmich.coeus.ui.theme.CoeusTheme

/** Fixed width so the card's size in metres is predictable — see [ArScreen]. */
val DeviceCardWidth = 240.dp

/**
 * The floating card above a placed device. Rendered inside a SceneView `ViewNode`, which hosts
 * its own composition: no `CompositionLocal` from the screen reaches here, so the theme is
 * re-applied and state is read from flows rather than passed in as values.
 */
@Composable
fun DeviceCard(
    device: Device,
    viewModel: ArViewModel,
    placementId: String,
) {
    val states by viewModel.states.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val state = states[device.id] ?: DeviceState.Unavailable

    CoeusTheme {
        Card(Modifier.width(DeviceCardWidth)) {
            Column(Modifier.padding(CardPadding)) {
                DeviceControls(
                    device = device,
                    state = state,
                    onToggle = { viewModel.toggle(device.id) },
                    onBrightness = { viewModel.setBrightness(device.id, it) },
                )
                if (mode == ArMode.EDIT) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Drag to move · twist to rotate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.remove(placementId) }) { Text("Remove") }
                    }
                }
            }
        }
    }
}
