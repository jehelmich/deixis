# Third-party components

The [LICENSE](LICENSE) at the root of this repository covers my own work: everything under
[`app/src/`](app/src/), the build scripts, the documentation and the app icon
([`docs/icon-source/`](docs/icon-source/)).

Nothing third-party is vendored. Everything below is fetched by Gradle at build time and
remains under its own licence.

| Component | Used for | Licence |
|---|---|---|
| [SceneView](https://github.com/sceneview/sceneview) (`io.github.sceneview:arsceneview`) | Compose API over ARCore and Filament: anchors, hit-testing, node editing, `ViewNode` cards | Apache-2.0 |
| [Filament](https://github.com/google/filament) | Physically based rendering (pulled in by SceneView) | Apache-2.0 |
| [ARCore SDK for Android](https://github.com/google-ar/arcore-android-sdk) (`com.google.ar:core`) | Motion tracking, plane detection, anchors | Apache-2.0 for the SDK; the runtime ("Google Play Services for AR") is governed by the [ARCore Additional Terms of Service](https://developers.google.com/ar/develop/terms) |
| [Ktor](https://ktor.io) | HTTP client for the Home Assistant REST API | Apache-2.0 |
| [Kotlin](https://kotlinlang.org), kotlinx.coroutines, kotlinx.serialization | Language and libraries | Apache-2.0 |
| [AndroidX](https://developer.android.com/jetpack/androidx) incl. Jetpack Compose, Navigation, DataStore, Lifecycle | UI framework and app plumbing | Apache-2.0 |
| [Turbine](https://github.com/cashapp/turbine), JUnit 4 | Tests only | Apache-2.0 / EPL-1.0 |

The 2019 thesis build (git tag `thesis-2019`) additionally contained Google's Sceneform
sample models (`andy`, `Cabin`, `House`, `igloo`), which are not part of this branch.
