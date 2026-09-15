# Anchoring requirements

Numbered, testable requirements for long-lived device anchoring, as designed in
[`anchoring.md`](anchoring.md). Each has an acceptance criterion and says how it is verified:
**S** = simulated / JVM test, runs in CI with no device; **B** = off-device benchmark with real
OpenCV; **D** = on the phone. Status is honest as of this commit.

Legend: ✅ implemented and verified · 🔧 implemented, device verification pending · ⏳ not yet built

## A. Storage

| ID | Requirement | Acceptance | Verified | Status |
|---|---|---|---|---|
| A1 | Every marker is stored as a pose in one common map frame, so relative geometry between markers is preserved exactly. | Round-trip a map; `inverse(P_i)·P_j` unchanged for all pairs. | S — `WorldMapCodecTest`, `FileMapStoreTest` | ✅ |
| A2 | The map stores descriptors and 3-D points, never images. | Map file contains no pixel data; size scales with feature count. | S — codec layout | ✅ |
| A3 | The on-disk format is versioned; an unknown version or a truncated/foreign file is rejected, never misread. | Garbage and half-files throw a typed error. | S — `WorldMapCodecTest` | ✅ |
| A4 | Saves are atomic; a crash mid-save cannot leave a corrupt map in place. | Temp-file + rename. | S — `FileMapStoreTest` | ✅ |
| A5 | Maps can be listed by header without loading descriptor payloads. | `list()` returns summaries. | S — `FileMapStoreTest` | ✅ |

## B. Capture (multi-viewpoint map)

| ID | Requirement | Acceptance | Verified | Status |
|---|---|---|---|---|
| B1 | A keyframe is stored only from a viewpoint the map does not already have (≥ 0.25 m or ≥ 15° from every stored keyframe). | Small moves add nothing; large ones add a keyframe. | S — `KeyframeSelectorTest` | ✅ |
| B2 | Frames with too little texture are not stored; keyframe count is capped. | Below `minFeatures` refused; at cap refused. | S — `KeyframeSelectorTest` | ✅ |
| B3 | Every stored feature has a reliable metric 3-D point; features without depth are dropped and descriptor rows stay aligned with points. | Dropped features leave no orphan descriptors. | S — `KeyframeBuilderTest` | ✅ |
| B4 | Back-projection is geometrically exact (project ∘ unproject = identity; point returns to its world position). | Sub-1e-3 round trips. | S — `GeometryTest`, `SyntheticSceneTest` | ✅ |
| B5 | Capture runs continuously in Edit mode with no explicit "start capture" step. | Looking around the room populates keyframes; a status readout shows the count. | D — Flip6 run 2026-09-15: 60 keyframes, 60 441 points; depth-from-motion needs ~3 s of movement before the first keyframe | ✅ |
| B6 | The user can save the current room (markers + keyframes) with one action. | Save writes a map; it appears on relaunch. | D — `home.dxmap` 2.8 MB, loaded on relaunch | ✅ |

## C. Recognition

| ID | Requirement | Acceptance | Verified | Status |
|---|---|---|---|---|
| C1 | Descriptor matching keeps only unambiguous matches (ratio test) before solving. | Ambiguous descriptors do not become correspondences. | B — synthetic bench | ✅ |
| C2 | A single rigid camera pose is recovered by RANSAC over 2-D↔3-D correspondences; inlier count is reported. | Synthetic scene: camera and marker recovered to < 1 cm; inliers ≈ true correspondences. | B — `SyntheticRelocalizationTest` (sub-mm, 95 inliers) | ✅ |
| C3 | The OpenCV→ARCore camera convention conversion is exact. | Hand-built poses round-trip. | S — `PnpConversionTest` | ✅ |
| C4 | An unrelated view never yields a lock. | `NotFound` for disjoint descriptors. | B — `SyntheticRelocalizationTest` | ✅ |
| C5 | The recognition envelope is measured, not assumed, and documented. | Viewpoint table exists for the shipped descriptor. | B — `ViewpointRobustnessTest` (ORB, CI) + `tools/viewpoint_bench.py` (XFeat) | ✅ |
| C6 | Absolute accuracy is measured against public ground truth (median cm/°, success @ 5 cm/5°). | Numbers reported for ≥ 1 7-Scenes scene. | B — `SevenScenesBenchmarkTest` (gated on dataset) | ⏳ needs dataset |
| C7 | Descriptor is viewpoint-robust enough for room use (precision ≥ 70 % at 30°). | Measured table clears the bar. | B | ⏳ XFeat on-device (ORB measured at 58 %: fails) |

## D. The feedback loop

| ID | Requirement | Acceptance | Verified | Status |
|---|---|---|---|---|
| D1 | Candidates below `minInliers` are ignored. | `RejectedWeak`. | S — `SessionAlignmentTest` | ✅ |
| D2 | **Never adopt an alignment on a single observation**: the first alignment needs `bootstrapConfirmations` consecutive agreeing candidates, each ≥ `bootstrapInliers`. | A lone wrong first lock is not adopted; two agreeing ones are. | S — `SessionAlignmentTest` | ✅ |
| D3 | **Constellation gate**: a candidate that would move any marker more than `maxMarkerJumpMeters` from its current rendered place is refused. Rotation error is weighted by lever arm. | 20° yaw with a device 2 m away → refused. | S — `SessionAlignmentTest` | ✅ |
| D4 | Consistent candidates are blended, not snapped; gain rises with inlier count. | Partial move; stronger candidate moves further. | S — `SessionAlignmentTest` | ✅ |
| D5 | Strong, mutually consistent refused candidates re-bootstrap the alignment (ARCore reset / bad first lock). | Consensus adopted after `rebootstrapAfter`. | S — `SessionAlignmentTest` | ✅ |
| D6 | Weak candidates cannot re-bootstrap (`rebootstrapMinInliers`); scattered refusals never re-bootstrap. | Repeated weak wrong lock refused; scattered set refused. | S — `SessionAlignmentTest` | ✅ |
| D7 | Under continuous drift (2 cm/s, 0.5°/s), noisy corrections at 2 Hz, a 4 s blackout and 1-in-12 wrong locks, markers stay within 15 cm of truth and **no wrong lock is ever accepted**. | Drift simulation passes. | S — `RelocalizationControllerTest` (worst 3 cm, 0 accepted) | ✅ |
| D8 | Recognition attempts are rate-limited. | ≈ `1/attemptInterval` calls per second. | S — `RelocalizationControllerTest` | ✅ |
| D9 | Status reflects reality: Searching → Locked → Coasting → Locked. | Transitions under a scripted relocalizer. | S — `RelocalizationControllerTest` | ✅ |

## E. Runtime on the device

| ID | Requirement | Acceptance | Verified | Status |
|---|---|---|---|---|
| E1 | On launch the saved map is loaded and recognition runs from the first frame; no explicit "load" step. | Open the app, look at the room, devices appear. | D — loaded at launch; first confirmed lock ~2 min into the run, 199 attempts, 83 % accepted, median 45 inliers | ✅ |
| E2 | On the first confirmed lock every marker is restored as an ARCore anchor at `T_S←M · P_M`, with its label and device binding. | Restored devices control their backend as before. | D — restored 2 (then 3) markers; taps picked them; cards and switches worked (Home Assistant backend) | ✅ |
| E3 | Corrections are applied as a smoothed offset from the anchor so devices glide; only corrections > 0.5 m recreate anchors. | Devices glide, never jump. | D — run 1 showed the failure (5 anchor recreations in 20 s, each ~5–7 cm = visible jumps); glide build installed, unwired verification pending | 🔧 |
| E4 | The UI shows Searching / Locked (with confidence) / Coasting. | Status pill visible in the AR view. | D — observed on the run | ✅ |
| E5 | Relocalization runs off the render thread and does not stall the camera feed. | Frame rate stays smooth during recognition. | D — worker thread, 2 attempts/s at 1920×1080 with 1200 ORB features; no stall reported | ✅ |
| E6 | If OpenCV cannot load, the app behaves exactly as before (no persistence) and says so. | Guarded by `OpenCvLoader`. | D — OpenCV 4.14 loads natively on the Flip6 (arm64); the guard path is exercised in tests, not on a failing device | ✅ |
| E7 | Two consecutive runs: place devices, save, kill the app, relaunch from a different spot in the room (≤ 30° from a captured view), devices reappear within ~10 cm. | The headline device test. | D — passed on the second run: devices came back and their controls worked; the first placement settled within ~6 cm over the following corrections. First run showed the initial placement shifted toward the session start (fixed: cumulative re-anchor, averaged bootstrap) | ✅ (jitter being tuned) |

## F. Upgrade path (tracked, not blocking)

| ID | Requirement | Status |
|---|---|---|
| F1 | Replace ORB with XFeat (LiteRT `litert-community/xfeat-litert`) + LighterGlue behind `FeatureExtractor`; re-measure C5/C7. | ⏳ |
| F2 | Run 7-Scenes for C6 numbers. | ⏳ |
| F3 | Multi-map place recognition (which room?) — only once E7 is reliable in one room. | deferred by design |

## Testing on the device, unwired

Walking a room with a USB cable does not work, so device runs are done unplugged and read back
afterwards from the phone's own log ring buffer:

1. Plug in once, install the build (`./gradlew installDebug`), and enlarge the buffer:
   `adb logcat -G 16M` (the Galaxy Z Flip6 caps it at 5 MiB ≈ 15 minutes of the app's output).
2. Unplug, run the scenario (E7: save → kill → walk elsewhere → relaunch → look at the room).
3. Plug back in **before doing anything else** and dump: `adb logcat -d -v time > run.log`.
   The `DeixisAnchoring` lines carry every relocalization decision with inliers and the
   resulting `T_session_map`, restore/re-anchor events and per-keyframe depth coverage — enough
   to measure jitter (alignment movement between accepted corrections) and lock rate offline.

