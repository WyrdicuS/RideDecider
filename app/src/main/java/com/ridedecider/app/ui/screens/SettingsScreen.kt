package com.ridedecider.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridedecider.app.data.di.ServiceLocator
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.ui.components.AccessibilityDisclosureDialog
import com.ridedecider.app.ui.components.StatusIndicator
import com.ridedecider.app.ui.overlay.state.HudStateHolder
import com.ridedecider.app.ui.theme.RdBgBase
import com.ridedecider.app.ui.theme.RdBgCanvas
import com.ridedecider.app.ui.theme.RdBorderSubtle
import com.ridedecider.app.ui.theme.RdBrandPrimary
import com.ridedecider.app.ui.theme.RdBrandPrimaryLight
import com.ridedecider.app.ui.theme.RdStatusAhead
import com.ridedecider.app.ui.theme.RdStatusBehind
import com.ridedecider.app.ui.theme.RdSurface
import com.ridedecider.app.ui.theme.RdSurfaceElevated
import com.ridedecider.app.ui.theme.RdTextPrimary
import com.ridedecider.app.data.accessibility.AccessibilityServiceStatus
import com.ridedecider.app.ui.theme.RdTextSecondary
import com.ridedecider.app.ui.theme.RdTextTertiary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    accessibilityStatus: AccessibilityServiceStatus = AccessibilityServiceStatus.DISABLED,
    isAccessibilityEnabled: Boolean = accessibilityStatus.isOperative,
    hasOverlayPermission: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onConfirm = {
                showAccessibilityDisclosure = false
                onOpenAccessibilitySettings()
            },
            onDismiss = {
                showAccessibilityDisclosure = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RdBgCanvas)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Cabecera
        Text(
            text = "Ajustes del Sistema",
            color = RdTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.3).sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        // 2. Sección: Estado de Servicios y Permisos
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RdBorderSubtle, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = RdSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "SERVICIOS Y PERMISOS",
                    color = RdTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                // Accesibilidad
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Servicio de Accesibilidad", color = RdTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Lectura no invasiva de Uber Driver", color = RdTextTertiary, fontSize = 11.sp)
                    }

                    val (statusLabel, isStatusActive, isStatusWarning) = when (accessibilityStatus) {
                        AccessibilityServiceStatus.CONNECTED -> Triple("ACTIVO", true, false)
                        AccessibilityServiceStatus.INTERRUPTED -> Triple("PAUSADO", true, true)
                        AccessibilityServiceStatus.ENABLED_DISCONNECTED -> Triple("PENDIENTE", false, true)
                        AccessibilityServiceStatus.DISABLED -> Triple(if (isAccessibilityEnabled) "ACTIVO" else "INACTIVO", isAccessibilityEnabled, false)
                    }

                    StatusIndicator(
                        label = statusLabel,
                        isActive = isStatusActive,
                        isWarning = isStatusWarning
                    )
                }

                if (!isAccessibilityEnabled) {
                    Button(
                        onClick = { showAccessibilityDisclosure = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = RdBrandPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "Activar Accesibilidad", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RdBorderSubtle))

                // Overlay
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Ventana Flotante (HUD)", color = RdTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(text = "HUD click-through sobre Uber Driver", color = RdTextTertiary, fontSize = 11.sp)
                    }
                    StatusIndicator(
                        label = if (hasOverlayPermission) "CONCEDIDO" else "PENDIENTE",
                        isActive = hasOverlayPermission
                    )
                }

                if (!hasOverlayPermission) {
                    Button(
                        onClick = onOpenOverlaySettings,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = RdBrandPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "Conceder Permiso de Superposición", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2b. Sección: Modo de Decisión (Manual / Automático) — R5
        val decisionModeRepo = remember { ServiceLocator.getDecisionModeRepository(context) }
        val currentDecisionMode by decisionModeRepo.modeFlow.collectAsState()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RdBorderSubtle, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = RdSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "MODO DE DECISIÓN",
                    color = RdTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                Text(
                    text = "Manual: analiza cada oferta respecto a tu objetivo personal. Automático: evalúa la oportunidad económica de la oferta en sí, sin usar tus objetivos.",
                    color = RdTextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(
                        com.ridedecider.app.domain.model.DecisionMode.MANUAL to "MANUAL",
                        com.ridedecider.app.domain.model.DecisionMode.AUTOMATIC to "AUTOMÁTICO"
                    ).forEach { (mode, label) ->
                        val isSelected = currentDecisionMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) RdBrandPrimary.copy(alpha = 0.18f) else RdSurfaceElevated,
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) RdBrandPrimary else RdBorderSubtle,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { decisionModeRepo.setMode(mode) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) RdBrandPrimaryLight else RdTextTertiary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. Sección: Motor de Rentabilidad Inteligente (evaluación económica intrínseca)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RdBorderSubtle, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = RdSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MOTOR DE RENTABILIDAD INTELIGENTE",
                        color = RdTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )

                    Box(
                        modifier = Modifier
                            .background(RdBrandPrimary.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                            .border(1.dp, RdBrandPrimary.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "EVALUACIÓN ECONÓMICA",
                            color = RdBrandPrimaryLight,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Text(
                    text = "El motor evalúa cada oferta según sus características económicas y los criterios configurados del motor. Esta evaluación es independiente de tus objetivos personales.",
                    color = RdTextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // 4. Sección: Gestión de Datos e Historial
        var resetSuccessMessage by remember { mutableStateOf(false) }
        LaunchedEffect(resetSuccessMessage) {
            if (resetSuccessMessage) {
                kotlinx.coroutines.delay(2500L)
                resetSuccessMessage = false
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RdBorderSubtle, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = RdSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "DATOS E HISTORIAL",
                    color = RdTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                Text(
                    text = "Permite restablecer a cero el registro de ofertas y viajes acumulados.",
                    color = RdTextTertiary,
                    fontSize = 12.sp
                )

                val earningsTracker = remember { ServiceLocator.getEarningsTracker(context) }
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            earningsTracker.clearAllTrips()
                            resetSuccessMessage = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, if (resetSuccessMessage) RdStatusAhead else RdStatusBehind.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (resetSuccessMessage) "Historial Restablecido a Cero" else "Borrar Historial de Viajes y Ganancias",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (resetSuccessMessage) RdStatusAhead else RdStatusBehind
                    )
                }
            }
        }

        // 5. Sección: Actualizaciones del Sistema
        val appUpdateManager = remember { ServiceLocator.getAppUpdateManager(context) }
        val updateState by appUpdateManager.updateState.collectAsState()
        var showPermissionDialog by remember { mutableStateOf(false) }

        if (updateState is com.ridedecider.app.domain.model.update.AppUpdateState.Available ||
            updateState is com.ridedecider.app.domain.model.update.AppUpdateState.Downloading ||
            updateState is com.ridedecider.app.domain.model.update.AppUpdateState.Downloaded ||
            updateState is com.ridedecider.app.domain.model.update.AppUpdateState.Installing ||
            updateState is com.ridedecider.app.domain.model.update.AppUpdateState.Error
        ) {
            com.ridedecider.app.ui.components.UpdateAvailableDialog(
                state = updateState,
                onStartDownload = { appUpdateManager.startDownload() },
                onCancelDownload = { appUpdateManager.cancelDownload() },
                onInstallApk = { actContext ->
                    if (appUpdateManager.canRequestPackageInstalls(actContext)) {
                        appUpdateManager.installApk(actContext)
                    } else {
                        showPermissionDialog = true
                    }
                },
                onRequestPermission = { actContext ->
                    actContext.startActivity(appUpdateManager.getManageUnknownAppSourcesIntent(actContext))
                },
                onRetry = { appUpdateManager.checkForUpdates() },
                onDismiss = { appUpdateManager.resetState() }
            )
        }

        if (showPermissionDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                containerColor = RdSurface,
                title = {
                    Text(
                        text = "Permiso de Instalación Necesario",
                        color = RdTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Android requiere que autorices a RideDecider para instalar actualizaciones descargadas fuera de Google Play Store.",
                        color = RdTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPermissionDialog = false
                            context.startActivity(appUpdateManager.getManageUnknownAppSourcesIntent(context))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RdBrandPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Permitir Instalación", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text("Cancelar", color = RdTextTertiary, fontSize = 12.sp)
                    }
                }
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RdBorderSubtle, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = RdSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTUALIZACIONES DEL SISTEMA",
                        color = RdTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )

                    Box(
                        modifier = Modifier
                            .background(RdSurfaceElevated, RoundedCornerShape(6.dp))
                            .border(1.dp, RdBorderSubtle, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "v${com.ridedecider.app.BuildConfig.VERSION_NAME}",
                            color = RdTextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = when (updateState) {
                        is com.ridedecider.app.domain.model.update.AppUpdateState.Checking -> "Buscando actualizaciones..."
                        is com.ridedecider.app.domain.model.update.AppUpdateState.UpToDate -> "RideDecider está actualizado a la última versión."
                        is com.ridedecider.app.domain.model.update.AppUpdateState.Available -> "Nueva versión disponible para instalar."
                        is com.ridedecider.app.domain.model.update.AppUpdateState.Error -> "No se pudo comprobar si hay actualizaciones."
                        else -> "Comprueba si existen nuevas versiones de rendimiento y compatibilidad."
                    },
                    color = when (updateState) {
                        is com.ridedecider.app.domain.model.update.AppUpdateState.UpToDate -> RdStatusAhead
                        is com.ridedecider.app.domain.model.update.AppUpdateState.Error -> RdStatusBehind
                        else -> RdTextTertiary
                    },
                    fontSize = 12.sp
                )

                OutlinedButton(
                    onClick = { appUpdateManager.checkForUpdates() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, RdBorderSubtle),
                    enabled = updateState !is com.ridedecider.app.domain.model.update.AppUpdateState.Checking
                ) {
                    Text(
                        text = if (updateState is com.ridedecider.app.domain.model.update.AppUpdateState.Checking) {
                            "Comprobando..."
                        } else {
                            "Comprobar Actualizaciones"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RdTextPrimary
                    )
                }
            }
        }

        // 6. Sección: Información de la Aplicación y Principio
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RdBorderSubtle, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = RdSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ACERCA DE RIDEDECIDER",
                    color = RdTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                Text(
                    text = "RideDecider v${com.ridedecider.app.BuildConfig.VERSION_NAME} Professional",
                    color = RdTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Diseñado exclusivamente para conductores profesionales de Uber Driver. Sin automatización, sin clicks automáticos ni acciones sintéticas.",
                    color = RdTextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RdSurfaceElevated, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "PRINCIPIO FUNDAMENTAL",
                            color = RdBrandPrimaryLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "RideDecider observa, analiza y recomienda.\nEl conductor siempre toma manualmente la decisión.",
                            color = RdTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
