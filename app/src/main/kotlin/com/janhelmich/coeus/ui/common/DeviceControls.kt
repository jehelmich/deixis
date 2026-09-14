package com.janhelmich.coeus.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janhelmich.coeus.domain.Device
import com.janhelmich.coeus.domain.DeviceKind
import com.janhelmich.coeus.domain.DeviceState
import java.util.Locale

/**
 * Readings and controls for one device. Shared between the floating AR card and the flat device
 * list so both are guaranteed to behave the same.
 */
@Composable
fun DeviceControls(
    device: Device,
    state: DeviceState,
    onToggle: () -> Unit,
    onBrightness: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    device.kind.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (state) {
                is DeviceState.Plug -> Switch(checked = state.isOn, onCheckedChange = { onToggle() })
                is DeviceState.Light -> Switch(checked = state.isOn, onCheckedChange = { onToggle() })
                is DeviceState.Sensor -> Unit
                DeviceState.Unavailable -> Switch(checked = false, enabled = false, onCheckedChange = null)
            }
        }
        when (state) {
            is DeviceState.Plug -> PlugReadings(state)
            is DeviceState.Light -> LightControls(state, onBrightness)
            is DeviceState.Sensor -> SensorReadings(state)
            DeviceState.Unavailable -> Text(
                "Unavailable",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun PlugReadings(state: DeviceState.Plug) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Reading("Power", state.powerW.format("W", 0))
        Reading("Current", state.currentA.format("A", 2))
        Reading("Voltage", state.voltageV.format("V", 0))
        Reading("Today", state.energyTodayKwh.format("kWh", 2))
        Reading("Total", state.energyTotalKwh.format("kWh", 1))
    }
}

@Composable
private fun LightControls(state: DeviceState.Light, onBrightness: (Float) -> Unit) {
    val brightness = state.brightness ?: return
    // Keep the thumb under the finger while dragging; commit once on release so a slow backend
    // is not flooded with requests.
    var dragging by remember { mutableStateOf<Float?>(null) }
    Column {
        Reading("Brightness", "${((dragging ?: brightness) * 100).toInt()} %")
        Slider(
            value = dragging ?: brightness,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let(onBrightness)
                dragging = null
            },
            enabled = state.isOn,
        )
    }
}

@Composable
private fun SensorReadings(state: DeviceState.Sensor) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Reading("Temperature", state.temperatureC.format("°C", 1))
        Reading("Humidity", state.humidityPercent.format("%", 0))
    }
}

@Composable
private fun Reading(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

val DeviceKind.label: String
    get() = when (this) {
        DeviceKind.PLUG -> "Smart plug"
        DeviceKind.LIGHT -> "Light"
        DeviceKind.SENSOR -> "Climate sensor"
    }

private fun Double?.format(unit: String, decimals: Int): String =
    if (this == null) "—" else String.format(Locale.getDefault(), "%.${decimals}f %s", this, unit)

/** Padding shared by every card that hosts [DeviceControls]. */
val CardPadding = 16.dp
