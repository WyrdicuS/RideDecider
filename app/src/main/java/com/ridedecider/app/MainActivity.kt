package com.ridedecider.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.sp
import com.ridedecider.app.data.accessibility.AccessibilityServiceStatus
import com.ridedecider.app.data.di.ServiceLocator
import com.ridedecider.app.ui.components.AppBottomNavigation
import com.ridedecider.app.ui.components.AppTab
import com.ridedecider.app.ui.screens.GoalsScreen
import com.ridedecider.app.ui.screens.HomeScreen
import com.ridedecider.app.ui.screens.LiveScreen
import com.ridedecider.app.ui.screens.SettingsScreen
import com.ridedecider.app.ui.theme.RdBackground
import com.ridedecider.app.ui.theme.RideDeciderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var hasOverlayPermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updatePermissionsStatus()

        setContent {
            RideDeciderTheme {
                val context = LocalContext.current
                val earningsTracker = remember { ServiceLocator.getEarningsTracker(context) }
                val serviceTracker = remember { ServiceLocator.getAccessibilityServiceStateTracker(context) }
                val accessibilityStatus by serviceTracker.status.collectAsState()
                var currentTab by remember { mutableStateOf(AppTab.HOME) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = RdBackground,
                    bottomBar = {
                        AppBottomNavigation(
                            selectedTab = currentTab,
                            onTabSelected = { currentTab = it }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentTab) {
                            AppTab.HOME -> {
                                HomeScreen(
                                    accessibilityStatus = accessibilityStatus,
                                    earningsTracker = earningsTracker
                                )
                            }
                            AppTab.LIVE -> {
                                LiveScreen(
                                    accessibilityStatus = accessibilityStatus,
                                    hasOverlayPermission = hasOverlayPermission,
                                    earningsTracker = earningsTracker
                                )
                            }
                            AppTab.GOALS -> {
                                GoalsScreen(
                                    earningsTracker = earningsTracker
                                )
                            }
                            AppTab.SETTINGS -> {
                                SettingsScreen(
                                    accessibilityStatus = accessibilityStatus,
                                    hasOverlayPermission = hasOverlayPermission,
                                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                                    onOpenOverlaySettings = { requestOverlayPermission() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsStatus()
    }

    private fun updatePermissionsStatus() {
        hasOverlayPermission = Settings.canDrawOverlays(this)
        ServiceLocator.getAccessibilityServiceStateTracker(this).reconcile(this)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
}

@Composable
fun TripLifecycleSimulatorCard(
    hasOverlayPermission: Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val earningsTracker = remember { ServiceLocator.getEarningsTracker(context) }
    val configProvider = remember { ServiceLocator.getProfitabilityConfigProvider(context) }

    var currentStateName by remember { mutableStateOf(earningsTracker.stateMachine.currentState::class.simpleName ?: "Idle") }
    var currentTripId by remember { mutableStateOf(earningsTracker.currentTripId ?: "Ninguno") }
    var lastFeedbackMessage by remember { mutableStateOf("Listo para simular") }
    var dailyProgress by remember { mutableStateOf<com.ridedecider.app.domain.model.EarningsProgress?>(null) }

    fun refreshState() {
        currentStateName = earningsTracker.stateMachine.currentState::class.simpleName ?: "Idle"
        currentTripId = earningsTracker.currentTripId ?: "Ninguno"
        coroutineScope.launch {
            dailyProgress = earningsTracker.getDailyProgress()
        }
    }

    LaunchedEffect(Unit) {
        refreshState()
    }

    val stateBadgeColor = when (currentStateName) {
        "Idle" -> Color(0xFF78909C)
        "PendingAcceptance", "OfferDetected" -> Color(0xFF29B6F6)
        "Assigned" -> Color(0xFF26C6DA)
        "ActiveTrip" -> Color(0xFFAB47BC)
        "Completed" -> Color(0xFF66BB6A)
        "Cancelled" -> Color(0xFFEF5350)
        "IgnoredOrExpired" -> Color(0xFFFFA726)
        else -> Color.White
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Simulador del Ciclo de Vida",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Text(
                    text = currentStateName,
                    color = stateBadgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(stateBadgeColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Text(
                text = "Último evento: $lastFeedbackMessage",
                color = Color(0xFF90A4AE),
                fontSize = 12.sp
            )

            // Fila 1: Detección de Ofertas
            Text(text = "1. Detección de Ofertas:", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val trip = com.ridedecider.app.domain.model.Trip(
                            id = "trip_dir_${System.currentTimeMillis()}",
                            timestamp = System.currentTimeMillis(),
                            offerType = com.ridedecider.app.domain.model.TripOfferType.TRIP_OFFER,
                            category = com.ridedecider.app.domain.model.UberCategory.UBER_X,
                            rawFare = 8.50,
                            currency = "€",
                            pickupDistanceKm = 1.2,
                            pickupDurationMinutes = 3.0,
                            pickupAddress = "Calle Gran Vía 12",
                            tripDistanceKm = 4.8,
                            tripDurationMinutes = 12.0,
                            dropoffAddress = "Aeropuerto T4",
                            isCashPayment = false,
                            passengerRating = 4.95
                        )
                        val eval = com.ridedecider.app.domain.usecase.EvaluateIncomingTripUseCase(
                            com.ridedecider.app.domain.engine.DecisionEngine()
                        ).invoke(trip, configProvider.getConfig())
                        coroutineScope.launch {
                            earningsTracker.recordEvaluatedOffer(trip, eval)
                            com.ridedecider.app.ui.overlay.state.HudStateHolder.emitEvaluation(eval)
                            lastFeedbackMessage = "Oferta directa detectada (8.50 €)"
                            refreshState()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "Directa (8.50€)", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                Button(
                    onClick = {
                        val trip = com.ridedecider.app.domain.model.Trip(
                            id = "trip_rad_${System.currentTimeMillis()}",
                            timestamp = System.currentTimeMillis(),
                            offerType = com.ridedecider.app.domain.model.TripOfferType.RADAR_OFFER,
                            category = com.ridedecider.app.domain.model.UberCategory.UBER_X,
                            rawFare = 4.10,
                            currency = "€",
                            pickupDistanceKm = 3.8,
                            pickupDurationMinutes = 8.0,
                            pickupAddress = "Plaza Mayor",
                            tripDistanceKm = 2.5,
                            tripDurationMinutes = 10.0,
                            dropoffAddress = "Paseo de la Castellana",
                            isCashPayment = true,
                            passengerRating = 4.70
                        )
                        val eval = com.ridedecider.app.domain.usecase.EvaluateIncomingTripUseCase(
                            com.ridedecider.app.domain.engine.DecisionEngine()
                        ).invoke(trip, configProvider.getConfig())
                        coroutineScope.launch {
                            earningsTracker.recordEvaluatedOffer(trip, eval)
                            com.ridedecider.app.ui.overlay.state.HudStateHolder.emitEvaluation(eval)
                            lastFeedbackMessage = "Oferta Radar detectada (4.10 €)"
                            refreshState()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "Radar (4.10€)", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            // Fila 2: Aceptación y Asignación
            Text(text = "2. Aceptación / Asignación:", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            earningsTracker.currentTripId?.let { id ->
                                earningsTracker.markTripAccepted(id)
                                com.ridedecider.app.ui.overlay.state.HudStateHolder.hideImmediately()
                                lastFeedbackMessage = "Aceptación manual directa confirmada"
                                refreshState()
                            } ?: run { lastFeedbackMessage = "No hay oferta pendiente" }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "Aceptar Dir.", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            earningsTracker.currentTripId?.let { id ->
                                earningsTracker.markTripAccepted(id)
                                com.ridedecider.app.ui.overlay.state.HudStateHolder.hideImmediately()
                                lastFeedbackMessage = "Match Radar manual confirmado"
                                refreshState()
                            } ?: run { lastFeedbackMessage = "No hay oferta pendiente" }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0097A7)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "Match Radar", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            earningsTracker.currentTripId?.let { id ->
                                earningsTracker.markRadarAutoAssigned(id)
                                com.ridedecider.app.ui.overlay.state.HudStateHolder.hideImmediately()
                                lastFeedbackMessage = "Radar auto-asignado por Uber"
                                refreshState()
                            } ?: run { lastFeedbackMessage = "No hay oferta pendiente" }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "Auto Radar", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }

            // Fila 3: Viaje Activo
            Text(text = "3. Inicio de Navegación:", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Button(
                onClick = {
                    coroutineScope.launch {
                        earningsTracker.markActiveTripStarted()
                        com.ridedecider.app.ui.overlay.state.HudStateHolder.hideImmediately()
                        lastFeedbackMessage = "Navegación activa (ACTIVE_TRIP) iniciada"
                        refreshState()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAB47BC)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Iniciar ACTIVE_TRIP (Navegación en ruta)", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            // Fila 4: Cancelaciones
            Text(text = "4. Cancelaciones:", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            earningsTracker.currentTripId?.let { id ->
                                earningsTracker.cancelTrip(id, com.ridedecider.app.domain.model.CancellationReason.RIDER, cancellationFee = 4.50)
                                lastFeedbackMessage = "Cancelado por Pasajero (+4.50 € comp.)"
                                refreshState()
                            } ?: run { lastFeedbackMessage = "No hay viaje para cancelar" }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "Pasajero (+4.5€)", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            earningsTracker.currentTripId?.let { id ->
                                earningsTracker.cancelTrip(id, com.ridedecider.app.domain.model.CancellationReason.RIDER, cancellationFee = null)
                                lastFeedbackMessage = "Cancelado por Pasajero (0 € comp.)"
                                refreshState()
                            } ?: run { lastFeedbackMessage = "No hay viaje para cancelar" }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "Pasajero (0€)", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            earningsTracker.currentTripId?.let { id ->
                                earningsTracker.cancelTrip(id, com.ridedecider.app.domain.model.CancellationReason.NO_SHOW, cancellationFee = 5.00)
                                lastFeedbackMessage = "No-Show pasajero (+5.00 € comp.)"
                                refreshState()
                            } ?: run { lastFeedbackMessage = "No hay viaje para cancelar" }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2185B)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "No-Show (+5€)", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            earningsTracker.currentTripId?.let { id ->
                                earningsTracker.cancelTrip(id, com.ridedecider.app.domain.model.CancellationReason.DRIVER, cancellationFee = null)
                                lastFeedbackMessage = "Cancelado por Conductor (0 €)"
                                refreshState()
                            } ?: run { lastFeedbackMessage = "No hay viaje para cancelar" }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5D4037)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "Conductor (0€)", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            earningsTracker.currentTripId?.let { id ->
                                earningsTracker.cancelTrip(id, com.ridedecider.app.domain.model.CancellationReason.UBER, cancellationFee = 3.00)
                                lastFeedbackMessage = "Cancelado por Uber (+3.00 €)"
                                refreshState()
                            } ?: run { lastFeedbackMessage = "No hay viaje para cancelar" }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "Uber (+3€)", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            earningsTracker.currentTripId?.let { id ->
                                earningsTracker.cancelTrip(id, com.ridedecider.app.domain.model.CancellationReason.UNKNOWN, cancellationFee = null)
                                lastFeedbackMessage = "Cancelado Motivo Desconocido (0 €)"
                                refreshState()
                            } ?: run { lastFeedbackMessage = "No hay viaje para cancelar" }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "Desconocido", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }

            // Fila 5: Finalización Real del Viaje
            Text(text = "5. Finalización del Viaje:", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            earningsTracker.currentTripId?.let { id ->
                                val active = (earningsTracker.stateMachine.currentState as? com.ridedecider.app.domain.model.TripLifecycleState.ActiveTrip)?.trip
                                val fare = active?.rawFare ?: 8.50
                                val dur = active?.tripDurationMinutes ?: 12.0
                                earningsTracker.completeTrip(id, finalEarnings = fare, durationMinutes = dur)
                                lastFeedbackMessage = "Viaje COMPLETED con tarifa estimada ($fare €)"
                                refreshState()
                            } ?: run { lastFeedbackMessage = "No hay viaje activo para completar" }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "Completar Estimada", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            earningsTracker.currentTripId?.let { id ->
                                earningsTracker.completeTrip(id, finalEarnings = 11.20, durationMinutes = 15.0)
                                lastFeedbackMessage = "Viaje COMPLETED con tarifa final recalculada (11.20 €)"
                                refreshState()
                            } ?: run { lastFeedbackMessage = "No hay viaje activo para completar" }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "Completar + Propina", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            // Fila 6: Invariantes y Reset
            Text(text = "6. Invariantes & Idempotencia:", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val stateBefore = earningsTracker.stateMachine.currentState
                            val attempt = earningsTracker.stateMachine.onTripCompleted(finalFareEur = 20.0)
                            if (stateBefore is com.ridedecider.app.domain.model.TripLifecycleState.Cancelled && attempt is com.ridedecider.app.domain.model.TripLifecycleState.Cancelled) {
                                lastFeedbackMessage = "Invariante protegida: CANCELLED no se convirtió en COMPLETED"
                            } else {
                                lastFeedbackMessage = "Transición evaluada: ${attempt::class.simpleName}"
                            }
                            refreshState()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "Test CANCELLED→COMPL.", fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }

                Button(
                    onClick = {
                        earningsTracker.stateMachine.reset()
                        com.ridedecider.app.ui.overlay.state.HudStateHolder.hideImmediately()
                        lastFeedbackMessage = "Máquina de estados reseteada a IDLE"
                        refreshState()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "Reset a IDLE", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}