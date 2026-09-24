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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridedecider.app.domain.model.EarningsProgress
import com.ridedecider.app.domain.model.ProgressStatus
import com.ridedecider.app.ui.theme.RdBorderSubtle
import com.ridedecider.app.ui.theme.RdBrandPrimary
import com.ridedecider.app.ui.theme.RdBrandPrimaryLight
import com.ridedecider.app.ui.theme.RdStatusAhead
import com.ridedecider.app.ui.theme.RdStatusBehind
import com.ridedecider.app.ui.theme.RdStatusOnTrack
import com.ridedecider.app.ui.theme.RdStatusTargetReached
import com.ridedecider.app.ui.theme.RdSurface
import com.ridedecider.app.ui.theme.RdSurfaceElevated
import com.ridedecider.app.ui.theme.RdTextPrimary
import com.ridedecider.app.ui.theme.RdTextSecondary
import com.ridedecider.app.ui.theme.RdTextTertiary
import java.util.Locale

/**
 * R6.4: umbral visual de alerta para "Ritmo necesario". Cuando el ritmo requerido
 * para alcanzar el objetivo supera este valor, el texto se pinta en color de alerta.
 * Es un criterio visual fijo del producto — no procede de ProfitabilityConfig ni de
 * Goals — y no participa en ninguna decision economica del motor.
 */
private const val REQUIRED_RATE_WARNING_THRESHOLD_EUR_PER_HOUR = 35.0

@Composable
fun GoalProgressCard(
    title: String,
    progress: EarningsProgress?,
    modifier: Modifier = Modifier,
    isHero: Boolean = false
) {
    if (progress == null) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .border(1.dp, RdBorderSubtle, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = RdSurface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Cargando métricas de $title...", color = RdTextTertiary, fontSize = 13.sp)
            }
        }
        return
    }

    val (statusLabel, statusColor) = when (progress.status) {
        ProgressStatus.TARGET_REACHED -> "OBJETIVO ALCANZADO" to RdStatusTargetReached
        ProgressStatus.AHEAD -> "POR DELANTE" to RdStatusAhead
        ProgressStatus.ON_TRACK -> "EN RITMO" to RdStatusOnTrack
        ProgressStatus.BEHIND -> "POR DEBAJO DEL RITMO" to RdStatusBehind
    }

    val progressFraction = (progress.completionPercentage / 100.0).coerceIn(0.0, 1.0).toFloat()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isHero) 1.5.dp else 1.dp,
                color = if (isHero) RdBrandPrimary.copy(alpha = 0.5f) else RdBorderSubtle,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RdSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Cabecera: Título del Objetivo y Estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(Locale.ROOT),
                    color = RdTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                        .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Importe Ganado y Meta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Ganado",
                        color = RdTextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f €", progress.earnedEur),
                        color = if (progress.earnedEur > 0) RdStatusAhead else RdTextPrimary,
                        fontSize = if (isHero) 28.sp else 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Objetivo",
                        color = RdTextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f €", progress.targetEur),
                        color = RdTextSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Barra de Progreso y Porcentaje
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (progress.completionPercentage >= 100.0) RdBrandPrimary else RdStatusAhead,
                    trackColor = RdSurfaceElevated,
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {}
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", progress.completionPercentage)}% completado",
                        color = RdTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Restante: ${String.format(Locale.US, "%.2f €", progress.remainingEur)}",
                        color = if (progress.remainingEur <= 0.0) RdStatusAhead else RdTextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Desglose de Horas y Ritmos (€/h)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RdSurfaceElevated, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Horas
                    Column {
                        Text(
                            text = "Horas trabajadas",
                            color = RdTextTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val workedFormatted = formatHoursMinutes(progress.workedHours)
                        val plannedFormatted = "${progress.plannedHours.toInt()} h"
                        Text(
                            text = "$workedFormatted / $plannedFormatted",
                            color = RdTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Ritmo Actual
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Ritmo actual",
                            color = RdTextTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val currentRateText = if (progress.workedHours > 0.05) {
                            String.format(Locale.US, "%.2f €/h", progress.currentHourlyRate)
                        } else {
                            "-- €/h"
                        }
                        Text(
                            text = currentRateText,
                            color = RdTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Ritmo Necesario
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Ritmo necesario",
                            color = RdTextTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val requiredRateText = if (progress.remainingHours > 0.05 && progress.remainingEur > 0.0) {
                            String.format(Locale.US, "%.2f €/h", progress.requiredHourlyRate)
                        } else if (progress.remainingEur <= 0.0) {
                            "0.00 €/h"
                        } else {
                            "-- €/h"
                        }
                        Text(
                            text = requiredRateText,
                            color = if (progress.requiredHourlyRate > REQUIRED_RATE_WARNING_THRESHOLD_EUR_PER_HOUR) RdStatusBehind else RdBrandPrimaryLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun formatHoursMinutes(hoursDecimal: Double): String {
    val totalMinutes = (hoursDecimal * 60).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours} h ${minutes} min" else "${minutes} min"
}
