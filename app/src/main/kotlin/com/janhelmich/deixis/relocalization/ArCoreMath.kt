package com.janhelmich.deixis.relocalization

import com.google.ar.core.Pose
import com.janhelmich.deixis.data.relocalization.rotationQuaternion
import dev.romainguy.kotlin.math.Float4
import dev.romainguy.kotlin.math.Mat4

/**
 * ARCore ↔ kotlin-math. ARCore's `Pose.toMatrix` is column-major (OpenGL style); kotlin-math's
 * `Mat4` constructor takes columns, so the copy is direct. ARCore's camera convention (X right,
 * Y up, Z back) is the one the relocalization geometry already uses.
 */
fun Pose.toMat4(): Mat4 {
    val m = FloatArray(16)
    toMatrix(m, 0)
    return Mat4(
        Float4(m[0], m[1], m[2], m[3]),
        Float4(m[4], m[5], m[6], m[7]),
        Float4(m[8], m[9], m[10], m[11]),
        Float4(m[12], m[13], m[14], m[15]),
    )
}

fun Mat4.toPose(): Pose {
    val q = rotationQuaternion
    val t = translation
    return Pose(floatArrayOf(t.x, t.y, t.z), floatArrayOf(q.x, q.y, q.z, q.w))
}
