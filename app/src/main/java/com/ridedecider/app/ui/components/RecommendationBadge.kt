package com.ridedecider.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.ui.overlay.model.HudVisualTier
import com.ridedecider.app.ui.theme.RdTierAcceptable
import com.ridedecider.app.ui.theme.RdTierBad
import com.ridedecider.app.ui.theme.RdTierExcellent
import com.ridedecider.app.ui.theme.RdTierGood

@Composable
fun RecommendationBadge(
    tier: HudVisualTier,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false
) {
    val tierColor = when (tier) {
        HudVisualTier.EXCELLENT -> RdTierExcellent
        HudVisualTier.GOOD -> RdTierGood
        HudVisualTier.ACCEPTABLE -> RdTierAcceptable
        HudVisualTier.BAD -> RdTierBad
    }

    Row(
        modifier = modifier
            .background(
                color = tierColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = tierColor.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(
                horizontal = if (isLarge) 12.dp else 8.dp,
                vertical = if (isLarge) 6.dp else 3.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = tier.icon,
            contentDescription = tier.label,
            tint = tierColor,
            modifier = Modifier.size(if (isLarge) 16.dp else 12.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = tier.label,
            color = tierColor,
            fontSize = if (isLarge) 14.sp else 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp
        )
    }
}

fun Decision.toVisualTier(): HudVisualTier {
    return when (this) {
        Decision.ACCEPT -> HudVisualTier.EXCELLENT
        Decision.REJECT -> HudVisualTier.BAD
        Decision.UNKNOWN -> HudVisualTier.ACCEPTABLE
    }
}
