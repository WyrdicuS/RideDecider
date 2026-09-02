package com.ridedecider.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridedecider.app.domain.engine.EarningsTracker
import com.ridedecider.app.domain.model.TripLifecycleState
import com.ridedecider.app.ui.components.RecommendationHeroCard
import com.ridedecider.app.ui.components.StatusIndicator
import com.ridedecider.app.ui.overlay.mapper.HudUiModelMapper
import com.ridedecider.app.ui.overlay.model.HudUiModel
import com.ridedecider.app.ui.theme.RdBgCanvas
import com.ridedecider.app.ui.theme.RdBorderSubtle
import com.ridedecider.app.ui.theme.RdBrandPrimary
import com.ridedecider.app.ui.theme.RdStatusAhead
import com.ridedecider.app.ui.theme.RdStatusBehind
import com.ridedecider.app.ui.theme.RdSurface
import com.ridedecider.app.ui.theme.RdSurfaceCard
import com.ridedecider.app.ui.theme.RdTextPrimary
import com.ridedecider.app.ui.theme.RdTextSecondary
import com.ridedecider.app.data.accessibility.AccessibilityServiceStatus
import com.ridedecider.app.ui.theme.RdStatusWarning
import com.ridedecider.app.ui.theme.RdTextTertiary
import com.ridedecider.app.ui.theme.RdTripStateActive
import com.ridedecider.app.ui.theme.RdTripStateAssigned
import com.ridedecider.app.ui.theme.RdTripStateCancelled
import com.ridedecider.app.ui.theme.RdTripStateCompleted
import com.ridedecider.app.ui.theme.RdTripStateExpired
import com.ridedecider.app.ui.theme.RdTripStateIdle
import com.ridedecider.app.ui.theme.RdTripStateOffer
import kotlinx.coroutines.launch

@Composable
fun LiveScreen(
    accessibilityStatus: AccessibilityServiceStatus = AccessibilityServiceStatus.DISABLED,
    isAccessibilityEnabled: Boolean = accessibilityStatus.isOperative,
    hasOverlayPermission: Boolean,
    earningsTracker: EarningsTracker,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val hudState by com.ridedecider.app.ui.overlay.state.HudStateHolder.state.collectAsState()
    var activeEvaluationModel by remember { mutableStateOf<HudUiModel?>(null) }
    var currentLifecycleState by remember { mutableStateOf(earningsTracker.stateMachine.currentState) }

    LaunchedEffect(hudState) {
        if (hudState is com.ridedecider.app.ui.overlay.model.HudState.Visible) {
            activeEvaluationModel = (hudState as com.ridedecider.app.ui.overlay.model.HudState.Visible).data
        }
        currentLifecycleState = earningsTracker.stateMachine.currentState
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RdBgCanvas)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Cabecera de Estado en Tiempo Real
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "En Vivo",
                    color = RdTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.3).sp
                )
                val statusText: String
                val statusColor: Color
                when (accessibilityStatus) {
                    AccessibilityServiceStatus.CONNECTED -> {
                        statusText = "RideDecider está activo · Escuchando Uber"
                        statusColor = RdStatusAhead
                    }
                    AccessibilityServiceStatus.INTERRUPTED -> {
                        statusText = "Servicio pausado por el sistema"
                        statusColor = RdStatusWarning
                    }
                    AccessibilityServiceStatus.ENABLED_DISCONNECTED -> {
                        statusText = "Servicio habilitado · Esperando vinculación"
                        statusColor = RdStatusWarning
                    }
                    AccessibilityServiceStatus.DISABLED -> {
                        statusText = if (isAccessibilityEnabled) "RideDecider está activo · Escuchando Uber" else "Servicio de Accesibilidad inactivo"
                        statusColor = if (isAccessibilityEnabled) RdStatusAhead else RdStatusBehind
                    }
                }
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusIndicator(
                    label = if (hasOverlayPermission) "HUD ACTIVO" else "SIN HUD",
                    isActive = hasOverlayPermission
                )
            }
        }

        // 2. Estado Actual de la Máquina de Estados
        val (stateLabel, stateColor) = when (currentLifecycleState) {
            is TripLifecycleState.Idle -> "IDLE (En Espera)" to RdTripStateIdle
            is TripLifecycleState.OfferDetected, is TripLifecycleState.PendingAcceptance -> "OFERTA DETECTADA" to RdTripStateOffer
            is TripLifecycleState.Assigned -> "VIAJE ASIGNADO" to RdTripStateAssigned
            is TripLifecycleState.ActiveTrip -> "EN RUTA" to RdTripStateActive
            is TripLifecycleState.Completed -> "VIAJE COMPLETADO" to RdTripStateCompleted
            is TripLifecycleState.Cancelled -> "VIAJE CANCELADO" to RdTripStateCancelled
            is TripLifecycleState.IgnoredOrExpired -> "OFERTA EXPIRADA" to RdTripStateExpired
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RdBorderSubtle, RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = RdSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ESTADO DEL VIAJE",
                    color = RdTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                Box(
                    modifier = Modifier
                        .background(stateColor.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                        .border(1.dp, stateColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = stateLabel,
                        color = stateColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // 3. Tarjeta Hero de la Oferta Activa / En Vivo
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "OFERTA EN CURSO",
                color = RdTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )

            RecommendationHeroCard(
                evaluation = activeEvaluationModel,
                isLiveMode = true
            )
        }

        // Espacio de seguridad inferior para asegurar scroll fluido y visibilidad completa sobre la barra de navegación
        Spacer(modifier = Modifier.height(32.dp))
    }
}
