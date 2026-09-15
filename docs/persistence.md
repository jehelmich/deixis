# Offline relocalization — long-lived anchors without the cloud

> Branch `feature/offline-relocalization`. This documents the design; the capture side and
> the storage/geometry it produces are implemented and unit-tested, and the matching step
> (`solvePnP`) is the next commit. Status is at the bottom.

## The problem

A placed device is only useful if it is still there next time. In one session ARCore tracks
an anchor perfectly; across a restart it cannot, because **ARCore has no on-device world
map**. Each session starts a brand-new coordinate frame, and there is no Android equivalent
of ARKit's `ARWorldMap`. Anchors are session-bound, full stop.

Google's answer is [Cloud Anchors](https://developers.google.com/ar/develop/cloud-anchors):
at placement the surroundings are fingerprinted and the fingerprint is uploaded; on a later
run ARCore relocalizes the live camera against that hosted map and hands the anchor back,
for up to a year. It works well — but the map lives on Google's servers, needs a Google
Cloud project and an API key, and needs a network round-trip.

This branch rebuilds that mechanism **on the device**. Same idea — a sparse visual map you
relocalize against — with nothing leaving the phone and no per-room tags to stick on walls.

## What a Cloud Anchor actually is, and what we can rebuild

A Cloud Anchor is a sparse point cloud with *appearance*: 3D points plus feature descriptors
that let a later frame recognise the same physical spot. The one thing ARCore does not hand
back is the descriptors themselves — so the pipeline synthesises its own from what every
`Frame` does expose:

| ARCore gives us | We use it for |
|---|---|
| `Frame.acquireCameraImage()` (CPU YUV) | run **ORB** → 2D keypoints + 32-byte binary descriptors |
| Depth API (`Frame.acquireDepthImage16Bits`) / hit-test | a **metric depth** for each keypoint |
| `Camera.getImageIntrinsics()` | back-project pixel + depth → a 3D point |
| `Camera.getPose()` | carry that point into the session's world frame |
| `Frame.acquirePointCloud()` | (alternative depth source; sparser) |

## The map

A [`WorldMap`](../app/src/main/kotlin/com/janhelmich/deixis/data/relocalization/WorldMap.kt)
is a set of **keyframes** captured while the user looks around placing devices, plus the
markers themselves. Each keyframe stores the camera pose it was taken from and, for every
feature, its pixel location, its 3D point **in the map frame**, and its ORB descriptor. A
marker is stored as its pose in the same map frame. Descriptors, never images, are what
persist: the map is appearance-tagged geometry, exactly like a Cloud Anchor, and cannot be
turned back into a photo of the room.

Maps are written one binary file each by
[`WorldMapCodec`](../app/src/main/kotlin/com/janhelmich/deixis/data/relocalization/WorldMapCodec.kt)
/ [`FileMapStore`](../app/src/main/kotlin/com/janhelmich/deixis/data/relocalization/MapStore.kt),
in the app's private storage. The format is versioned; saves are atomic (temp file + rename).

## Capture (implemented)

While the user arranges a room:

1. Every so often, and biased toward new viewpoints, grab the camera image, run
   [`OrbFeatureExtractor`](../app/src/main/kotlin/com/janhelmich/deixis/data/relocalization/OrbFeatureExtractor.kt).
2. [`KeyframeBuilder`](../app/src/main/kotlin/com/janhelmich/deixis/data/relocalization/KeyframeBuilder.kt)
   gives each feature a depth, back-projects it (`CameraIntrinsics.unproject` in
   [`Geometry.kt`](../app/src/main/kotlin/com/janhelmich/deixis/data/relocalization/Geometry.kt)),
   and carries it into the map frame with the camera pose. Features with no reliable depth
   are dropped — a descriptor without a trustworthy 3D point invites a false match.
3. On save, the markers' current poses are written alongside the keyframes.

The extractor is the only class that touches OpenCV; everything else is plain arrays, so the
back-projection, the transform algebra, the codec and the store are all unit-tested on the
JVM without a device or native code.

## Relocalization (next)

Given a saved map and a live frame:

1. ORB the live frame; match its descriptors against the map's by Hamming distance
   (brute-force with cross-check, or FLANN-LSH), with a ratio test.
2. Each good match is a **2D (live pixel) ↔ 3D (map point)** correspondence. Feed them to
   `solvePnPRansac` → the live camera's pose **in the map frame**, plus an inlier count.
3. With enough inliers, compose the transform that carries anything in the map into the
   running session (`relocalizationTransform` in `Geometry.kt`):

   ```
   T_session_map = T_session_camera · (T_map_camera)⁻¹
   ```

   `T_session_camera` is where ARCore puts the live camera now; `T_map_camera` is what PnP
   just recovered. Every marker's session pose is then `T_session_map · T_map_marker`
   (`MarkerPose.inSession`), and each becomes a fresh ARCore anchor — the offline equivalent
   of a Cloud Anchor `resolve`.

`Relocalizer` is the seam and `OrbRelocalizer` is the OpenCV implementation: brute-force
Hamming matching with Lowe's ratio test, then `solvePnPRansac`, then the convention conversion
(`cameraInMapFromPnp`). It returns the recovered camera pose and the map→session transform, or
`NotFound` when too few matches agree — it never reports a low-confidence guess.

## Honest limits

- **ORB is not Google's descriptor.** Expect relocalization to want a similar viewpoint and
  similar lighting to capture; large changes will miss. A learned descriptor (SuperPoint via
  LiteRT) behind the same `FeatureExtractor` interface is the obvious upgrade, and the reason
  that interface exists.
- **Drift.** Anchors far from where relocalization locked on inherit ARCore's tracking
  drift. Several keyframes spread around the room, and re-relocalizing opportunistically,
  keep it honest.
- **Not a loop-closing SLAM system.** One area per map, no global optimisation. That is the
  right scope for "put my lights back where they were", not a mapping product.
- **Depth.** Devices without the Depth API fall back to hit-test/point-cloud depth, which is
  sparser; fewer features get a 3D point and maps are thinner.

## Why this is the interesting part

Placing a device in one session is a demo. A device that is **still there tomorrow**,
because the app quietly recognised the room and rebuilt its anchors from a map it made
itself, is the actual idea behind deixis — reference that survives you leaving and coming
back — and it does it without a cloud, an account, or a tag on the wall.

## Status

- **Done, tested:** map model, binary codec (round-trip + rejects garbage), file store,
  back-projection and relocalization-transform math, keyframe builder (depth gating +
  descriptor alignment) — 12 JVM tests. OpenCV wired (arm64, `initLocal`) behind an
  availability check so the app is unaffected where it will not load.
- **Done, tested:** `OrbRelocalizer` (match → `solvePnPRansac` → convention conversion),
  verified end to end on synthetic ground truth with real OpenCV off-device (recovers the
  camera and marker to sub-millimetre; refuses to lock on an unrelated view). The convention
  flip is pinned separately against hand-built poses.
- **Next:** the capture loop in the AR screen, a maps UI (save/name/load/delete), rebuilding
  markers on load, and running the 7-Scenes benchmark for accuracy numbers on real data.
