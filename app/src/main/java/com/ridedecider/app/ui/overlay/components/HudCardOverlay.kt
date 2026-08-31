package com.ridedecider.app.ui.overlay.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.ui.overlay.model.HudUiModel
import com.ridedecider.app.ui.overlay.model.HudVisualTier
import com.ridedecider.app.ui.theme.RdTierAcceptable
import com.ridedecider.app.ui.theme.RdTierBad
import com.ridedecider.app.ui.theme.RdTierExcellent
import com.ridedecider.app.ui.theme.RdTierGood

/**
 * Tarjeta visual de alto contraste para el HUD de conducción en parabrisas:
 *
 * ┌───────────────────────────────────────────┐
 * │  [◆ EXCELENTE]          [EFECTIVO] [RADAR]│
 * │                                           │
 * │         12,50 €  ·  4,2 km  ·  7 min      │
 * │                                           │
 * │          1,32 €/km   ·   38,50 €/h        │
 * └───────────────────────────────────────────┘
 *
 * Cumple estándares WCAG AAA para visualización bajo luz solar directa:
 * - Tipografía principal en blanco puro 20sp Black.
 * - Tipografía secundaria en Slate-200 (#E2E8F0) 14sp Bold.
 * - Píldora de recomendación con fondo saturado y borde de alta visibilidad.
 * - 100% click-through (FLAG_NOT_TOUCHABLE).
 */
@Composable
fun HudCardOverlay(
    model: HudUiModel,
    modifier: Modifier = Modifier
) {
    val tier = model.tier
    val tierColor = when (tier) {
        HudVisualTier.EXCELLENT -> RdTierExcellent
        HudVisualTier.GOOD -> RdTierGood
        HudVisualTier.ACCEPTABLE -> RdTierAcceptable
        HudVisualTier.BAD -> RdTierBad
    }
    val cardBackground = Color(0xF80B0F17) // Fondo ultra oscuro sólido para contraste bajo sol

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = tierColor.copy(alpha = 0.90f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Cabecera: Píldora de Recomendación + Distintivos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Píldora de Nivel Cualitativo de Alto Contraste
                Box(
                    modifier = Modifier
                        .background(tierColor.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                        .border(1.dp, tierColor.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = tier.icon,
                            contentDescription = tier.label,
                            tint = tierColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = tier.label,
                            color = tierColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                // Indicadores discretos si aplican (Efectivo / Radar)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (model.isCashPayment) {
                        Text(
                            text = "EFECTIVO",
                            color = Color(0xFFFDE047),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .background(Color(0x33FACC15), RoundedCornerShape(5.dp))
                                .border(1.dp, Color(0x66FACC15), RoundedCornerShape(5.dp))
                                .padding(horizontal = 6.dp, vertical = 2.5.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (model.offerType == TripOfferType.RADAR_OFFER) {
                        Text(
                            text = "RADAR",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color(0x3364748B), RoundedCornerShape(5.dp))
                                .border(1.dp, Color(0x6694A3B8), RoundedCornerShape(5.dp))
                                .padding(horizontal = 6.dp, vertical = 2.5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(7.dp))

            // 2. Línea Principal: Precio · km · tiempo (Alta visibilidad con luz solar)
            val detailsLine = buildString {
                append(model.fareText)
                if (model.totalDistanceText.isNotBlank()) {
                    append("   ·   ")
                    append(model.totalDistanceText)
                }
                if (model.totalDurationText.isNotBlank()) {
                    append("   ·   ")
                    append(model.totalDurationText)
                }
            }

            Text(
                text = detailsLine,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.2.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 3. Línea Secundaria: Ratios de Rentabilidad (€/km · €/h en Slate-200)
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.grossPerKmText,
                    color = Color(0xFFE2E8F0),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "   ·   ",
                    color = Color(0x9994A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = model.grossPerHourText,
                    color = Color(0xFFE2E8F0),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
