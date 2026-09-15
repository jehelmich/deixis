package com.janhelmich.deixis.bench

import dev.romainguy.kotlin.math.Float3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** The pose parsing and convention flip, tested without the dataset. */
class SevenScenesPoseTest {

    @Test
    fun `parses a 4x4 camera-to-world pose row-major`() {
        val pose = SevenScenes.readPose(
            """
            1 0 0 0.5
            0 1 0 1.5
            0 0 1 -2.0
            0 0 0 1
            """.trimIndent(),
        )
        // Translation is the last column.
        assertTrue(abs(pose.w.x - 0.5f) < 1e-6f && abs(pose.w.y - 1.5f) < 1e-6f && abs(pose.w.z + 2f) < 1e-6f)
    }

    @Test
    fun `the Kinect to ARCore flip negates the camera's Y and Z axes, keeps the centre`() {
        val kinect = SevenScenes.readPose("1 0 0 3\n0 1 0 4\n0 0 1 5\n0 0 0 1")
        val arc = SevenScenes.poseArcFromKinect(kinect)
        // Same camera centre (translation), flipped Y/Z basis.
        assertTrue(abs(arc.w.x - 3f) < 1e-6f && abs(arc.w.y - 4f) < 1e-6f && abs(arc.w.z - 5f) < 1e-6f)
        assertTrue(arc.y.y < 0f && arc.z.z < 0f, "Y and Z axes should be flipped")
    }
}
