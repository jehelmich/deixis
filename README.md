# Deixis — smart-home control by pointing at things

[![CI](https://github.com/jehelmich/deixis/actions/workflows/ci.yml/badge.svg)](https://github.com/jehelmich/deixis/actions/workflows/ci.yml)

Point your phone at the room, pin your smart plugs, lights and sensors to where they
actually are, and control them by tapping the thing itself. A floating card above each
device shows its live readings — power draw, brightness, temperature — and the switch to
change them.

*Deixis* is the linguists' word for reference that only works from where you stand — "this
one", "over there". The idea comes from my Cambridge Computer Science bachelor thesis
(2019): can pointing at a device replace finding it in the flat list every smart-home app
ends up with? This repository
is that thesis app rebuilt on the 2026 Android stack — Kotlin, Jetpack Compose, ARCore via
SceneView — with one addition the original never had: a **simulated home**, so it runs as a
demo without any hardware. The 2019 code is preserved at the git tag
[`thesis-2019`](https://github.com/jehelmich/deixis/tree/thesis-2019).

## Try it

Grab the APK from the [latest release](https://github.com/jehelmich/deixis/releases) or build
it yourself (below). It starts against the simulated home:

- **AR tab** — *Edit* mode: add a marker where a device lives, then name it and pick which
  backend device it stands for; drag to move, twist to rotate, pinch to resize. *Use* mode:
  tap a device to open its card and flip switches. Needs a phone with
  [ARCore support](https://developers.google.com/ar/devices).
- **Devices tab** — the same devices and controls as a plain list. Works on anything,
  including an emulator, and is the quickest way to see the backend doing something.
- **Settings tab** — switch to a real [Home Assistant](https://www.home-assistant.io)
  instance: enter its URL and a long-lived access token, test, save. Switches, lights and
  temperature/humidity sensors show up; see [docs/home-assistant.md](docs/home-assistant.md).

## How it works

```
                 ┌──────────────── UI (Compose) ────────────────┐
                 │  ArScreen        DevicesScreen   SettingsScreen│
                 │  ArViewModel     DevicesViewModel SettingsVM   │
                 └────────┬───────────────┬───────────────┬──────┘
                          │               │               │
   ARCore session ──▶ AnchorNode ──┐      │      ConnectionSettings (DataStore)
   plane hit-test     ├─ geometry  │      ▼               │
   tap / drag / twist └─ ViewNode ─┼─▶ SmartHomeRepository ◀──── AppContainer picks one
                        (Compose   │      ▲         ▲
                         card in   │      │         │
                         3D space) │  SimulatedSmartHome   HomeAssistantRepository
                                   │  ticking readings     Ktor ⇄ /api/states, /api/services
```

The AR screen is a [SceneView](https://github.com/sceneview/sceneview) `ARSceneView`. Each
marker is an `AnchorNode` on an ARCore anchor. Where it is and what it is are decided
separately: the anchor comes from tapping a surface, the binding to a backend device from a
dropdown afterwards, and the marker's shape follows that binding — a pin until assigned, then
a bulb, a plug or a sensor box whose colour reacts to state. In Use mode a selected marker
carries a `ViewNode` that renders an ordinary Compose card in 3D and keeps it facing the
camera. Editing is SceneView's own gesture handling: the anchor owns position (drags
re-hit-test against the detected planes), a child node owns rotation and scale, because
ARCore rewrites the anchor's pose every frame.

Everything below the UI talks to one interface, `SmartHomeRepository`. The simulated
implementation ticks plausible readings; the Home Assistant one polls the REST API while
anyone is looking and refreshes after every command. `AppContainer` swaps between them when
the settings change. [docs/architecture.md](docs/architecture.md) goes into detail.

## Repository layout

```
app/src/main/kotlin/com/janhelmich/deixis/
  domain/          Device, DeviceState, SmartHomeRepository — no Android in here
  data/simulated/  the pretend home
  data/homeassistant/  REST client, entity → device mapping, polling repository
  data/settings/   backend choice, URL and token, in DataStore
  di/              AppContainer: builds the graph by hand
  ui/ar/           AR screen, view model, procedural geometry, floating card
  ui/devices/      flat device list
  ui/settings/     backend settings
  ui/common/       DeviceControls, shared by the AR card and the list
app/src/test/      JVM unit tests (backend logic, HA mapping, HTTP client, polling)
docs/              architecture, Home Assistant setup, thesis notes, icon source
.github/workflows  CI on every push; APK attached to releases on `v*` tags
```

## Building

Requires JDK 21+ and an Android SDK with platform 37 and build-tools 36. Point Gradle at the
SDK with `local.properties` (`sdk.dir=…`) or `ANDROID_HOME`.

```sh
./gradlew lintDebug testDebugUnitTest    # what CI runs
./gradlew assembleDebug                  # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                   # onto a connected device
./gradlew connectedDebugAndroidTest      # the AR placement test, on that device
```

No Android Studio is needed, though it opens the project fine.

## Status

University work from 2019, rewritten in September 2026. What is and is not verified:

- **Builds, lints clean and passes its unit tests on every push** (see the badge). The
  simulated backend, the Home Assistant entity mapping, the HTTP client and the polling
  repository are all covered; the last two against a mock server.
- **One on-device test** (`ArPlacementTest`) drives the real UI over ARCore: add a marker,
  tap until it lands on a plane, bind it, switch modes. It passes on the phone below in
  about twelve seconds, and skips itself where ARCore cannot track. A separate weekly
  workflow tries it in the emulator's virtual scene on an x86_64 runner; I could not
  reproduce that locally — Emulator 37 on Apple Silicon exposes its camera as ID `10`, and
  the arm64 ARCore build only looks for `0` — so treat that job as an experiment until it
  has gone green.
- **AR interaction has been tried on one phone** (Galaxy Z Flip6, Android 16, ARCore 1.56):
  placing on a desk, naming and binding markers, selecting, toggling from the floating card,
  drag / twist / pinch and mode switching all work. Not yet checked: vertical surfaces, several devices at once, Home Assistant
  against a live instance, and anything other than that one device. Two things found on the
  first run are documented in the code — SceneView `ViewNode`s need an explicit per-frame
  invalidate to show state changes, and ARCore pauses anchors often enough that a paused
  anchor has to stay visible.
- **Home Assistant support targets the 2019 thesis hardware**: TP-Link plugs that report
  power on the `switch` entity itself. Newer integrations put those readings on separate
  `sensor.*` entities, which the plug card then shows as "—". Lights and climate sensors
  are generic.
- **Placements do not survive process death.** ARCore anchors are session-bound; keeping
  them would need Cloud Anchors or a persistence layer, neither of which the thesis had.

## Licence

My own work is BSD 2-Clause — see [LICENSE](LICENSE). Everything the build pulls in stays
under its own terms; [THIRD_PARTY.md](THIRD_PARTY.md) lists what and why.
