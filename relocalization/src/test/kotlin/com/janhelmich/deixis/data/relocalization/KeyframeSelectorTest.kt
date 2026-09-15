package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import dev.romainguy.kotlin.math.rotation
import dev.romainguy.kotlin.math.translation
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyframeSelectorTest {

    private val cfg = KeyframeSelectorConfig(minTranslationMeters = 0.25f, minRotationDegrees = 15f, minFeatures = 100, maxKeyframes = 3)

    @Test
    fun `captures the first frame, then only genuinely new viewpoints`() {
        val s = KeyframeSelector(cfg)
        val origin = Mat4.identity()
        assertTrue(s.shouldCapture(origin, 500)); s.recordCaptured(origin)
        assertFalse(s.shouldCapture(translation(Float3(0.1f, 0f, 0f)), 500), "10 cm is not new")
        assertFalse(s.shouldCapture(rotation(Float3(0f, 1f, 0f), 5f), 500), "5° is not new")
        assertTrue(s.shouldCapture(translation(Float3(0.3f, 0f, 0f)), 500), "30 cm is new")
        assertTrue(s.shouldCapture(rotation(Float3(0f, 1f, 0f), 20f), 500), "20° is new")
    }

    @Test
    fun `refuses thin frames and stops at the cap`() {
        val s = KeyframeSelector(cfg)
        assertFalse(s.shouldCapture(Mat4.identity(), 50), "too few features")
        for (i in 0 until 3) s.recordCaptured(translation(Float3(i.toFloat(), 0f, 0f)))
        assertFalse(s.shouldCapture(translation(Float3(10f, 0f, 0f)), 500), "at cap")
    }
}
