package com.ridedecider.app.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridedecider.app.domain.model.DecisionMode
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.ui.overlay.model.HudUiModel
import com.ridedecider.app.ui.overlay.model.HudVisualTier
import com.ridedecider.app.ui.theme.RdBadgeCash
import com.ridedecider.app.ui.theme.RdBadgeDirect
import com.ridedecider.app.ui.theme.RdBadgeRadar
import com.ridedecider.app.ui.theme.RdBorderSubtle
import com.ridedecider.app.ui.theme.RdSurface
import com.ridedecider.app.ui.theme.RdSurfaceElevated
import com.ridedecider.app.ui.theme.RdSurfaceInteractive
import com.ridedecider.app.ui.theme.RdTextPrimary
import com.ridedecider.app.ui.theme.RdTextSecondary
import com.ridedecider.app.ui.theme.RdTextTertiary
import com.ridedecider.app.ui.theme.RdTierAcceptable
import com.ridedecider.app.ui.theme.RdTierBad
import com.ridedecider.app.ui.theme.RdTierExcellent
import com.ridedecider.app.ui.theme.RdTierGood

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon

@Composable
fun RecommendationHeroCard(
    evaluation: HudUiModel?,
    modifier: Modifier = Modifier,
    isLiveMode: Boolean = false
) {
    if (evaluation == null) {
        // Estado vacío / Esperando oferta
        Card(
            modifier = modifier
                .fillMaxWidth()
                .border(1.dp, RdBorderSubtle, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = RdSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (isLiveMode) 28.dp else 22.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(RdSurfaceElevated, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sensors,
                        contentDescription = "Radar de escucha activo",
                        tint = RdTextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .background(RdSurfaceInteractive, RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isLiveMode) "ESCUCHANDO UBER DRIVER" else "ESPERANDO OFERTA",
                        color = RdTextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isLiveMode) "Esperando próxima oferta de Uber Driver..." else "No hay evaluaciones recientes",
                    color = RdTextSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
        return
    }

    val tier = evaluation.tier
    val tierColor = when (tier) {
        HudVisualTier.EXCELLENT -> RdTierExcellent
        HudVisualTier.GOOD -> RdTierGood
        HudVisualTier.ACCEPTABLE -> RdTierAcceptable
        HudVisualTier.BAD -> RdTierBad
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isLiveMode) 2.dp else 1.5.dp,
                color = tierColor.copy(alpha = 0.8f),
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RdSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isLiveMode) 20.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Fila superior: Badge y Tipo de oferta (Directa / Radar / Efectivo)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RecommendationBadge(tier = tier, isLarge = isLiveMode)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (evaluation.isCashPayment) {
                        Text(
                            text = "EFECTIVO",
                            color = RdBadgeCash,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(RdBadgeCash.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .border(1.dp, RdBadgeCash.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    val offerBadgeText = if (evaluation.offerType == TripOfferType.RADAR_OFFER) "TRIP RADAR" else "OFERTA DIRECTA"
                    val offerBadgeColor = if (evaluation.offerType == TripOfferType.RADAR_OFFER) RdBadgeRadar else RdBadgeDirect
                    Text(
                        text = offerBadgeText,
                        color = offerBadgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(offerBadgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(1.dp, offerBadgeColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // R6.3: en AUTOMATIC, mostrar la semantica automatica (recommendation / quality /
            // confidence) entre los badges superiores y la tarifa. En MANUAL este bloque
            // no se pinta y la tarjeta preserva su composicion previa.
            if (evaluation.decisionMode == DecisionMode.AUTOMATIC && evaluation.recommendationText != null) {
                Spacer(modifier = Modifier.height(if (isLiveMode) 10.dp else 8.dp))
                Text(
                    text = evaluation.recommendationText,
                    color = tierColor,
                    fontSize = if (isLiveMode) 15.sp else 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.6.sp
                )
                // Sub-linea compacta con Calidad y Confianza si el mapper las proporciono.
                // qualityText es null cuando el assessment es null (fallback conservador):
                // en ese caso no se pinta nada, sin fabricar valores.
                val qualityText = evaluation.qualityText
                val confidenceText = evaluation.confidenceText
                if (qualityText != null || confidenceText != null) {
                    val parts = listOfNotNull(
                        qualityText?.let { "Calidad $it" },
                        confidenceText
                    )
                    Text(
                        text = parts.joinToString("   ·   "),
                        color = RdTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isLiveMode) 14.dp else 10.dp))

            // Tarifa Prominente
            Text(
                text = evaluation.fareText,
                color = RdTextPrimary,
                fontSize = if (isLiveMode) 38.sp else 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )

            // Cinemática esencial (Distancia total · Duración estimada)
            if (evaluation.totalDistanceText.isNotBlank() || evaluation.totalDurationText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                val kinematicText = buildString {
                    if (evaluation.totalDistanceText.isNotBlank()) append(evaluation.totalDistanceText)
                    if (evaluation.totalDistanceText.isNotBlank() && evaluation.totalDurationText.isNotBlank()) append(" · ")
                    if (evaluation.totalDurationText.isNotBlank()) append(evaluation.totalDurationText)
                }
                Text(
                    text = kinematicText,
                    color = RdTextSecondary,
                    fontSize = if (isLiveMode) 15.sp else 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(if (isLiveMode) 14.dp else 10.dp))

            // Ratios de Rentabilidad (€/km  ·  €/h)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RdSurfaceElevated, RoundedCornerShape(10.dp))
                    .padding(vertical = if (isLiveMode) 10.dp else 8.dp, horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = evaluation.grossPerKmText,
                            color = RdTextPrimary,
                            fontSize = if (isLiveMode) 16.sp else 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "€ / km",
                            color = RdTextTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(RdBorderSubtle)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = evaluation.grossPerHourText,
                            color = RdTextPrimary,
                            fontSize = if (isLiveMode) 16.sp else 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "€ / hora",
                            color = RdTextTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // R6.3: pie compacto de override (solo AUTOMATIC + speOverridden). Muestra el
            // texto que ya viene del OpportunityEvaluator; la UI no lo interpreta.
            if (evaluation.decisionMode == DecisionMode.AUTOMATIC &&
                evaluation.isOverride &&
                evaluation.overrideText != null
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = evaluation.overrideText,
                    color = RdTextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
