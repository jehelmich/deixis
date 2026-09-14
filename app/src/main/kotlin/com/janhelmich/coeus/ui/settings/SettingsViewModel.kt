package com.janhelmich.coeus.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.janhelmich.coeus.data.settings.Backend
import com.janhelmich.coeus.data.settings.ConnectionSettings
import com.janhelmich.coeus.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val testConnection: suspend (ConnectionSettings) -> Result<String>,
) : ViewModel() {

    /** The form as currently edited — saved only when the user says so. */
    private val _draft = MutableStateFlow(ConnectionSettings())
    val draft: StateFlow<ConnectionSettings> = _draft.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch { _draft.value = settings.settings.first() }
    }

    fun setBackend(backend: Backend) = _draft.update { it.copy(backend = backend) }
    fun setBaseUrl(url: String) = _draft.update { it.copy(baseUrl = url) }
    fun setToken(token: String) = _draft.update { it.copy(token = token) }

    fun test() = viewModelScope.launch {
        _busy.value = true
        _status.value = testConnection(_draft.value).fold(
            onSuccess = { it },
            onFailure = { "Failed: ${it.message ?: it::class.simpleName}" },
        )
        _busy.value = false
    }

    fun save() = viewModelScope.launch {
        settings.save(_draft.value)
        _status.value = "Saved."
    }
}
