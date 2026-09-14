# Architecture

Three layers, one interface between them, no framework doing the wiring.

## Domain

[`domain/`](../app/src/main/kotlin/com/janhelmich/coeus/domain/) has no Android imports.

- `Device` — identity: id, name, `DeviceKind` (`PLUG`, `LIGHT`, `SENSOR`). The kind decides
  both the 3D shape and which controls the card shows.
- `DeviceState` — a sealed snapshot: `Plug` (on/off plus optional power readings), `Light`
  (on/off plus optional brightness 0..1), `Sensor` (optional temperature and humidity), or
  `Unavailable`. Readings a backend cannot supply are `null`, never zero, so the UI can show
  "—" instead of a lie.
- `SmartHomeRepository` — `devices`, `states` and `error` flows; `toggle`, `setBrightness`
  and `refresh` commands. Default methods derive per-device flows from those.

## Data

Two repositories and the settings that pick between them.

**`SimulatedSmartHome`** holds a `MutableStateFlow<Map<String, DeviceState>>` and a ticker
coroutine. Each tick, plugs that are on draw their nominal load ±3 % and accumulate energy;
the climate sensor random-walks within an indoor range. `advance(Duration)` is public so
tests drive time directly with a seeded `Random` instead of a scheduler.

**`HomeAssistantRepository`** wraps `HomeAssistantApi`, a thin Ktor client for three
endpoints: `GET /api/` (auth check), `GET /api/states`, `POST /api/services/{domain}/{service}`.
The entity list is a `flow {}` that polls, then sleeps until either the poll interval passes
or `refresh()` fires — `shareIn(WhileSubscribed)` means it polls only while a screen is
collecting. Every command triggers a refresh so the UI reflects the result without waiting.
A failed poll keeps the last device list but flips every state to `Unavailable` and sets
`error`.

Mapping lives in `HaEntity.kt`: `switch.*` → plug, `light.*` → light, `sensor.*` with a
temperature or humidity `device_class` → sensor; everything else is dropped. Attributes are
kept as raw `JsonObject` because their shape depends on the integration behind the entity.

**`SettingsRepository`** stores the backend choice, base URL and token in DataStore
Preferences. **`AppContainer`** collects it, builds the matching repository, and exposes it
as a `StateFlow<SmartHomeRepository>`; view models `flatMapLatest` over that so a settings
change swaps the live data source underneath them. The previous repository is closed.

## UI

Compose throughout, Material 3, one `NavHost` with three tabs. Each screen has a view model
built with `viewModelFactory { initializer { … } }` from the container — enough DI for an
app with one of everything.

### The AR screen

```
ARSceneView
├── PlacementReticle            (edit mode, while a device is waiting to be placed)
└── AnchorNode  name = "placement:<id>", isEditable = (mode == EDIT), scale locked
    ├── CubeNode / SphereNode / CylinderNode   (DeviceGeometry, isTouchable = false)
    └── ViewNode                (only while selected)
        └── DeviceCard          (Compose, its own composition)
```

- **Placing.** The palette sets `pendingDeviceId`. A tap on empty space runs
  `surfaceHit()` — the first hit on a tracked plane, inside its polygon, the same rule the
  2019 code used for its crosshair — and `ArViewModel.place()` turns the hit into an anchor.
- **Selecting.** Tapping any child of an anchor resolves to that anchor because the geometry
  is not touchable; `Node.placementId()` walks up the parents to the tagged node.
- **Editing.** SceneView's own node gestures. `moveHitTest` re-runs `surfaceHit()` so a
  dragged device stays on a plane; the anchor is detached during the drag and recreated on
  release. Rotation is a two-finger twist; `isScaleEditable = false`.
- **Cards.** A `ViewNode` renders a Compose tree onto a textured quad at 250 px per metre.
  `DeviceCard` is a fixed 240 dp wide, so `ArScreen` computes the scale that makes it
  0.30 m in the room whatever the phone's density. `onFrame` turns it towards the camera.
  The `ViewNode` hosts a separate composition: no `CompositionLocal` from the screen reaches
  it, so the theme is re-applied inside and state is read from the view model's flows.
- **Geometry colour.** One `MaterialInstance` per shape, created once and recoloured in
  place when state changes; released when the shape leaves the scene.

Placements live in `ArViewModel` so they survive the AR view being rebuilt, but not process
death — ARCore anchors belong to their session.

### Everything else

`DevicesScreen` is `DeviceControls` in a `LazyColumn`; it shares that composable with the
AR card so the two cannot drift apart. `SettingsScreen` edits a draft and saves on demand;
"Test connection" pings HA and counts usable entities without saving anything.

## Build

Kotlin 2.4 via AGP 9's built-in Kotlin (no `kotlin-android` plugin), Compose compiler and
kotlinx.serialization plugins, a version catalog, JVM target 21 (SceneView inlines 21-level
bytecode). Release builds run R8 with rules for the serialization plugin and the native
loaders. `android.nativeLibraryAlignmentPageSize = "16k"` keeps Filament's `.so` files
acceptable to Play on 16 KB-page devices.
