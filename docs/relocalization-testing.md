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
pins the projection/back-projection inverse and the map→session transform, and is where the
`solvePnPRansac` step gets its first correctness check — including deliberately injected pixel
noise and outlier matches, to confirm RANSAC rejects them. See `SyntheticSceneTest`. Fast,
deterministic, no dependencies; the correctness floor everything else stands on.

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

**Harness.** A separate JVM module (`:relocalization-bench`) depending on the pure
relocalization sources plus desktop OpenCV (`org.openpnp:opencv`, the same `org.opencv.*` API
as the Android AAR but with desktop natives), so the identical matching code runs off-device.
The dataset is downloaded and cached (it is large — one scene, not all seven, in CI), and the
benchmark runs as a JUnit test that asserts error stays under a committed baseline, so a
regression in the matcher shows up as a red build with a number attached.

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

## What "good" looks like

For a room-scale demo, returning a marker to within a few centimetres of a surface it sits on
is the bar — close enough that a lamp is on its table, not floating beside it. The honest
failure mode to watch is not small error but **wrong locks**: relocalizing confidently against
the wrong part of the room. That is what the inlier threshold and the 5 cm/5° success-rate
metric are there to catch, and why a low-inlier result must report "not found" rather than a
best guess.
