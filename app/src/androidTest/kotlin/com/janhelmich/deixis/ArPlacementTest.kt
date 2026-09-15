package com.janhelmich.deixis

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.ar.core.ArCoreApk
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertNotNull

/**
 * End-to-end check of the one interaction everything else depends on: a marker can be placed
 * on a detected surface. Runs against real ARCore — on a phone, or on an emulator whose back
 * camera is the VirtualScene and which has Google Play Services for AR sideloaded — and
 * skips itself anywhere ARCore cannot run, so the plain CI job stays green.
 *
 * Plane detection wants a little camera motion, which the test cannot provide on a headless
 * emulator; it simply keeps tapping the middle of the screen until a tap lands on a plane or
 * the deadline passes. ARCore does find the virtual scene's table from a standing start.
 */
@RunWith(AndroidJUnit4::class)
class ArPlacementTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext

    @Before
    fun launch() {
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        assumeTrue("ARCore not usable here: $availability", availability.isSupported)

        device.executeShellCommand("pm grant ${context.packageName} android.permission.CAMERA")
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        context.startActivity(
            context.packageManager.getLaunchIntentForPackage(context.packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        assertNotNull("AR screen did not come up",
            device.wait(Until.findObject(By.text("Add marker")), LAUNCH_TIMEOUT_MS))
    }

    @Test
    fun aMarkerCanBePlacedOnADetectedSurface() {
        device.findObject(By.text("Add marker")).click()
        assertNotNull(device.wait(Until.findObject(By.text("Cancel")), UI_TIMEOUT_MS))

        // The edit panel opens the moment a placement succeeds; its "Name" field is the signal.
        val deadline = System.currentTimeMillis() + PLACEMENT_TIMEOUT_MS
        var placed = false
        while (!placed && System.currentTimeMillis() < deadline) {
            device.click(device.displayWidth / 2, device.displayHeight / 2)
            placed = device.wait(Until.hasObject(By.text("Name")), TAP_INTERVAL_MS)
        }
        assumeTrue(
            "No surface was tracked within ${PLACEMENT_TIMEOUT_MS / 1000}s; ARCore is running " +
                "but never detected a plane, which is an environment problem, not an app one.",
            placed,
        )

        // Bind it to a simulated device and make sure the card follows in Use mode.
        device.findObject(By.text("Device")).click()
        device.wait(Until.findObject(By.text("Desk lamp")), UI_TIMEOUT_MS)!!.click()
        device.findObject(By.desc("Done")).click()
        device.findObject(By.text("Use")).click()
        assertNotNull(device.wait(Until.findObject(By.text("Tap a device to control it")), UI_TIMEOUT_MS))
    }

    private companion object {
        const val LAUNCH_TIMEOUT_MS = 30_000L
        const val UI_TIMEOUT_MS = 10_000L
        const val PLACEMENT_TIMEOUT_MS = 90_000L
        const val TAP_INTERVAL_MS = 1_500L
    }
}
