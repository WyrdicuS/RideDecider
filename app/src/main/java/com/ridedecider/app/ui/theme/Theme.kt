package com.ridedecider.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat

private val RideDeciderDarkColorScheme = darkColorScheme(
    primary = RdBrandPrimary,
    onPrimary = RdTextPrimary,
    primaryContainer = RdBrandPrimaryDark,
    onPrimaryContainer = RdTextPrimary,
    secondary = RdBrandPrimaryLight,
    onSecondary = RdBgBase,
    secondaryContainer = RdSurfaceElevated,
    onSecondaryContainer = RdTextPrimary,
    tertiary = RdTierGood,
    onTertiary = RdBgBase,
    background = RdBgBase,
    onBackground = RdTextPrimary,
    surface = RdSurface,
    onSurface = RdTextPrimary,
    surfaceVariant = RdSurfaceElevated,
    onSurfaceVariant = RdTextSecondary,
    outline = RdBorderSubtle,
    outlineVariant = RdBorderProminent
)

@Composable
fun RideDeciderTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = RdBackground.toArgb()
                window.navigationBarColor = RdBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = RideDeciderDarkColorScheme,
        typography = Typography,
        content = {
            CompositionLocalProvider(
                LocalTextStyle provides TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    color = RdTextPrimary
                )
            ) {
                content()
            }
        }
    )
}