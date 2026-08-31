package com.ridedecider.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridedecider.app.data.di.ServiceLocator
import com.ridedecider.app.domain.engine.EarningsTracker
import com.ridedecider.app.domain.model.DriverGoals
import com.ridedecider.app.domain.model.EarningsProgress
import com.ridedecider.app.domain.model.GoalPeriod
import com.ridedecider.app.ui.components.GoalProgressCard
import com.ridedecider.app.ui.theme.RdBgBase
import com.ridedecider.app.ui.theme.RdBgCanvas
import com.ridedecider.app.ui.theme.RdBorderSubtle
import com.ridedecider.app.ui.theme.RdBrandPrimary
import com.ridedecider.app.ui.theme.RdStatusAhead
import com.ridedecider.app.ui.theme.RdSurface
import com.ridedecider.app.ui.theme.RdSurfaceInteractive
import com.ridedecider.app.ui.theme.RdTextPrimary
import com.ridedecider.app.ui.theme.RdTextSecondary
import com.ridedecider.app.ui.theme.RdTextTertiary
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun GoalsScreen(
    earningsTracker: EarningsTracker,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val goalsRepository = remember { ServiceLocator.getDriverGoalsRepository(context) }
    val scrollState = rememberScrollState()

    var selectedPeriod by remember { mutableStateOf(GoalPeriod.DAILY) }
    var dailyProgress by remember { mutableStateOf<EarningsProgress?>(null) }
    var weeklyProgress by remember { mutableStateOf<EarningsProgress?>(null) }
    var monthlyProgress by remember { mutableStateOf<EarningsProgress?>(null) }
    var currentGoals by remember { mutableStateOf<DriverGoals?>(null) }

    // Editor state
    var editTargetInput by remember { mutableStateOf("") }
    var editHoursInput by remember { mutableStateOf("") }
    var showEditSuccess by remember { mutableStateOf(false) }

    fun refreshGoalsData() {
        coroutineScope.launch {
            val goals = goalsRepository.getGoals()
            currentGoals = goals
            dailyProgress = earningsTracker.getDailyProgress()
            weeklyProgress = earningsTracker.getWeeklyProgress()
            monthlyProgress = earningsTracker.getMonthlyProgress()

            // Update inputs for active period
            when (selectedPeriod) {
                GoalPeriod.DAILY -> {
                    editTargetInput = goals.dailyTargetEur.toInt().toString()
                    editHoursInput = goals.dailyPlannedHours.toInt().toString()
                }
                GoalPeriod.WEEKLY -> {
                    editTargetInput = goals.weeklyTargetEur.toInt().toString()
                    editHoursInput = goals.weeklyPlannedHours.toInt().toString()
                }
                GoalPeriod.MONTHLY -> {
                    editTargetInput = goals.monthlyTargetEur.toInt().toString()
                    editHoursInput = goals.monthlyPlannedHours.toInt().toString()
                }
            }
        }
    }

    LaunchedEffect(selectedPeriod) {
        refreshGoalsData()
    }

    val activeProgress = when (selectedPeriod) {
        GoalPeriod.DAILY -> dailyProgress
        GoalPeriod.WEEKLY -> weeklyProgress
        GoalPeriod.MONTHLY -> monthlyProgress
    }

    val periodTitle = when (selectedPeriod) {
        GoalPeriod.DAILY -> "Objetivo Diario"
        GoalPeriod.WEEKLY -> "Objetivo Semanal"
        GoalPeriod.MONTHLY -> "Objetivo Mensual"
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
            text = "Objetivos Económicos",
            color = RdTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.3).sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        // 2. Selector de Período (Pestañas Diario / Semanal / Mensual)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RdSurfaceInteractive, RoundedCornerShape(12.dp))
                .border(1.dp, RdBorderSubtle, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(
                GoalPeriod.DAILY to "DIARIO",
                GoalPeriod.WEEKLY to "SEMANAL",
                GoalPeriod.MONTHLY to "MENSUAL"
            ).forEach { (period, label) ->
                val isSelected = selectedPeriod == period
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) RdBrandPrimary else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            selectedPeriod = period
                            showEditSuccess = false
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else RdTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // 3. Tarjeta de Progreso Detallada
        GoalProgressCard(
            title = periodTitle,
            progress = activeProgress,
            isHero = true
        )

        // 4. Editor Rápido del Objetivo Seleccionado
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RdBorderSubtle, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = RdSurface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "CONFIGURAR $periodTitle".uppercase(Locale.ROOT),
                    color = RdTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                val saveButtonBg by animateColorAsState(
                    targetValue = if (showEditSuccess) RdStatusAhead else RdBrandPrimary,
                    animationSpec = tween(durationMillis = 250),
                    label = "goalsSaveBtnBg"
                )

                LaunchedEffect(showEditSuccess) {
                    if (showEditSuccess) {
                        kotlinx.coroutines.delay(2000L)
                        showEditSuccess = false
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editTargetInput,
                        onValueChange = { editTargetInput = it },
                        label = { Text("Meta", fontSize = 12.sp) },
                        trailingIcon = {
                            Text("€", color = RdTextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = RdBgBase,
                            unfocusedContainerColor = RdBgBase,
                            focusedBorderColor = RdBrandPrimary,
                            unfocusedBorderColor = RdBorderSubtle,
                            focusedTextColor = RdTextPrimary,
                            unfocusedTextColor = RdTextPrimary
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editHoursInput,
                        onValueChange = { editHoursInput = it },
                        label = { Text("Horas planificadas", fontSize = 12.sp) },
                        trailingIcon = {
                            Text("h", color = RdTextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = RdBgBase,
                            unfocusedContainerColor = RdBgBase,
                            focusedBorderColor = RdBrandPrimary,
                            unfocusedBorderColor = RdBorderSubtle,
                            focusedTextColor = RdTextPrimary,
                            unfocusedTextColor = RdTextPrimary
                        ),
                        singleLine = true
                    )
                }

                // Botón Guardar con Micro-Feedback
                Button(
                    onClick = {
                        val target = editTargetInput.toDoubleOrNull() ?: 120.0
                        val hours = editHoursInput.toDoubleOrNull() ?: 8.0
                        coroutineScope.launch {
                            val current = goalsRepository.getGoals()
                            val updated = when (selectedPeriod) {
                                GoalPeriod.DAILY -> current.copy(dailyTargetEur = target, dailyPlannedHours = hours)
                                GoalPeriod.WEEKLY -> current.copy(weeklyTargetEur = target, weeklyPlannedHours = hours)
                                GoalPeriod.MONTHLY -> current.copy(monthlyTargetEur = target, monthlyPlannedHours = hours)
                            }
                            goalsRepository.updateGoals(updated)
                            showEditSuccess = true
                            refreshGoalsData()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = saveButtonBg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (showEditSuccess) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Guardado",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Objetivo Guardado", fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Text(text = "Guardar Objetivo", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
