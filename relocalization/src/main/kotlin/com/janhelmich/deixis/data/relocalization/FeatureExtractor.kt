package com.janhelmich.deixis.data.relocalization

/** ORB features found in one image, before any 3D is known. Parallel arrays, [count] long. */
data class ExtractedFeatures(
    val count: Int,
    /** `count` pairs of (x, y) pixel coordinates. */
    val keypoints: FloatArray,
    /** `count * descriptorBytes` bytes, row-major. */
    val descriptors: ByteArray,
    val descriptorBytes: Int = ObservedFeatures.ORB_DESCRIPTOR_BYTES,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)

    companion object {
        val EMPTY = ExtractedFeatures(0, FloatArray(0), ByteArray(0))
    }
}

/**
 * Finds repeatable feature points in a greyscale image. The one implementation
 * ([OrbFeatureExtractor]) uses OpenCV; the interface keeps OpenCV out of the map-building and
 * storage code so all of that stays unit-testable on the JVM.
 */
interface FeatureExtractor {
    /** @param gray row-major 8-bit luminance, [width] × [height]. */
    fun extract(gray: ByteArray, width: Int, height: Int): ExtractedFeatures
}
