package com.janhelmich.coeus.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.janhelmich.coeus.domain.Device
import com.janhelmich.coeus.domain.DeviceState
import com.janhelmich.coeus.domain.SmartHomeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the flat device list, which works on any device — no camera, no ARCore. */
@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModel(private val repository: StateFlow<SmartHomeRepository>) : ViewModel() {

    val backendName: StateFlow<String> = repository.map { it.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.value.name)

    val devices: StateFlow<List<Pair<Device, DeviceState>>> = repository
        .flatMapLatest { it.devicesWithState }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val error: StateFlow<String?> = repository
        .flatMapLatest { it.error }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggle(deviceId: String) = viewModelScope.launch { repository.value.toggle(deviceId) }

    fun setBrightness(deviceId: String, brightness: Float) =
        viewModelScope.launch { repository.value.setBrightness(deviceId, brightness) }

    fun refresh() = viewModelScope.launch { repository.value.refresh() }
}
