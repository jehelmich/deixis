package com.janhelmich.deixis.data.relocalization

import org.opencv.core.Mat
import org.opencv.core.MatOfKeyPoint
import org.opencv.features2d.ORB

/**
 * OpenCV's ORB detector behind [FeatureExtractor]. ORB is the same class of oriented FAST +
 * rotated BRIEF binary descriptor that classic visual-relocalization pipelines use; each
 * descriptor is 32 bytes, matched by Hamming distance.
 *
 * This is the only class in the package that touches OpenCV, and OpenCV's native library must
 * be loaded before it is constructed — see [OpenCvLoader]. Everything downstream
 * ([KeyframeBuilder], [WorldMapCodec], [FileMapStore]) works on plain arrays and is tested
 * without a device.
 */
class OrbFeatureExtractor(maxFeatures: Int = 1200) : FeatureExtractor {

    private val orb: ORB = ORB.create(maxFeatures)

    override fun extract(gray: ByteArray, width: Int, height: Int): ExtractedFeatures {
        require(gray.size >= width * height) { "gray buffer too small for ${width}x$height" }
        val image = Mat(height, width, org.opencv.core.CvType.CV_8UC1)
        val keypoints = MatOfKeyPoint()
        val descriptors = Mat()
        try {
            image.put(0, 0, gray)
            orb.detectAndCompute(image, Mat(), keypoints, descriptors)

            val kp = keypoints.toArray()
            val count = kp.size
            if (count == 0 || descriptors.empty()) return ExtractedFeatures.EMPTY

            val bytesPerRow = descriptors.cols() // 32 for ORB
            val coords = FloatArray(count * 2)
            for (i in 0 until count) {
                coords[i * 2] = kp[i].pt.x.toFloat()
                coords[i * 2 + 1] = kp[i].pt.y.toFloat()
            }
            val descBytes = ByteArray(count * bytesPerRow)
            descriptors.get(0, 0, descBytes)
            return ExtractedFeatures(count, coords, descBytes, bytesPerRow)
        } catch (t: Throwable) {
            logger.log(java.util.logging.Level.WARNING, "ORB extraction failed", t)
            return ExtractedFeatures.EMPTY
        } finally {
            image.release(); keypoints.release(); descriptors.release()
        }
    }

    private companion object { val logger: java.util.logging.Logger = java.util.logging.Logger.getLogger("OrbFeatureExtractor") }
}
