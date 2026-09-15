# Testing offline relocalization

Relocalization either puts the marker back within a few centimetres of where it was, or it
does not — so it has to be measured, not eyeballed. There is objective, recorded sensor data
for this, at three levels, each answering a different question.

| Layer | Data | Ground truth | Question it answers | Where it runs |
|---|---|---|---|---|
| 1. Synthetic | Generated 3D scene + known poses | Exact, by construction | Is the geometry/PnP math correct? | JVM, CI, now |
| 2. Benchmark | Public RGB-D relocalization datasets | Motion-capture / KinectFusion poses | How accurate is it, in cm and degrees, against data other people also use? | Desktop JVM, CI |
| 3. Record/playback | ARCore MP4 recordings of real rooms | ARCore's own tracking (consistency, not absolute) | Does a real device, restarted, come back to the same spot? | Phone, deterministic replay |

## Layer 1 — synthetic ground truth (done)

A known cloud of 3D points projected into known camera poses with `CameraIntrinsics.project`
manufactures 2D↔3D correspondences whose answer is exactly right, with no sensor noise. This
pins the projection/back-projection inverse and the map→session transform (`SyntheticSceneTest`),
and the OpenCV↔ARCore convention flip separately (`PnpConversionTest`). Fast, deterministic,
no device; the correctness floor everything else stands on.

### Layer 1.5 — the whole relocalizer on synthetic data (done)

`SyntheticRelocalizationTest`, in the `:relocalization-bench` module, runs the **real**
`OrbRelocalizer` — OpenCV descriptor matching and `solvePnPRansac` — off device against desktop
OpenCV. Each 3D point gets a unique random descriptor; a "live" view reuses those descriptors at
projected pixels, mixed with outliers. The relocalizer recovers the camera and a carried marker
to **sub-millimetre** (95 inliers in the seeded case) and returns `NotFound` for an unrelated
view. This proves the matching plumbing, the PnP call and the convention conversion end to end
without a phone — the piece that most needed real execution, and it runs in CI.

## Layer 2 — a public relocalization benchmark (planned)

The task — recover a camera's 6-DoF pose in a known scene from one frame — is a standard
one with standard datasets and a standard metric, so the pipeline can be scored against
numbers other work reports rather than against itself.

- **[Microsoft 7-Scenes](https://www.microsoft.com/en-us/research/project/rgb-d-dataset-7-scenes/)**
  — the canonical RGB-D relocalization benchmark. Handheld Kinect, 640×480 RGB + 16-bit
  depth, KinectFusion ground-truth camera-to-world poses, split into train and test
  sequences per scene. This is almost exactly our input: RGB (→ ORB), depth (→ 3D per
  keypoint), and a pose to score against.
- **[TUM RGB-D](https://cvg.cit.tum.de/data/datasets/rgbd-dataset)** — RGB-D with
  motion-capture trajectories; a second, independent source.

**Protocol.** Build a `WorldMap` from a scene's *training* frames (ORB + depth back-project,
using the dataset's poses to place points in a common frame). For each *test* frame, run the
relocalizer and compare the recovered pose to ground truth.

**Metrics** (the 7-Scenes conventions, so the numbers are comparable):
- median **translation error** (cm) and **rotation error** (°);
- **success rate** at the standard **5 cm / 5°** threshold;
- inlier count distribution, as a health check on the matcher.

**Harness (built, gated on the data).** The `:relocalization-bench` module depends on the pure
relocalization sources plus desktop OpenCV (`org.openpnp:opencv` — the same `org.opencv.*` API
as the Android AAR, with mac/linux/win natives), so the identical matching code runs off-device.
`SevenScenesBenchmarkTest` builds a `WorldMap` from a training sequence (ORB + depth
back-projection, placed with the dataset's ground-truth poses) and localizes test-sequence
frames, printing median translation (cm), median rotation (°) and the success rate at 5 cm/5°.
It skips itself unless `DEIXIS_7SCENES_DIR` points at a scene directory, so CI stays green
without shipping gigabytes:

```sh
# download one scene from https://www.microsoft.com/en-us/research/project/rgb-d-dataset-7-scenes/
# (e.g. "chess"), unzip its seq-*.zip, then:
DEIXIS_7SCENES_DIR=/path/to/chess ./gradlew :relocalization-bench:test --tests '*SevenScenesBenchmark*' -i
```

The pose parser and the convention flip are unit-tested without the data (`SevenScenesPoseTest`).

## Layer 3 — ARCore record & playback (planned, needs one capture)

ARCore's [Recording and Playback API](https://developers.google.com/ar/develop/recording-and-playback)
records the camera feed, IMU and depth to an MP4 and replays it as if it were live — the same
mechanism the existing `ARPlacementTest` and SceneView's own AR tests use. SceneView exposes
it as `ARSceneView(playbackDataset = …)`.

**Protocol.** Record one pass through a room (or two passes of the same room). Replay it once
to build the map and note where markers were placed; replay it again — or replay the second
pass — with the map loaded, and assert each rebuilt marker's anchor lands within a threshold
(say 10 cm) of its original placement. Because playback replays the identical sensor stream,
the test is deterministic and belongs in CI's on-device lane; the recordings are committed
fixtures, captured once by a human (the "human records once, machines replay forever" pattern
already used for the placement test).

**What it does and does not prove.** This is the most realistic test — real ARCore tracking,
real depth, real ORB on real image noise — but its reference is ARCore's own tracking, not an
external mocap, so it measures **consistency** (does the device return to the same place)
rather than absolute accuracy. Layer 2 supplies the absolute numbers; layer 3 supplies the
proof that the whole chain works end to end on a phone.

## Measured: ORB is not viewpoint-robust enough; XFeat + LighterGlue is meaningfully better

The load-bearing question is how much *viewpoint change* relocalization survives. Measured with
the planar homography protocol above (`tools/viewpoint_bench.py`) on the same indoor image and
the same simulated camera rotations — correct matches judged against the known homography:

| viewpoint rotation | ORB (matches / correct / precision) | XFeat + LighterGlue |
|---|---|---|
| 10° | 492 / 430 / 87% | 986 / 937 / **95%** |
| 20° | 291 / 224 / 77% | 685 / 611 / **89%** |
| 30° | 116 / 67 / **58%** | 366 / 270 / **74%** |
| 40° | 12 / 1 / **8%** (noise) | 24 / 13 / 54% |
| 50° | 9 / 0 / 0% | 0 / 0 / 0% |

Reading it honestly:

- **ORB collapses into the wrong-lock regime by ~30°.** At 30° barely half its matches are
  correct (58%), and by 40° it is pure noise (1 of 12). A relocalizer built on ORB would
  confidently put artifacts in the wrong place — the "replace your devices every time" failure.
- **XFeat + LighterGlue roughly doubles the correct matches and keeps precision usable further
  out** — 74% at 30°, still 54% at 40° where ORB is dead. It moves the reliable envelope from
  ~20° (ORB's precision-safe limit) to ~30–40°, and, crucially, avoids the wrong-lock regime.
- **It is not magic.** Beyond ~40° of pure rotation on a hard, narrow scene even XFeat fails,
  and this is the *optimistic* planar case. So learned features are necessary but not
  sufficient on their own. (An earlier estimate of "50–60°" was too optimistic; these are the
  measured numbers.)

The consequence for the design: **learned features must be paired with multi-viewpoint capture.**
If the map stores each feature's appearance from several angles (keyframes taken while the user
looks around), then a query from a new position is always within ~30° of *some* stored view, and
the effective recovery envelope is the union of those cones — which is how you get "walk in from
anywhere and it recovers" rather than "stand where you captured." XFeat widens each cone; the
multi-viewpoint map multiplies them. That combination, not the descriptor alone, is what makes
within-space relocalization reliable.

Licence/runtime for the plan: XFeat is Apache-2.0 and designed for mobile; an on-device model
ships as `litert-community/xfeat-litert` (`.tflite`, runs on LiteRT 2.2), and LighterGlue is
Apache-2.0. So the whole upgrade is permissive and has a real on-device path.

## What "good" looks like

For a room-scale demo, returning a marker to within a few centimetres of a surface it sits on
is the bar — close enough that a lamp is on its table, not floating beside it. The honest
failure mode to watch is not small error but **wrong locks**: relocalizing confidently against
the wrong part of the room. That is what the inlier threshold and the 5 cm/5° success-rate
metric are there to catch, and why a low-inlier result must report "not found" rather than a
best guess.
