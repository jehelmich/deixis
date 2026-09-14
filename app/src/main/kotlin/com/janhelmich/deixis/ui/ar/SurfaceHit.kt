package com.janhelmich.deixis.ui.ar

import android.view.MotionEvent
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.github.sceneview.node.Node

/**
 * The first hit under [event] that lies on a tracked plane, inside the plane's polygon — the
 * same rule the 2019 app used to decide whether the crosshair was over a usable surface.
 */
fun surfaceHit(frame: Frame, event: MotionEvent): HitResult? {
    if (frame.camera.trackingState != TrackingState.TRACKING) return null
    return frame.hitTest(event).firstOrNull { hit ->
        val plane = hit.trackable as? Plane ?: return@firstOrNull false
        plane.trackingState == TrackingState.TRACKING && plane.isPoseInPolygon(hit.hitPose)
    }
}

/** Node names are the only cheap tag SceneView gives us; placements are named with this prefix. */
const val PLACEMENT_NAME_PREFIX = "placement:"

/** The placement a tapped node belongs to, walking up from whatever child was actually hit. */
tailrec fun Node.placementId(): String? {
    val name = name
    if (name != null && name.startsWith(PLACEMENT_NAME_PREFIX)) {
        return name.removePrefix(PLACEMENT_NAME_PREFIX)
    }
    return parent?.placementId()
}
