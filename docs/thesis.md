# The 2019 thesis app, and what became of it

The git tag `thesis-2019` holds the code as it stood when the bachelor thesis was written:
Java, Sceneform 1.6, Android Support Library, Volley, `.sfb` model assets. None of it builds
today — Sceneform was archived by Google in 2020, JCenter is gone, Support Library became
AndroidX — so the rewrite was a re-platforming rather than a dependency bump.

The concepts survived intact; only the implementation changed.

| 2019 | 2026 | Notes |
|---|---|---|
| `MainActivity` with a static `mode` string (`MODE_USE` / `MODE_EDIT`) | `ArMode` enum in `ArViewModel` | Same two modes, same meaning |
| Bottom gallery of `ImageView` thumbnails → `addObject()` at screen centre | Palette of chips → tap a surface to place | Placement moved from the crosshair to the tap |
| `ARDevice extends Node` holding two info cards, swapped by mode | `AnchorNode` + `DeviceGeometry` + one `DeviceCard` that reads the mode | One card, two states |
| `ARInfoCard` / `SmartSwitchInfoCard` as `ViewRenderable` of XML layouts | `ViewNode` hosting a Compose `DeviceControls` | Shared with the non-AR list |
| `BetterNode.smartSetEnabled()` rotating a card to face the camera | `onFrame { lookTowards(…) }` on the `ViewNode` | Same maths, per frame |
| `ThreeAxisController` d-pad nudging `localPosition` by 0.1 m | SceneView node gestures: drag re-hit-tests planes, twist rotates | The d-pad was a stand-in for gestures that Sceneform made awkward |
| `Plane.isPoseInPolygon` check before placing | `surfaceHit()` | Kept verbatim in spirit |
| `RequestSingleton` (Volley) + hard-coded HA URL and token in source | `HomeAssistantApi` (Ktor) + settings screen, token in DataStore | The token is the reason the history was rewritten before publishing |
| `.obj` / `.fbx` models compiled to `.sfb` by the Sceneform Gradle plugin | Primitive geometry from code | No asset pipeline, and the shapes can react to state |
| — | `SimulatedSmartHome` | The demo the thesis could not offer |

The thesis hardware was a TP-Link HS110 smart plug and a Philips Hue white bulb behind a
Home Assistant instance on a laptop hotspot, which is why the plug mapping still knows the
HS110's attribute names.
