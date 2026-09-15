package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import dev.romainguy.kotlin.math.Quaternion
import dev.romainguy.kotlin.math.dot
import dev.romainguy.kotlin.math.length
import dev.romainguy.kotlin.math.normalize
import dev.romainguy.kotlin.math.rotation
import dev.romainguy.kotlin.math.slerp
import dev.romainguy.kotlin.math.translation
import kotlin.math.abs
import kotlin.math.acos

/** Small rigid-transform helpers shared by the alignment filter and the app's anchor code. */

/** `[R | t]`: rotate by [rotation], then translate by [translation]. */
fun rigidTransform(rotation: Quaternion, translation: Float3): Mat4 =
    translation(translation) * rotation(rotation)

/** The rotation part of a rigid transform, as a unit quaternion. */
val Mat4.rotationQuaternion: Quaternion
    get() = normalize(rotation(this).toQuaternion())

/** How far apart two rigid transforms are: metres between origins, degrees between rotations. */
data class PoseDelta(val translationMeters: Float, val rotationDegrees: Float)

fun poseDelta(a: Mat4, b: Mat4): PoseDelta {
    val dt = length(a.translation - b.translation)
    val qa = a.rotationQuaternion
    val qb = b.rotationQuaternion
    // q and -q are the same rotation; |dot| folds that. angle = 2·acos(|q_a·q_b|).
    val d = abs(dot(qa, qb)).coerceIn(0f, 1f)
    val deg = Math.toDegrees(2.0 * acos(d.toDouble())).toFloat()
    return PoseDelta(dt, deg)
}

/** Move [t] of the way from [from] to [to]: slerp on rotation, lerp on translation. */
fun blendPose(from: Mat4, to: Mat4, t: Float): Mat4 {
    val q = normalize(slerp(from.rotationQuaternion, to.rotationQuaternion, t))
    val p = from.translation + (to.translation - from.translation) * t
    return rigidTransform(q, p)
}
