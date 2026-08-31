package com.ridedecider.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridedecider.app.ui.theme.RdBgBase
import com.ridedecider.app.ui.theme.RdBorderSubtle
import com.ridedecider.app.ui.theme.RdBrandPrimary
import com.ridedecider.app.ui.theme.RdBrandPrimaryLight
import com.ridedecider.app.ui.theme.RdSurfaceElevated
import com.ridedecider.app.ui.theme.RdTextPrimary
import com.ridedecider.app.ui.theme.RdTextTertiary

enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Inicio", Icons.Rounded.Home),
    LIVE("En Vivo", Icons.Rounded.Sensors),
    GOALS("Objetivos", Icons.Rounded.TrackChanges),
    SETTINGS("Ajustes", Icons.Rounded.Settings)
}

@Composable
fun AppBottomNavigation(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(RdBgBase)
            .border(
                width = 1.dp,
                color = RdBorderSubtle,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.values().forEach { tab ->
                val isSelected = tab == selectedTab
                val contentColor = if (isSelected) RdBrandPrimaryLight else RdTextTertiary

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tab) }
                        .background(
                            color = if (isSelected) RdBrandPrimary.copy(alpha = 0.10f) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        color = if (isSelected) RdTextPrimary else RdTextTertiary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .size(width = 16.dp, height = 2.5.dp)
                                .background(RdBrandPrimary, RoundedCornerShape(2.dp))
                        )
                    } else {
                        Spacer(modifier = Modifier.height(5.5.dp))
                    }
                }
            }
        }
    }
}
