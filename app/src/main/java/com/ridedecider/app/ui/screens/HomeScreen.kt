package com.ridedecider.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridedecider.app.R
import com.ridedecider.app.data.di.ServiceLocator
import com.ridedecider.app.domain.engine.EarningsTracker
import com.ridedecider.app.domain.model.EarningsProgress
import com.ridedecider.app.domain.model.RecordedTrip
import com.ridedecider.app.domain.model.TripTrackingStatus
import com.ridedecider.app.ui.components.GoalProgressCard
import com.ridedecider.app.ui.components.MetricCard
import com.ridedecider.app.ui.components.RecommendationHeroCard
import com.ridedecider.app.ui.components.StatusIndicator
import com.ridedecider.app.ui.overlay.model.HudUiModel
import com.ridedecider.app.ui.theme.RdBgCanvas
import com.ridedecider.app.ui.theme.RdBorderSubtle
import com.ridedecider.app.ui.theme.RdBrandPrimary
import com.ridedecider.app.ui.theme.RdStatusAhead
import com.ridedecider.app.ui.theme.RdStatusBehind
import com.ridedecider.app.ui.theme.RdSurface
import com.ridedecider.app.ui.theme.RdSurfaceCard
import com.ridedecider.app.ui.theme.RdSurfaceInteractive
import com.ridedecider.app.ui.theme.RdTextPrimary
import com.ridedecider.app.ui.theme.RdTextSecondary
import com.ridedecider.app.ui.theme.RdTextTertiary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HistoryPeriod(val label: String) {
    TODAY("Hoy"),
    LAST_7_DAYS("7 Días"),
    ALL("Histórico")
}

@Composable
fun HomeScreen(
    isAccessibilityEnabled: Boolean,
    earningsTracker: EarningsTracker,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    val earningsRepository = remember { ServiceLocator.getEarningsRepository(context) }
    val hudState by com.ridedecider.app.ui.overlay.state.HudStateHolder.state.collectAsState()

    var selectedPeriod by remember { mutableStateOf(HistoryPeriod.TODAY) }
    var dailyProgress by remember { mutableStateOf<EarningsProgress?>(null) }
    var displayedTrips by remember { mutableStateOf<List<RecordedTrip>>(emptyList()) }
    var lastEvaluationUiModel by remember { mutableStateOf<HudUiModel?>(null) }

    LaunchedEffect(hudState) {
        if (hudState is com.ridedecider.app.ui.overlay.model.HudState.Visible) {
            lastEvaluationUiModel = (hudState as com.ridedecider.app.ui.overlay.model.HudState.Visible).data
        }
    }

    fun refreshData() {
        coroutineScope.launch {
            dailyProgress = earningsTracker.getDailyProgress()
            val now = System.currentTimeMillis()
            val (startTimestamp, endTimestamp) = when (selectedPeriod) {
                HistoryPeriod.TODAY -> {
                    val calendar = java.util.Calendar.getInstance()
                    calendar.timeInMillis = now
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    calendar.set(java.util.Calendar.MINUTE, 0)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                    calendar.timeInMillis to (now + 86400000L)
                }
                HistoryPeriod.LAST_7_DAYS -> {
                    (now - (7 * 24 * 3600 * 1000L)) to (now + 86400000L)
                }
                HistoryPeriod.ALL -> {
                    0L to (now + 86400000L)
                }
            }
            displayedTrips = earningsRepository.getTripsBetween(startTimestamp, endTimestamp)
        }
    }

    LaunchedEffect(selectedPeriod, hudState) {
        refreshData()
    }

    val completedTripsCount = displayedTrips.count { it.status == TripTrackingStatus.COMPLETED }
    val cancelledTrips = displayedTrips.filter {
        it.status == TripTrackingStatus.CANCELLED_BY_RIDER ||
        it.status == TripTrackingStatus.CANCELLED_BY_DRIVER ||
        it.status == TripTrackingStatus.CANCELLED_BY_UBER ||
        it.status == TripTrackingStatus.CANCELLED_NO_SHOW ||
        it.status == TripTrackingStatus.CANCELLED_UNKNOWN
    }
    val cancellationFeesTotal = cancelledTrips.mapNotNull { it.cancellationFeeEur }.sum()
    val completedEarnings = displayedTrips.filter { it.status == TripTrackingStatus.COMPLETED }
        .mapNotNull { it.finalEarningsEur ?: it.trip.rawFare }
        .sum() + cancellationFeesTotal

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RdBgCanvas)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Cabecera Principal: Logo Real + Branding + Estado de Conexión de Uber
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Logo de RideDecider
                Image(
                    painter = painterResource(id = R.drawable.ic_ridedecider_logo),
                    contentDescription = "RideDecider Logo",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, RdBorderSubtle, RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "RideDecider",
                        color = RdTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = if (isAccessibilityEnabled) "Uber conectado" else "Servicio inactivo",
                        color = if (isAccessibilityEnabled) RdStatusAhead else RdTextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            StatusIndicator(
                label = if (isAccessibilityEnabled) "ACTIVO" else "INACTIVO",
                isActive = isAccessibilityEnabled
            )
        }

        // 2. Tarjeta Principal: Objetivo de Hoy (Métricas Reales)
        GoalProgressCard(
            title = "Objetivo de Hoy",
            progress = dailyProgress,
            isHero = true
        )

        // 3. Sección: Última Recomendación
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "ÚLTIMA RECOMENDACIÓN",
                color = RdTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            RecommendationHeroCard(
                evaluation = lastEvaluationUiModel,
                isLiveMode = false
            )
        }

        // 4. Selector de Período de Rendimiento (Hoy / 7 Días / Histórico)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RdSurfaceInteractive, RoundedCornerShape(12.dp))
                .border(1.dp, RdBorderSubtle, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HistoryPeriod.values().forEach { period ->
                val isSelected = selectedPeriod == period
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) RdBrandPrimary else Color.Transparent)
                        .clickable { selectedPeriod = period }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = period.label,
                        color = if (isSelected) Color.White else RdTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // 5. Sección: Rendimiento según período seleccionado
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "RENDIMIENTO · ${selectedPeriod.label.uppercase()}",
                color = RdTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )

            // Fila 1: Ritmo €/h y Ganancia Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val currentRate = dailyProgress?.currentHourlyRate ?: 0.0
                val workedHours = dailyProgress?.workedHours ?: 0.0
                val hourlyRateFormatted = if (workedHours > 0.05) {
                    String.format(Locale.US, "%.2f", currentRate)
                } else {
                    "--"
                }

                MetricCard(
                    label = "Ritmo Económico",
                    value = hourlyRateFormatted,
                    unit = "€/h",
                    modifier = Modifier.weight(1f),
                    valueColor = if (currentRate >= 25.0) RdBrandPrimary else RdTextPrimary,
                    subValue = if (workedHours > 0.05) "En tiempo activo" else "Sin actividad"
                )

                val earnedFormatted = String.format(Locale.US, "%.2f", completedEarnings)
                MetricCard(
                    label = "Ganado (${selectedPeriod.label})",
                    value = earnedFormatted,
                    unit = "€",
                    modifier = Modifier.weight(1f),
                    valueColor = RdStatusAhead,
                    subValue = "$completedTripsCount completados"
                )
            }

            // Fila 2: Kilómetros Totales y Cancelaciones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val totalKm = displayedTrips.filter { it.status == TripTrackingStatus.COMPLETED }
                    .mapNotNull { it.trip.tripDistanceKm }
                    .sum()
                val kmFormatted = String.format(Locale.US, "%.1f", totalKm)

                MetricCard(
                    label = "Kilómetros en Ruta",
                    value = kmFormatted,
                    unit = "km",
                    modifier = Modifier.weight(1f),
                    subValue = "Distancia efectiva"
                )

                MetricCard(
                    label = "Cancelaciones",
                    value = "${cancelledTrips.size}",
                    unit = "viajes",
                    modifier = Modifier.weight(1f),
                    valueColor = if (cancelledTrips.isNotEmpty()) RdStatusBehind else RdTextPrimary,
                    subValue = if (cancellationFeesTotal > 0.0) "+${String.format(Locale.US, "%.2f €", cancellationFeesTotal)} comp." else "0.00 € comp."
                )
            }
        }

        // 6. Sección: Historial de Viajes Registrados
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "REGISTRO DE VIAJES (${displayedTrips.size})",
                color = RdTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )

            if (displayedTrips.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, RdBorderSubtle, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = RdSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = RdTextTertiary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Sin viajes registrados en este período",
                            color = RdTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    displayedTrips.take(10).forEach { tripRecord ->
                        val trip = tripRecord.trip
                        val isCompleted = tripRecord.status == TripTrackingStatus.COMPLETED
                        val isCancelled = tripRecord.status.name.startsWith("CANCELLED")
                        val timeStr = timeFormat.format(Date(tripRecord.recordedTimestamp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, RdBorderSubtle, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = RdSurface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(
                                                when {
                                                    isCompleted -> RdStatusAhead.copy(alpha = 0.15f)
                                                    isCancelled -> RdStatusBehind.copy(alpha = 0.15f)
                                                    else -> RdBrandPrimary.copy(alpha = 0.15f)
                                                },
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                isCompleted -> Icons.Rounded.CheckCircle
                                                isCancelled -> Icons.Rounded.Cancel
                                                else -> Icons.Rounded.DirectionsCar
                                            },
                                            contentDescription = null,
                                            tint = when {
                                                isCompleted -> RdStatusAhead
                                                isCancelled -> RdStatusBehind
                                                else -> RdBrandPrimary
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = trip.category.name.replace("_", " "),
                                            color = RdTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "$timeStr · ${trip.tripDistanceKm ?: 0.0} km · ${trip.tripDurationMinutes?.toInt() ?: 0} min",
                                            color = RdTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    val fare = tripRecord.finalEarningsEur ?: trip.rawFare ?: 0.0
                                    Text(
                                        text = String.format(Locale.US, "%.2f €", fare),
                                        color = if (isCompleted) RdStatusAhead else RdTextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = tripRecord.status.name,
                                        color = when {
                                            isCompleted -> RdStatusAhead
                                            isCancelled -> RdStatusBehind
                                            else -> RdTextTertiary
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Espacio para evitar superposición con la barra de navegación inferior
        Spacer(modifier = Modifier.height(20.dp))
    }
}
