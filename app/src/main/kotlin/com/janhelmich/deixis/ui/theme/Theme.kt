package com.janhelmich.deixis.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// The 2019 app's teal, kept as the fallback brand colour on devices without dynamic colour.
private val Teal = Color(0xFF008577)
private val TealDark = Color(0xFF4DB6AC)
private val Accent = Color(0xFFD81B60)

private val LightScheme = lightColorScheme(primary = Teal, tertiary = Accent)
private val DarkScheme = darkColorScheme(primary = TealDark, tertiary = Accent)

@Composable
fun DeixisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
