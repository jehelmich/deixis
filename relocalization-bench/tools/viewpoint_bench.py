#!/usr/bin/env python3
"""
Viewpoint-robustness comparison for relocalization features: ORB vs XFeat + LighterGlue.

Measures how many feature matches survive a known viewpoint change and how many are correct.
A real indoor image is warped by an exact homography that simulates rotating the camera by
theta degrees (H = K * Ry(theta) * K^-1); because the homography is known, every match has
ground truth. This is the standard planar (HPatches-style) protocol and the optimistic case:
no parallax, occlusion or lighting change. Real "walk to another spot" is harder — which is
why the conclusion is "learned features + multi-viewpoint maps," not "learned features alone."

This is a research/eval tool, deliberately outside the Gradle build (it needs PyTorch). It
produced the numbers quoted in docs/relocalization-testing.md.

Setup:
    python3 -m venv venv && . venv/bin/activate
    pip install torch kornia opencv-python-headless numpy tqdm
    git clone https://github.com/verlab/accelerated_features   # XFeat + weights (Apache-2.0)

Run:
    python viewpoint_bench.py --image room.png --xfeat-repo accelerated_features
"""
import argparse, math, sys
import cv2, numpy as np


def rotation_homography(w, h, deg, f_scale=0.9):
    f = f_scale * w; cx, cy = w / 2, h / 2
    K = np.array([[f, 0, cx], [0, f, cy], [0, 0, 1.0]])
    a = math.radians(deg)
    R = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
    return K @ R @ np.linalg.inv(K)


def correct_count(pts0, pts1, H, thresh_px=4.0):
    c = 0
    for p0, p1 in zip(pts0, pts1):
        p = H @ np.array([p0[0], p0[1], 1.0]); p = p[:2] / p[2]
        if np.hypot(p[0] - p1[0], p[1] - p1[1]) <= thresh_px:
            c += 1
    return c


def bench_orb(gray, angles):
    orb = cv2.ORB_create(4096); bf = cv2.BFMatcher(cv2.NORM_HAMMING)
    h, w = gray.shape[:2]
    kp0, d0 = orb.detectAndCompute(gray, None)
    print(f"\nORB on {w}x{h} ({len(kp0)} features):")
    print("  angle | matches | correct | precision")
    for deg in angles:
        H = rotation_homography(w, h, deg)
        warp = cv2.warpPerspective(gray, H, (w, h))
        kp1, d1 = orb.detectAndCompute(warp, None)
        good = [m for m, n in bf.knnMatch(d0, d1, k=2) if m.distance < 0.75 * n.distance]
        c = correct_count([kp0[m.queryIdx].pt for m in good], [kp1[m.trainIdx].pt for m in good], H)
        print(f"  {deg:4d}° | {len(good):7d} | {c:7d} | {(100*c/len(good) if good else 0):6.0f}%")


def bench_xfeat(rgb, angles, repo):
    sys.path.insert(0, repo)
    from modules.xfeat import XFeat
    xf = XFeat(weights=f"{repo}/weights/xfeat.pt", top_k=4096)
    h, w = rgb.shape[:2]
    print(f"\nXFeat + LighterGlue on {w}x{h}:")
    print("  angle | matches | correct | precision")
    for deg in angles:
        H = rotation_homography(w, h, deg)
        warp = cv2.warpPerspective(rgb, H, (w, h))
        d0 = xf.detectAndCompute(rgb, top_k=4096)[0]; d0.update({"image_size": (w, h)})
        d1 = xf.detectAndCompute(warp, top_k=4096)[0]; d1.update({"image_size": (w, h)})
        mk0, mk1, _ = xf.match_lighterglue(d0, d1)
        c = correct_count(mk0, mk1, H) if len(mk0) else 0
        print(f"  {deg:4d}° | {len(mk0):7d} | {c:7d} | {(100*c/len(mk0) if len(mk0) else 0):6.0f}%")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--image", required=True)
    ap.add_argument("--xfeat-repo", help="path to a clone of verlab/accelerated_features")
    ap.add_argument("--angles", default="0,10,20,30,40,50")
    args = ap.parse_args()
    angles = [int(a) for a in args.angles.split(",")]
    bgr = cv2.imread(args.image)
    bench_orb(cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY), angles)
    if args.xfeat_repo:
        bench_xfeat(cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB), angles, args.xfeat_repo)


if __name__ == "__main__":
    main()
