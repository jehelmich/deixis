package com.janhelmich.deixis.data.relocalization

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Float4
import dev.romainguy.kotlin.math.Mat4

/**
 * Turns `solvePnP`'s output into the camera→map transform this pipeline's algebra expects.
 *
 * `solvePnP` returns a rotation `R` (as a 3×3, after `Rodrigues`) and translation `t` such that a
 * map point `X` maps to the **OpenCV camera** frame as `X_cv = R·X + t` — X right, Y down, Z
 * into the scene. ARCore's camera frame is X right, Y up, Z **out** of the scene (a point in
 * front has negative Z), which is the convention [CameraIntrinsics.unproject] and the rest of
 * this code use. The two differ by `F = diag(1, −1, −1)`.
 *
 * So the ARCore camera→map transform is
 *
 *     (cv-camera→map) · (arc-camera→cv-camera) = [Rᵀ | −Rᵀt] · F
 *
 * i.e. rotation `Rᵀ·F` (negate the 2nd and 3rd columns of `Rᵀ`) and translation `−Rᵀt`. This is
 * the single most error-prone step in the pipeline, so it is isolated here and pinned by
 * [PnpConversionTest] against hand-built poses, with no OpenCV needed to check it.
 *
 * @param rotationRowMajor the 3×3 `R` in row-major order (9 floats), as read from the
 *   `Rodrigues` output.
 * @param t the translation `t`.
 */
fun cameraInMapFromPnp(rotationRowMajor: FloatArray, t: Float3): Mat4 {
    require(rotationRowMajor.size == 9) { "expected a 3x3 rotation, got ${rotationRowMajor.size} values" }
    val r = rotationRowMajor
    // Rᵀ rows are R's columns. Rᵀ[i][j] = R[j][i].
    // Column j of Rᵀ = row j of R. Negating columns 2,3 of Rᵀ multiplies Rᵀ by F on the right.
    // Build the rotation part of Rᵀ·F directly.
    val rtF = arrayOf(
        floatArrayOf(r[0], -r[3], -r[6]),
        floatArrayOf(r[1], -r[4], -r[7]),
        floatArrayOf(r[2], -r[5], -r[8]),
    )
    // translation = −Rᵀ·t
    val tx = -(r[0] * t.x + r[3] * t.y + r[6] * t.z)
    val ty = -(r[1] * t.x + r[4] * t.y + r[7] * t.z)
    val tz = -(r[2] * t.x + r[5] * t.y + r[8] * t.z)
    // kotlin-math Mat4 columns are x, y, z (basis) and w (translation).
    return Mat4(
        Float4(rtF[0][0], rtF[1][0], rtF[2][0], 0f),
        Float4(rtF[0][1], rtF[1][1], rtF[2][1], 0f),
        Float4(rtF[0][2], rtF[1][2], rtF[2][2], 0f),
        Float4(tx, ty, tz, 1f),
    )
}
