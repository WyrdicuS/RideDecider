package com.ridedecider.app.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ridedecider.app.domain.model.update.AppReleaseManifest
import com.ridedecider.app.domain.model.update.AppUpdateState
import com.ridedecider.app.ui.theme.RdBgBase
import com.ridedecider.app.ui.theme.RdBorderSubtle
import com.ridedecider.app.ui.theme.RdBrandPrimary
import com.ridedecider.app.ui.theme.RdBrandPrimaryLight
import com.ridedecider.app.ui.theme.RdStatusAhead
import com.ridedecider.app.ui.theme.RdStatusBehind
import com.ridedecider.app.ui.theme.RdSurface
import com.ridedecider.app.ui.theme.RdSurfaceElevated
import com.ridedecider.app.ui.theme.RdTextPrimary
import com.ridedecider.app.ui.theme.RdTextSecondary
import com.ridedecider.app.ui.theme.RdTextTertiary

/**
 * Diálogo modal para gestión de actualizaciones privadas de RideDecider.
 * Cumple estrictamente con el sistema cromático Deep Obsidian & Kinetic Iris y la regla de 0 emojis.
 */
@Composable
fun UpdateAvailableDialog(
    state: AppUpdateState,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallApk: (Context) -> Unit,
    onRequestPermission: (Context) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = {
            if (state !is AppUpdateState.Downloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = state !is AppUpdateState.Downloading,
            dismissOnClickOutside = state !is AppUpdateState.Downloading
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(RdSurface)
                .border(1.dp, RdBorderSubtle, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // 1. Icono y Título
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(RdBrandPrimary.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, RdBrandPrimary.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (state) {
                            is AppUpdateState.Downloading -> Icons.Rounded.CloudDownload
                            is AppUpdateState.Downloaded -> Icons.Rounded.CheckCircle
                            is AppUpdateState.Error -> Icons.Rounded.ErrorOutline
                            else -> Icons.Rounded.SystemUpdate
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Icono de actualización",
                            tint = when (state) {
                                is AppUpdateState.Downloaded -> RdStatusAhead
                                is AppUpdateState.Error -> RdStatusBehind
                                else -> RdBrandPrimaryLight
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = when (state) {
                                is AppUpdateState.Available -> "Nueva Versión Disponible"
                                is AppUpdateState.Downloading -> "Descargando Actualización"
                                is AppUpdateState.Downloaded -> "Descarga Completada"
                                is AppUpdateState.Installing -> "Instalando Actualización"
                                is AppUpdateState.Error -> "Error de Actualización"
                                else -> "Actualización RideDecider"
                            },
                            color = RdTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Distribución Privada OTA",
                            color = RdTextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }

                // 2. Contenido según estado
                when (state) {
                    is AppUpdateState.Available -> {
                        ManifestContent(manifest = state.manifest)
                    }
                    is AppUpdateState.Downloading -> {
                        DownloadingContent(
                            percentage = state.progressPercentage,
                            downloadedBytes = state.bytesDownloaded,
                            totalBytes = state.totalBytes
                        )
                    }
                    is AppUpdateState.Downloaded -> {
                        DownloadedContent(manifest = state.manifest)
                    }
                    is AppUpdateState.Installing -> {
                        InstallingContent()
                    }
                    is AppUpdateState.Error -> {
                        ErrorContent(message = state.message)
                    }
                    else -> Unit
                }

                // 3. Botones de Acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (state) {
                        is AppUpdateState.Available -> {
                            if (!state.manifest.isMandatory) {
                                TextButton(onClick = onDismiss) {
                                    Text("Más tarde", color = RdTextSecondary, fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Button(
                                onClick = onStartDownload,
                                colors = ButtonDefaults.buttonColors(containerColor = RdBrandPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Actualizar Ahora", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        is AppUpdateState.Downloading -> {
                            OutlinedButton(
                                onClick = onCancelDownload,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cancelar", color = RdStatusBehind, fontSize = 13.sp)
                            }
                        }

                        is AppUpdateState.Downloaded -> {
                            Button(
                                onClick = { onInstallApk(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = RdBrandPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Instalar Actualización", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        is AppUpdateState.Error -> {
                            TextButton(onClick = onDismiss) {
                                Text("Cerrar", color = RdTextSecondary, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(containerColor = RdBrandPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Reintentar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun ManifestContent(manifest: AppReleaseManifest) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(RdSurfaceElevated, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "RideDecider v${manifest.versionName}",
                        color = RdBrandPrimaryLight,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Build ${manifest.versionCode}",
                        color = RdTextTertiary,
                        fontSize = 11.sp
                    )
                }
                if (manifest.apkSizeBytes > 0) {
                    val mb = manifest.apkSizeBytes / (1024.0 * 1024.0)
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f MB", mb),
                        color = RdTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (manifest.releaseNotes.isNotEmpty()) {
            Text(
                text = "Novedades de la versión:",
                color = RdTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                manifest.releaseNotes.forEach { note ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(5.dp)
                                .background(RdBrandPrimaryLight, CircleShape)
                        )
                        Text(
                            text = note,
                            color = RdTextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadingContent(percentage: Int, downloadedBytes: Long, totalBytes: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LinearProgressIndicator(
            progress = { (percentage / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = RdBrandPrimary,
            trackColor = RdBgBase,
            strokeCap = StrokeCap.Round
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val downloadedMb = downloadedBytes / (1024.0 * 1024.0)
            val totalMb = totalBytes / (1024.0 * 1024.0)
            Text(
                text = String.format(java.util.Locale.US, "%.1f MB / %.1f MB", downloadedMb, totalMb),
                color = RdTextTertiary,
                fontSize = 11.sp
            )
            Text(
                text = "$percentage%",
                color = RdBrandPrimaryLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DownloadedContent(manifest: AppReleaseManifest) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(RdSurfaceElevated, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = "Integridad SHA-256 verificada",
                tint = RdStatusAhead,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "Paquete APK v${manifest.versionName} verificado e íntegro con SHA-256. Listo para instalar.",
                color = RdTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun InstallingContent() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = RdBrandPrimary,
            strokeWidth = 2.5.dp
        )
        Text(
            text = "Iniciando instalador nativo de Android...",
            color = RdTextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ErrorContent(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(RdStatusBehind.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .border(1.dp, RdStatusBehind.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = message,
            color = RdStatusBehind,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}
