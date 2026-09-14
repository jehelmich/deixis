package com.janhelmich.deixis.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.janhelmich.deixis.data.settings.Backend

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Backend", style = MaterialTheme.typography.titleMedium)
            BackendOption(
                title = "Simulated home",
                description = "Five pretend devices with live-looking readings. Works anywhere.",
                selected = draft.backend == Backend.SIMULATED,
                onSelect = { viewModel.setBackend(Backend.SIMULATED) },
            )
            BackendOption(
                title = "Home Assistant",
                description = "Control real switches, lights and sensors over the REST API.",
                selected = draft.backend == Backend.HOME_ASSISTANT,
                onSelect = { viewModel.setBackend(Backend.HOME_ASSISTANT) },
            )

            if (draft.backend == Backend.HOME_ASSISTANT) {
                OutlinedTextField(
                    value = draft.baseUrl,
                    onValueChange = viewModel::setBaseUrl,
                    label = { Text("Base URL") },
                    placeholder = { Text("http://homeassistant.local:8123") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.token,
                    onValueChange = viewModel::setToken,
                    label = { Text("Long-lived access token") },
                    supportingText = { Text("Profile → Security → Long-lived access tokens") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = viewModel::test, enabled = !busy) {
                    Text(if (busy) "Testing…" else "Test connection")
                }
            }

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text("Save") }

            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun BackendOption(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
