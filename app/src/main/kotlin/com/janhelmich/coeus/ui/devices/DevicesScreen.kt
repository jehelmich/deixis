package com.janhelmich.coeus.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.janhelmich.coeus.ui.common.CardPadding
import com.janhelmich.coeus.ui.common.DeviceControls

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(viewModel: DevicesViewModel) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val backend by viewModel.backendName.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(backend) },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let {
                item {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (devices.isEmpty() && error == null) {
                item { Text("Waiting for devices…", style = MaterialTheme.typography.bodyMedium) }
            }
            items(devices, key = { it.first.id }) { (device, state) ->
                Card(Modifier.fillMaxWidth()) {
                    DeviceControls(
                        device = device,
                        state = state,
                        onToggle = { viewModel.toggle(device.id) },
                        onBrightness = { viewModel.setBrightness(device.id, it) },
                        modifier = Modifier.padding(CardPadding),
                    )
                }
            }
        }
    }
}
