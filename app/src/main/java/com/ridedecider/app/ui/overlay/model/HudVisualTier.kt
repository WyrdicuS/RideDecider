package com.ridedecider.app.ui.overlay.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.ridedecider.app.ui.theme.RdTierAcceptable
import com.ridedecider.app.ui.theme.RdTierBad
import com.ridedecider.app.ui.theme.RdTierExcellent
import com.ridedecider.app.ui.theme.RdTierGood

/**
 * Los 4 niveles visuales del HUD de conducción sincronizados con el Design System:
 *
 * EXCELENTE   -> Kinetic Iris   (RdTierExcellent)
 * BUENO       -> Mint Emerald   (RdTierGood)
 * ACEPTABLE   -> Warm Amber     (RdTierAcceptable)
 * MALO        -> Coral Crimson  (RdTierBad)
 */
enum class HudVisualTier(
    val label: String,
    val icon: ImageVector,
    val color: Color
) {
    EXCELLENT(
        label = "EXCELENTE",
        icon = Icons.Rounded.AutoAwesome,
        color = RdTierExcellent
    ),
    GOOD(
        label = "BUENO",
        icon = Icons.Rounded.CheckCircle,
        color = RdTierGood
    ),
    ACCEPTABLE(
        label = "ACEPTABLE",
        icon = Icons.Rounded.Info,
        color = RdTierAcceptable
    ),
    BAD(
        label = "MALO",
        icon = Icons.Rounded.Cancel,
        color = RdTierBad
    )
}
