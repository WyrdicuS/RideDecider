package com.ridedecider.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridedecider.app.data.di.ServiceLocator
import com.ridedecider.app.ui.components.AppBottomNavigation
import com.ridedecider.app.ui.components.AppTab
import com.ridedecider.app.ui.screens.GoalsScreen
import com.ridedecider.app.ui.screens.HomeScreen
import com.ridedecider.app.ui.screens.LiveScreen
import com.ridedecider.app.ui.screens.SettingsScreen
import com.ridedecider.app.ui.theme.RdBackground
import com.ridedecider.app.ui.theme.RideDeciderTheme

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

                // R6.3: comprobacion automatica de actualizaciones al iniciar la app y
                // presentacion global del dialogo (aparece en cualquier pestaña, no solo Ajustes).
                // AppUpdateManager es singleton via ServiceLocator: SettingsScreen comparte estado.
                val appUpdateManager = remember { ServiceLocator.getAppUpdateManager(context) }
                val updateState by appUpdateManager.updateState.collectAsState()
                var showInstallPermissionDialog by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    // Solo dispara la comprobacion si aun no se ha ejecutado en esta sesion.
                    if (updateState is com.ridedecider.app.domain.model.update.AppUpdateState.Idle) {
                        appUpdateManager.checkForUpdates()
                    }
                }

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

                // Dialogo global de actualizacion: se muestra en TODAS las pantallas si
                // AppUpdateManager reporta un estado accionable (Available/Downloading/
                // Downloaded/Installing/Error). Dismiss => resetState (Idle), y no vuelve
                // a aparecer hasta el proximo checkForUpdates (manual en Ajustes o reinicio).
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
                                showInstallPermissionDialog = true
                            }
                        },
                        onRequestPermission = { actContext ->
                            actContext.startActivity(appUpdateManager.getManageUnknownAppSourcesIntent(actContext))
                        },
                        onRetry = { appUpdateManager.checkForUpdates() },
                        onDismiss = { appUpdateManager.resetState() }
                    )
                }

                if (showInstallPermissionDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showInstallPermissionDialog = false },
                        containerColor = com.ridedecider.app.ui.theme.RdSurface,
                        title = {
                            Text(
                                text = "Permiso de Instalación Necesario",
                                color = com.ridedecider.app.ui.theme.RdTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                text = "Android requiere que autorices a RideDecider para instalar actualizaciones descargadas fuera de Google Play Store.",
                                color = com.ridedecider.app.ui.theme.RdTextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showInstallPermissionDialog = false
                                    context.startActivity(appUpdateManager.getManageUnknownAppSourcesIntent(context))
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = com.ridedecider.app.ui.theme.RdBrandPrimary
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Permitir Instalación", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(
                                onClick = { showInstallPermissionDialog = false }
                            ) {
                                Text(
                                    "Cancelar",
                                    color = com.ridedecider.app.ui.theme.RdTextTertiary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    )
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
