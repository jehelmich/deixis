package com.janhelmich.deixis.ui.ar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.google.android.filament.MaterialInstance
import com.janhelmich.deixis.domain.DeviceKind
import com.janhelmich.deixis.domain.DeviceState
import io.github.sceneview.SceneScope
import io.github.sceneview.material.setColor
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size

/**
 * A device's 3D body, built from primitives so the repository needs no model assets and the
 * shapes can react to state — a bulb glows, a plug's LED turns green.
 *
 * All sizes are metres; every shape rests on the anchor's surface (y = 0). The shapes stay
 * touchable: a tap on one is resolved to the owning anchor by [placementId], and drag/twist
 * gestures bubble up to the anchor node because the shapes themselves are not editable.
 */
@Composable
fun SceneScope.DeviceGeometry(kind: DeviceKind?, state: DeviceState) {
    when (kind) {
        DeviceKind.PLUG -> PlugGeometry(state as? DeviceState.Plug)
        DeviceKind.LIGHT -> LightGeometry(state as? DeviceState.Light)
        DeviceKind.SENSOR -> SensorGeometry(state as? DeviceState.Sensor)
        null -> MarkerGeometry()
    }
}

/** A flat ring on the surface under the selected marker, so it is obvious which one gestures act on. */
@Composable
fun SceneScope.SelectionRing() {
    val ring = rememberColorMaterial(Palette.Marker, metallic = 0f, roughness = 0.9f)
    CylinderNode(radius = 0.075f, height = 0.003f, position = Position(y = 0.0015f), materialInstance = ring)
}

/** A marker that has not been assigned a device yet: a pin, so it reads as "something goes here". */
@Composable
private fun SceneScope.MarkerGeometry() {
    val pin = rememberColorMaterial(Palette.Marker, metallic = 0.2f, roughness = 0.5f)
    CylinderNode(radius = 0.004f, height = 0.09f, position = Position(y = 0.045f), materialInstance = pin)
    SphereNode(radius = 0.02f, position = Position(y = 0.10f), materialInstance = pin)
}

@Composable
private fun SceneScope.PlugGeometry(state: DeviceState.Plug?) {
    val body = rememberColorMaterial(Palette.Body, roughness = 0.6f)
    val led = rememberColorMaterial(
        when {
            state == null -> Palette.Fault
            state.isOn -> Palette.LedOn
            else -> Palette.LedOff
        },
        roughness = 0.3f,
    )
    CubeNode(
        size = Size(0.08f, 0.045f, 0.08f),
        position = Position(y = 0.0225f),
        materialInstance = body,
    )
    SphereNode(
        radius = 0.006f,
        position = Position(x = 0.028f, y = 0.045f, z = 0.028f),
        materialInstance = led,
    )
}

@Composable
private fun SceneScope.LightGeometry(state: DeviceState.Light?) {
    val metal = rememberColorMaterial(Palette.Metal, metallic = 0.9f, roughness = 0.35f)
    val glass = rememberColorMaterial(
        when {
            state == null -> Palette.Fault
            state.isOn -> lerp(Palette.BulbDim, Palette.BulbBright, state.brightness ?: 1f)
            else -> Palette.BulbOff
        },
        roughness = 0.2f,
    )
    CylinderNode(radius = 0.035f, height = 0.015f, position = Position(y = 0.0075f),
        materialInstance = metal)
    CylinderNode(radius = 0.006f, height = 0.13f, position = Position(y = 0.08f),
        materialInstance = metal)
    SphereNode(radius = 0.045f, position = Position(y = 0.19f),
        materialInstance = glass)
}

@Composable
private fun SceneScope.SensorGeometry(state: DeviceState.Sensor?) {
    val body = rememberColorMaterial(if (state == null) Palette.Fault else Palette.Body, roughness = 0.7f)
    val antenna = rememberColorMaterial(Palette.Metal, metallic = 0.8f, roughness = 0.4f)
    CubeNode(size = Size(0.06f, 0.02f, 0.04f), position = Position(y = 0.01f),
        materialInstance = body)
    CylinderNode(radius = 0.003f, height = 0.05f, position = Position(x = 0.02f, y = 0.045f),
        materialInstance = antenna)
}

/**
 * One material instance per shape, recoloured in place when [color] changes rather than
 * recreated, and released when the shape leaves the scene.
 */
@Composable
private fun SceneScope.rememberColorMaterial(
    color: Color,
    metallic: Float = 0f,
    roughness: Float = 0.5f,
): MaterialInstance {
    val material = remember(materialLoader) {
        materialLoader.createColorInstance(color, metallic = metallic, roughness = roughness)
    }
    LaunchedEffect(material, color) { material.setColor(color.toArgb()) }
    DisposableEffect(material) { onDispose { materialLoader.destroyMaterialInstance(material) } }
    return material
}

private object Palette {
    val Body = Color(0xFFF2F2F0)
    val Metal = Color(0xFF8A8F98)
    val LedOn = Color(0xFF2ECC71)
    val LedOff = Color(0xFF3A3F47)
    val BulbOff = Color(0xFFB9BCC2)
    val BulbDim = Color(0xFFFFE0A3)
    val BulbBright = Color(0xFFFFF4D6)
    val Fault = Color(0xFFB00020)
    val Marker = Color(0xFF3D8BFF)
}
