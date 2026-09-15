package com.janhelmich.deixis.ui.ar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janhelmich.deixis.domain.Device
import com.janhelmich.deixis.ui.common.label

/**
 * Edit-mode configuration for one marker: what to call it, which backend device it stands
 * for, and a way to remove it. A 2D sheet rather than part of the 3D card because SceneView's
 * card window cannot take keyboard focus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacementSheet(
    placement: Placement,
    devices: List<Device>,
    onRename: (String) -> Unit,
    onBind: (String?) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val bound = devices.firstOrNull { it.id == placement.deviceId }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = placement.label,
                onValueChange = onRename,
                label = { Text("Name") },
                placeholder = { Text(bound?.name ?: "e.g. Reading lamp") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = bound?.let { "${it.name} · ${it.kind.label}" } ?: "Not assigned",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Device") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Not assigned") },
                        onClick = { onBind(null); expanded = false },
                    )
                    devices.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.name) },
                            trailingIcon = {
                                Text(device.kind.label, style = MaterialTheme.typography.labelSmall)
                            },
                            onClick = { onBind(device.id); expanded = false },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Drag to move · twist to rotate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}
