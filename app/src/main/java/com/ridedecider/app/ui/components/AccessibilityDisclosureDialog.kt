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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ridedecider.app.R
import com.ridedecider.app.ui.theme.RdBorderSubtle
import com.ridedecider.app.ui.theme.RdBrandPrimary
import com.ridedecider.app.ui.theme.RdSurface
import com.ridedecider.app.ui.theme.RdSurfaceElevated
import com.ridedecider.app.ui.theme.RdTextPrimary
import com.ridedecider.app.ui.theme.RdTextSecondary

/**
 * Diálogo de Divulgación Destacada (Prominent Disclosure) y consentimiento previo
 * para el Servicio de Accesibilidad de RideDecider.
 *
 * Cumple rigurosamente con la política de Google Play User Data & Accessibility API.
 */
@Composable
fun AccessibilityDisclosureDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 640.dp)
                .border(1.dp, RdBorderSubtle, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = RdSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cabecera: Icono temático e Introducción
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        Icon(
                            imageVector = Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = RdBrandPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.accessibility_disclosure_title),
                        color = RdTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Contenido desplazable para asegurar legibilidad en cualquier pantalla
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.accessibility_disclosure_intro),
                        color = RdTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    // Sección 1: Qué información se utiliza
                    DisclosureSectionCard(
                        title = stringResource(R.string.accessibility_disclosure_section_data_title)
                    ) {
                        DisclosureBulletItem(text = stringResource(R.string.accessibility_disclosure_data_item_1))
                        Spacer(modifier = Modifier.height(6.dp))
                        DisclosureBulletItem(text = stringResource(R.string.accessibility_disclosure_data_item_2))
                    }

                    // Sección 2: Finalidad
                    DisclosureSectionCard(
                        title = stringResource(R.string.accessibility_disclosure_section_purpose_title)
                    ) {
                        Text(
                            text = stringResource(R.string.accessibility_disclosure_purpose_body),
                            color = RdTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }

                    // Sección 3: Privacidad y control
                    DisclosureSectionCard(
                        title = stringResource(R.string.accessibility_disclosure_section_privacy_title)
                    ) {
                        DisclosureBulletItem(text = stringResource(R.string.accessibility_disclosure_privacy_item_1))
                        Spacer(modifier = Modifier.height(6.dp))
                        DisclosureBulletItem(text = stringResource(R.string.accessibility_disclosure_privacy_item_2))
                    }
                }

                // Botones de acción
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RdBrandPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.accessibility_disclosure_action_continue),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RdTextSecondary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RdBorderSubtle),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.accessibility_disclosure_action_cancel),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = RdTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisclosureSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RdBorderSubtle, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = RdSurfaceElevated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title.uppercase(),
                color = RdTextSecondary,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            content()
        }
    }
}

@Composable
private fun DisclosureBulletItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(14.dp)
                .background(RdBrandPrimary.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = RdBrandPrimary,
                modifier = Modifier.size(10.dp)
            )
        }
        Text(
            text = text,
            color = RdTextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
