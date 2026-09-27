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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
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
import com.ridedecider.app.domain.model.DecisionMode
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.ui.overlay.model.HudUiModel
import com.ridedecider.app.ui.overlay.model.HudVisualTier
import com.ridedecider.app.ui.theme.RdBadgeCash
import com.ridedecider.app.ui.theme.RdBadgeDirect
import com.ridedecider.app.ui.theme.RdBadgeRadar
import com.ridedecider.app.ui.theme.RdBorderSubtle
import com.ridedecider.app.ui.theme.RdBrandPrimaryLight
import com.ridedecider.app.ui.theme.RdStatusAhead
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

@Composable
fun RecommendationHeroCard(
    evaluation: HudUiModel?,
    modifier: Modifier = Modifier,
    isLiveMode: Boolean = false,
    pickupAddress: String? = null,
    dropoffAddress: String? = null
) {
    if (evaluation == null) {
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
            // 1. Fila superior: badge de tier + badges de tipo de oferta.
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
                            letterSpacing = 0.5.sp,
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
                        letterSpacing = 0.5.sp,
                        modifier = Modifier
                            .background(offerBadgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(1.dp, offerBadgeColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // 2. En AUTOMATIC: texto de recomendacion (destacado) + linea Calidad/Confianza.
            if (evaluation.decisionMode == DecisionMode.AUTOMATIC && evaluation.recommendationText != null) {
                Spacer(modifier = Modifier.height(if (isLiveMode) 12.dp else 8.dp))
                Text(
                    text = evaluation.recommendationText,
                    color = tierColor,
                    fontSize = if (isLiveMode) 16.sp else 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.6.sp
                )
                val qualityText = evaluation.qualityText
                val confidenceText = evaluation.confidenceText
                if (qualityText != null || confidenceText != null) {
                    Spacer(modifier = Modifier.height(2.dp))
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

            // 3. Tarifa prominente (numero hero).
            Text(
                text = evaluation.fareText,
                color = RdTextPrimary,
                fontSize = if (isLiveMode) 38.sp else 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )

            // 4. Cinematica compacta con icono: distancia total · duracion.
            if (evaluation.totalDistanceText.isNotBlank() || evaluation.totalDurationText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Route,
                        contentDescription = null,
                        tint = RdTextSecondary,
                        modifier = Modifier.size(if (isLiveMode) 15.dp else 13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
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
            }

            // 4b. En LIVE: linea adicional con desglose pickup/trayecto si esta disponible.
            if (isLiveMode) {
                val pickupSummary = evaluation.pickupSummaryText.takeIf { it.isNotBlank() && it != "N/A" }
                val tripSummary = evaluation.tripSummaryText.takeIf { it.isNotBlank() && it != "N/A" }
                if (pickupSummary != null || tripSummary != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val parts = mutableListOf<String>()
                    pickupSummary?.let { parts.add("Recogida: $it") }
                    tripSummary?.let { parts.add("Trayecto: $it") }
                    Text(
                        text = parts.joinToString(" · "),
                        color = RdTextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isLiveMode) 14.dp else 12.dp))

            // 5. Sub-box con RATIO DISTANCIA y RATIO TIEMPO (labels uppercase, valor Black).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RdSurfaceElevated, RoundedCornerShape(12.dp))
                    .padding(vertical = if (isLiveMode) 12.dp else 10.dp, horizontal = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RatioColumn(
                        label = "RATIO DISTANCIA",
                        value = evaluation.grossPerKmText,
                        unit = "€/km",
                        color = tierColor,
                        isLiveMode = isLiveMode,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(if (isLiveMode) 40.dp else 34.dp)
                            .background(RdBorderSubtle)
                    )
                    RatioColumn(
                        label = "RATIO TIEMPO",
                        value = evaluation.grossPerHourText,
                        unit = "€/h",
                        color = tierColor,
                        isLiveMode = isLiveMode,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 6. Linea de insight compuesta (MANUAL): contribucion al objetivo + ritmo vs objetivo.
            //    Utiliza los textos ya producidos por HudUiModelMapper.buildManualModel.
            if (evaluation.decisionMode == DecisionMode.MANUAL) {
                val insight = buildManualInsightLine(
                    goalContributionText = evaluation.goalContributionText,
                    goalPaceText = evaluation.goalPaceText
                )
                if (insight != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                            contentDescription = null,
                            tint = RdStatusAhead,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = insight,
                            color = RdTextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // 7. En LIVE: bloque de direcciones "Recogida / Destino" cuando esten disponibles.
            if (isLiveMode && (!pickupAddress.isNullOrBlank() || !dropoffAddress.isNullOrBlank())) {
                Spacer(modifier = Modifier.height(12.dp))
                AddressBlock(
                    pickupAddress = pickupAddress,
                    dropoffAddress = dropoffAddress
                )
            }

            // 8. Pie de override (solo AUTOMATIC + speOverridden).
            if (evaluation.decisionMode == DecisionMode.AUTOMATIC &&
                evaluation.isOverride &&
                evaluation.overrideText != null
            ) {
                Spacer(modifier = Modifier.height(10.dp))
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

@Composable
private fun RatioColumn(
    label: String,
    value: String,
    unit: String,
    color: Color,
    isLiveMode: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = RdTextTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Extract numeric portion "1,42" from "1,42 €/km" style strings; fall back to full string.
        val numericPart = value.substringBefore(' ').trim()
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (numericPart.isNotBlank()) numericPart else value,
                color = color,
                fontSize = if (isLiveMode) 22.sp else 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.3).sp
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = unit,
                color = RdTextTertiary,
                fontSize = if (isLiveMode) 11.sp else 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = if (isLiveMode) 3.dp else 2.dp)
            )
        }
    }
}

/**
 * R7.2: bloque "Recogida / Destino" solo en LiveScreen. Muestra las direcciones reales
 * expuestas por Trip.pickupAddress / Trip.dropoffAddress cuando el pipeline las capturo.
 */
@Composable
private fun AddressBlock(
    pickupAddress: String?,
    dropoffAddress: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(RdSurfaceElevated, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp, horizontal = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!pickupAddress.isNullOrBlank()) {
                AddressRow(
                    label = "RECOGIDA",
                    value = pickupAddress,
                    indicatorColor = RdBrandPrimaryLight,
                    circle = true
                )
            }
            if (!pickupAddress.isNullOrBlank() && !dropoffAddress.isNullOrBlank()) {
                // Divider vertical entre origen y destino (visual continuidad de ruta).
                Box(
                    modifier = Modifier
                        .padding(start = 5.dp)
                        .width(2.dp)
                        .height(12.dp)
                        .background(RdBorderSubtle)
                )
            }
            if (!dropoffAddress.isNullOrBlank()) {
                AddressRow(
                    label = "DESTINO",
                    value = dropoffAddress,
                    indicatorColor = RdTierGood,
                    circle = false
                )
            }
        }
    }
}

@Composable
private fun AddressRow(
    label: String,
    value: String,
    indicatorColor: Color,
    circle: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (circle) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(indicatorColor, CircleShape)
                        .border(2.dp, indicatorColor.copy(alpha = 0.4f), CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(indicatorColor, RoundedCornerShape(2.dp))
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                color = RdTextTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = value,
                color = RdTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * R7.2: compone la linea de insight de MANUAL a partir de los strings que ya produce el
 * mapper (goalContributionText = "X% del restante", goalPaceText = "Y% ritmo").
 * Devuelve null si no hay datos suficientes — la card no debe fabricar contenido.
 */
private fun buildManualInsightLine(
    goalContributionText: String?,
    goalPaceText: String?
): String? {
    val contribution = goalContributionText?.let { text ->
        // Format from mapper: "12.5% del restante"
        val pct = extractLeadingPercentage(text) ?: return@let null
        "Aporta ${formatPercentage(pct)} del restante diario"
    }
    val pace = goalPaceText?.let { text ->
        // Format from mapper: "138% ritmo"
        val pct = extractLeadingPercentage(text) ?: return@let null
        when {
            pct >= 100.0 -> "Supera tu ritmo en +${formatPercentage(pct - 100.0)}"
            pct > 0.0 -> "Va ${formatPercentage(100.0 - pct)} por debajo de tu ritmo"
            else -> null
        }
    }
    val parts = listOfNotNull(contribution, pace)
    if (parts.isEmpty()) return null
    return parts.joinToString(" · ")
}

private fun extractLeadingPercentage(text: String): Double? {
    // Accept "12.5% ..." / "12,5% ..." / "138%..."
    val match = Regex("""(-?\d+(?:[.,]\d+)?)\s*%""").find(text) ?: return null
    return match.groupValues[1].replace(',', '.').toDoubleOrNull()
}

private fun formatPercentage(pct: Double): String {
    // Show 0 decimals if integer, 1 decimal otherwise. Use Spanish comma.
    val rounded = Math.round(pct * 10.0) / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        "${rounded.toLong()}%"
    } else {
        String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f%%", rounded)
    }
}
