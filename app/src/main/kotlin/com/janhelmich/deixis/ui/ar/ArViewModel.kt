package com.janhelmich.deixis.ui.ar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ar.core.Anchor
import com.janhelmich.deixis.domain.Device
import com.janhelmich.deixis.domain.DeviceState
import com.janhelmich.deixis.domain.SmartHomeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The two ways of interacting with the scene, carried over from the thesis app:
 * [EDIT] arranges devices in the room, [USE] operates them.
 */
enum class ArMode { USE, EDIT }

/** A device pinned to a real-world spot. [anchor] belongs to the current ARCore session. */
class Placement(val id: String, val deviceId: String, val anchor: Anchor)

/**
 * Scene state for [ArScreen]. Placements live here rather than in the composition so they
 * survive the AR view being rebuilt; they do not survive process death, since ARCore anchors
 * cannot be serialised without Cloud Anchors.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArViewModel(private val repository: StateFlow<SmartHomeRepository>) : ViewModel() {

    private val _mode = MutableStateFlow(ArMode.EDIT)
    val mode: StateFlow<ArMode> = _mode.asStateFlow()

    val backendName: StateFlow<String> = repository.map { it.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.value.name)

    val devices: StateFlow<List<Device>> = repository
        .flatMapLatest { it.devices }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val states: StateFlow<Map<String, DeviceState>> = repository
        .flatMapLatest { it.states }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val error: StateFlow<String?> = repository
        .flatMapLatest { it.error }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _placements = MutableStateFlow<List<Placement>>(emptyList())
    val placements: StateFlow<List<Placement>> = _placements.asStateFlow()

    /** Placements whose device still exists on the backend, paired with that device. */
    val placedDevices: StateFlow<List<Pair<Placement, Device>>> =
        combine(_placements, devices) { placements, devices ->
            val byId = devices.associateBy { it.id }
            placements.mapNotNull { p -> byId[p.deviceId]?.let { p to it } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    /** Device picked from the palette, waiting for the user to tap a surface. */
    private val _pendingDeviceId = MutableStateFlow<String?>(null)
    val pendingDeviceId: StateFlow<String?> = _pendingDeviceId.asStateFlow()

    fun setMode(mode: ArMode) {
        _mode.value = mode
        if (mode == ArMode.USE) _pendingDeviceId.value = null
    }

    /** Pick a device to place next; picking it again cancels. */
    fun choose(deviceId: String) {
        _pendingDeviceId.update { if (it == deviceId) null else deviceId }
    }

    /** Pin the pending device at [anchor]. Returns false (and detaches) if nothing was pending. */
    fun place(anchor: Anchor): Boolean {
        val deviceId = _pendingDeviceId.value ?: run { anchor.detach(); return false }
        val placement = Placement(UUID.randomUUID().toString(), deviceId, anchor)
        _placements.update { it + placement }
        _pendingDeviceId.value = null
        _selectedId.value = placement.id
        return true
    }

    fun select(placementId: String?) {
        _selectedId.update { if (it == placementId) null else placementId }
    }

    fun remove(placementId: String) {
        _placements.update { list ->
            list.partition { it.id == placementId }.let { (gone, kept) ->
                gone.forEach { it.anchor.detach() }
                kept
            }
        }
        if (_selectedId.value == placementId) _selectedId.value = null
    }

    fun toggle(deviceId: String) = viewModelScope.launch { repository.value.toggle(deviceId) }

    fun setBrightness(deviceId: String, brightness: Float) =
        viewModelScope.launch { repository.value.setBrightness(deviceId, brightness) }

    override fun onCleared() {
        _placements.value.forEach { it.anchor.detach() }
    }
}
