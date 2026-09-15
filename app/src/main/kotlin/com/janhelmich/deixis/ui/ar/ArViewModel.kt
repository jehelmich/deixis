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

/**
 * A spot in the room the user has marked. Where it is ([anchor]) is decided by tapping a
 * surface; what it is ([deviceId]) and what to call it ([label]) are set afterwards, so a
 * marker can be placed before it is known which backend device it stands for.
 */
data class Placement(
    val id: String,
    val anchor: Anchor,
    val label: String = "",
    val deviceId: String? = null,
)

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

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    /** True while the user has asked to add a marker and has yet to tap a surface. */
    private val _placing = MutableStateFlow(false)
    val placing: StateFlow<Boolean> = _placing.asStateFlow()

    fun setMode(mode: ArMode) {
        _mode.value = mode
        if (mode == ArMode.USE) _placing.value = false
    }

    fun togglePlacing() {
        _placing.update { !it }
        if (_placing.value) _selectedId.value = null
    }

    /** Pin a new, unbound marker at [anchor]. Returns false (and detaches) if not placing. */
    fun place(anchor: Anchor): Boolean {
        if (!_placing.value) {
            anchor.detach()
            return false
        }
        val placement = Placement(UUID.randomUUID().toString(), anchor)
        _placements.update { it + placement }
        _placing.value = false
        _selectedId.value = placement.id
        return true
    }

    /** Make [placementId] the one marker that takes edit gestures; tapping it again keeps it. */
    fun select(placementId: String?) {
        _selectedId.value = placementId
    }

    fun deselect() {
        _selectedId.value = null
    }

    fun rename(placementId: String, label: String) = edit(placementId) { it.copy(label = label) }

    /** Bind the marker to a backend device, or `null` to leave it unassigned. */
    fun bind(placementId: String, deviceId: String?) = edit(placementId) { it.copy(deviceId = deviceId) }

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

    private fun edit(placementId: String, transform: (Placement) -> Placement) {
        _placements.update { list -> list.map { if (it.id == placementId) transform(it) else it } }
    }

    override fun onCleared() {
        _placements.value.forEach { it.anchor.detach() }
    }
}
