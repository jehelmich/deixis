package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point
import org.opencv.core.Point3
import org.opencv.features2d.BFMatcher

/**
 * The OpenCV implementation of [Relocalizer]: match the live frame's ORB descriptors against
 * the map's, then `solvePnPRansac` on the resulting 2D↔3D correspondences to find where the
 * live camera sits in the map frame. The offline counterpart to resolving a Cloud Anchor.
 *
 * Requires OpenCV's native library to be loaded first (`OpenCvLoader` on Android, the desktop
 * loader in the bench). Pure — no Android — so the identical code is measured off-device.
 */
class OrbRelocalizer(
    /** Lowe ratio-test threshold; lower is stricter. */
    private val ratio: Float = 0.75f,
    /** RANSAC reprojection tolerance, in pixels. */
    private val reprojectionErrorPx: Double = 4.0,
    /** Below this many PnP inliers, report [RelocalizationResult.NotFound] rather than guess. */
    private val minInliers: Int = 15,
) : Relocalizer {

    private val matcher: BFMatcher = BFMatcher.create(Core.NORM_HAMMING, false)

    override fun relocalize(
        map: WorldMap,
        liveFeatures: ExtractedFeatures,
        intrinsics: CameraIntrinsics,
        cameraInSession: Mat4,
    ): RelocalizationResult {
        if (liveFeatures.count < minInliers) return RelocalizationResult.NotFound

        // Flatten the map into one descriptor matrix and a parallel list of 3D points.
        val mapPoints = ArrayList<Float3>(map.pointCount)
        val descBytes = liveFeatures.descriptorBytes
        val mapDescList = ArrayList<ByteArray>(map.pointCount)
        for (kf in map.keyframes) {
            val f = kf.features
            for (i in 0 until f.count) {
                mapPoints.add(f.worldPoints[i])
                mapDescList.add(f.descriptors.copyOfRange(i * descBytes, (i + 1) * descBytes))
            }
        }
        if (mapPoints.size < minInliers) return RelocalizationResult.NotFound

        val mapDesc = descriptorMat(flatten(mapDescList), mapPoints.size, descBytes)
        val liveDesc = descriptorMat(liveFeatures.descriptors, liveFeatures.count, descBytes)
        val knn = ArrayList<MatOfDMatch>()
        try {
            // For each live descriptor, its two nearest map descriptors; keep it only if the best
            // is clearly better than the runner-up (Lowe's ratio test rejects ambiguous matches).
            matcher.knnMatch(liveDesc, mapDesc, knn, 2)
            val objectPts = ArrayList<Point3>()
            val imagePts = ArrayList<Point>()
            for (pair in knn) {
                val m = pair.toArray()
                if (m.size < 2) continue
                if (m[0].distance < ratio * m[1].distance) {
                    val p = mapPoints[m[0].trainIdx]
                    objectPts.add(Point3(p.x.toDouble(), p.y.toDouble(), p.z.toDouble()))
                    val li = m[0].queryIdx
                    imagePts.add(Point(liveFeatures.keypoints[li * 2].toDouble(),
                        liveFeatures.keypoints[li * 2 + 1].toDouble()))
                }
            }
            if (objectPts.size < minInliers) return RelocalizationResult.NotFound

            return solve(objectPts, imagePts, intrinsics, cameraInSession)
        } finally {
            mapDesc.release(); liveDesc.release(); knn.forEach { it.release() }
        }
    }

    private fun solve(
        objectPts: List<Point3>,
        imagePts: List<Point>,
        intrinsics: CameraIntrinsics,
        cameraInSession: Mat4,
    ): RelocalizationResult {
        val obj = MatOfPoint3f().apply { fromList(objectPts) }
        val img = MatOfPoint2f().apply { fromList(imagePts) }
        val k = cameraMatrix(intrinsics)
        val dist = MatOfByte() // no distortion; ARCore intrinsics are already rectified
        val rvec = Mat(); val tvec = Mat(); val inliers = Mat(); val rot = Mat()
        try {
            val ok = Calib3d.solvePnPRansac(
                obj, img, k, org.opencv.core.MatOfDouble(0.0, 0.0, 0.0, 0.0, 0.0),
                rvec, tvec, false, 200, reprojectionErrorPx.toFloat(), 0.99, inliers, Calib3d.SOLVEPNP_ITERATIVE,
            )
            val inlierCount = if (inliers.empty()) 0 else inliers.rows()
            if (!ok || inlierCount < minInliers) return RelocalizationResult.NotFound

            Calib3d.Rodrigues(rvec, rot)
            val r = FloatArray(9) { rot.get(it / 3, it % 3)[0].toFloat() }
            val t = Float3(tvec.get(0, 0)[0].toFloat(), tvec.get(1, 0)[0].toFloat(), tvec.get(2, 0)[0].toFloat())
            val cameraInMap = cameraInMapFromPnp(r, t)
            return RelocalizationResult.Located(
                sessionFromMap = relocalizationTransform(cameraInMap, cameraInSession),
                cameraInMap = cameraInMap,
                inlierCount = inlierCount,
            )
        } finally {
            obj.release(); img.release(); k.release(); dist.release()
            rvec.release(); tvec.release(); inliers.release(); rot.release()
        }
    }

    private fun cameraMatrix(i: CameraIntrinsics): Mat = Mat(3, 3, CvType.CV_64F).apply {
        put(0, 0, i.fx.toDouble()); put(0, 2, i.cx.toDouble())
        put(1, 1, i.fy.toDouble()); put(1, 2, i.cy.toDouble())
        put(2, 2, 1.0)
    }

    private fun descriptorMat(bytes: ByteArray, rows: Int, cols: Int): Mat =
        Mat(rows, cols, CvType.CV_8U).apply { put(0, 0, bytes) }

    private fun flatten(rows: List<ByteArray>): ByteArray {
        if (rows.isEmpty()) return ByteArray(0)
        val out = ByteArray(rows.size * rows[0].size)
        var o = 0
        for (r in rows) { System.arraycopy(r, 0, out, o, r.size); o += r.size }
        return out
    }
}
