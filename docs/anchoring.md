# Anchoring: how devices are stored, recognised and kept in place

This is the design of record for long-lived device placement — the thing that makes a marker
still be on the desk tomorrow, from wherever you walk in, without re-placing it. It replaces
"place devices in one session" with a **map you make yourself**, a **recogniser** that finds that
map in the live camera, and a **feedback loop** that keeps the two aligned while you move.

It is built entirely on the phone: no cloud service, no account, no tags on the wall. The
mechanism is the same class as Cloud Anchors / ARKit world anchors — a sparse visual map with
appearance, relocalized by feature matching and a geometric solve — reconstructed locally.
[`persistence.md`](persistence.md) covers the pipeline; [`relocalization-testing.md`](relocalization-testing.md)
covers how it is measured; this document covers the *model*: what is stored, what is recognised,
and how the loop closes. The numbered requirements it satisfies are in
[`anchoring-requirements.md`](anchoring-requirements.md).

## 1. Three frames

Everything is a rigid transform between three coordinate frames.

| frame | what it is | lifetime |
|---|---|---|
| **map** `M` | the coordinate frame of the room as recorded — the ARCore world frame of the session that created the map | persistent (on disk) |
| **session** `S` | ARCore's live world frame for *this* run; starts fresh every launch and **drifts** slowly over minutes and room-scale distance | this run only |
| **alignment** `T_S←M` | the transform that carries anything stored in `M` into `S` | continuously re-estimated |

A device is rendered at `T_S←M · P_M`, where `P_M` is its stored pose in the map. That one line is
the whole point: the stored pose never changes; only the alignment does.

## 2. What is stored: a rigid constellation on a visual map

A **`WorldMap`** is two things in one frame:

- **Keyframes.** Snapshots of the room from several viewpoints. Each keyframe holds, per feature,
  a 2-D pixel, a **3-D point in `M`** (pixel + depth back-projected through the camera pose) and a
  **descriptor** (a compact fingerprint of the patch around it). Descriptors, not images, are
  what persist — the map is appearance-tagged geometry and cannot be turned back into a photo.
- **Markers.** Every placed device as a `MarkerPose`: label, bound backend device, and its pose
  `P_M` in the same map frame.

Because all markers share `M`, **their geometry relative to each other is recorded implicitly and
exactly** — the pose of device *i* relative to device *j* is `inverse(P_i) · P_j`. They form a
rigid constellation attached to the room's features. Two consequences fall out of that:

- **Recognising any part of the room places every device**, including the ones behind you. One
  alignment, recovered from whatever features are in view, positions the whole constellation.
- **The constellation is a known-good shape** that a candidate alignment can be checked against
  (§5). That is what lets the loop refuse wrong locks instead of guessing.

The room's own features are the anchor. Virtual markers do not emit features; they ride on the
features that the room does. (A marker bound to a *physically visible* device could one day be a
landmark in its own right; that is a stronger, later design.)

## 3. Capture: making the map multi-viewpoint

Relocalization survives only a bounded viewpoint change — measured at roughly ±30° for XFeat and
less for ORB ([`relocalization-testing.md`](relocalization-testing.md)). So the map must describe
each feature from **several angles**, and the capture loop's job is to make that happen without
the user thinking about it:

- While the room is being arranged (Edit mode), every frame is offered to a **`KeyframeSelector`**.
  A frame becomes a keyframe only if the camera has moved ≥ 0.25 m from, or turned ≥ 15° from,
  *every* keyframe already stored, and it has enough texture. Standing still adds nothing; looking
  around adds views.
- Each accepted frame goes through **`KeyframeBuilder`**: features get a depth, are back-projected
  into `M`, and features without a trustworthy depth are dropped (a descriptor without a reliable
  3-D point invites a false match).
- **Saving** (`MapBuilder`) writes the keyframes plus every marker's current anchor pose — in the
  capture session's frame, which *is* `M` — as one versioned binary file (`WorldMapCodec`,
  `FileMapStore`). Atomic; a crash mid-save cannot leave a half-map.

The recovery envelope is the **union of the cones** around the stored viewpoints. The descriptor
sets the width of each cone; capture sets how many there are.

## 4. Recognition: finding the map in a live frame

On every attempt (§6 sets the cadence), one live frame is turned into features and handed to the
**`Relocalizer`**:

1. Match live descriptors to the map's (Hamming for ORB, L2 for learned descriptors), keeping only
   unambiguous matches (Lowe's ratio test).
2. Each surviving match is a **2-D live pixel ↔ 3-D map point** correspondence.
3. `solvePnPRansac` finds the single camera pose in `M` that the most correspondences agree on;
   the agreeing ones are **inliers**.
4. With enough inliers, that pose plus ARCore's pose of the same camera in `S` gives a candidate
   alignment: `T_S←M = T_S←cam · (T_M←cam)⁻¹`. Too few inliers → **`NotFound`**, deliberately, rather
   than a low-confidence guess.

`OrbRelocalizer` implements this today. The extractor and matcher sit behind seams so the
descriptor can be upgraded (XFeat + LighterGlue is the measured, licence-clean next step)
without touching anything else in this document.

## 5. The feedback loop: a self-correcting alignment

A single recognition would snap the devices into place once and then let ARCore's drift carry
them off. The loop instead treats every confident recognition as a **measurement** of `T_S←M`
and maintains one smoothed estimate that the markers are always rendered through
(**`SessionAlignment`**). Its rules, in order:

1. **Ignore weak candidates.** Fewer inliers than `minInliers` → discarded.
2. **Never adopt on a single observation.** With no alignment yet, a candidate is only *pending*.
   The first alignment is adopted when `bootstrapConfirmations` consecutive candidates **agree**
   with each other (within a few centimetres at the markers) and each is at least
   `bootstrapInliers` strong. A lone confident-looking wrong lock — the worst possible start —
   is never accepted.
3. **Constellation gate.** With an alignment in hand, ask where the candidate would put every
   marker versus where the current alignment puts it. The markers are a rigid set; a candidate
   that moves *any* of them more than `maxMarkerJumpMeters` is a **wrong lock** and is refused.
   Checking at the markers, not the camera, gives rotation error its lever arm: a small angular
   error that would swing a device across the room counts as the large jump it is.
4. **Blend, don't snap.** A consistent candidate pulls the estimate toward itself with a gain that
   rises with its inlier count (`minGain`…`maxGain`). Devices glide back into place; they never
   jump.
5. **Re-bootstrap on strong consensus.** If `rebootstrapAfter` consecutive candidates are each
   refused by the gate yet **agree with each other**, the current alignment is the one that is
   wrong — ARCore reset its world, or the first lock was off — and the consensus replaces it.
   Only candidates at least `rebootstrapMinInliers` strong may vote: overturning an alignment
   demands more evidence than establishing one, and wrong locks are characteristically weaker
   than honest ones, so a repeatable wrong lock cannot vote itself in.

Between accepted corrections the markers coast on ARCore's own tracking, which is accurate over
seconds; each accepted correction pulls the accumulated drift back out. The visible result is
exactly the "alignment grid" you would want: devices that stay put, quietly re-registered
whenever the app is sure, and never yanked somewhere wrong.

## 6. Runtime: the controller and what the app shows

**`RelocalizationController`** runs the loop as a pure state machine fed with each frame's features
and camera pose:

```
Searching ──confirmed lock──▶ Locked ──no correction for coastAfter──▶ Coasting ──lock──▶ Locked
```

- It attempts recognition at most every `attemptIntervalNanos` (recognition is not free).
- **Searching:** no alignment yet — the UI asks the user to look around the room.
- **Locked:** alignment fresh; markers are rendered through it and the UI can show confidence
  (last accepted inlier count).
- **Coasting:** still aligned, but riding on ARCore alone for a while — the UI should say so, and
  the loop keeps trying.
- On the first lock the app **restores** every marker as a fresh ARCore anchor at `T_S←M · P_M`; on
  a later accepted correction larger than a few centimetres it re-anchors, so ARCore's own
  tracking stabilises the markers between corrections and the loop corrects the drift.

There is no "load session" step. The app loads the saved map on launch and the controller runs
from the first frame; recognition is implicit and continuous.

## 7. What is proven, and where

| Piece | How it is verified |
|---|---|
| Storage format, geometry, back-projection, alignment maths | JVM unit tests |
| Full matcher + PnP + convention conversion | real OpenCV on synthetic ground truth, off-device (sub-mm) |
| Descriptor viewpoint envelope (ORB vs XFeat) | homography benchmark, measured |
| Bootstrap confirmation, constellation gate, blending, re-bootstrap | unit tests on hand-built poses |
| The whole loop under drift, dropouts and wrong locks | **drift simulation**: 60 s, 2 cm/s + 0.5°/s drift, a 4 s blackout, 1-in-12 wrong locks — markers stay within 3 cm, zero wrong locks accepted |
| Capture → save → auto-load → restore, end to end | on the phone (see the requirements doc's device checklist) |
| Absolute accuracy against ground truth | 7-Scenes benchmark, gated on the dataset |

## 8. Honest limits

- Recognition is only as viewpoint-tolerant as the descriptor; ORB's envelope is narrow and
  precision fails by ~30°. The loop is correct with ORB but will *lock less often*; XFeat widens
  each cone. Multi-viewpoint capture is not optional either way.
- Between locks the markers drift with ARCore. How tight the alignment feels is set by how often
  a confident lock is available — which is the descriptor and the capture coverage again.
- One area per map, no global optimisation. This is anchor-keeping, not a mapping product.
- Anchors are not persisted through ARCore; the map is. On a fresh launch nothing is on screen
  until the first confirmed lock — by design, since guessing is the failure mode.
