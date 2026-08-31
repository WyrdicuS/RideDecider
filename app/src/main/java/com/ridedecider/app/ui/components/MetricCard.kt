package com.ridedecider.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridedecider.app.ui.theme.RdSurfaceBorder
import com.ridedecider.app.ui.theme.RdSurfaceCard
import com.ridedecider.app.ui.theme.RdTextPrimary
import com.ridedecider.app.ui.theme.RdTextSecondary
import com.ridedecider.app.ui.theme.RdTextTertiary

@Composable
fun MetricCard(
    label: String,
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    valueColor: Color = RdTextPrimary,
    subValue: String? = null
) {
    Card(
        modifier = modifier
            .border(1.dp, RdSurfaceBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RdSurfaceCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = RdTextSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    color = valueColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (unit != null) {
                    Text(
                        text = unit,
                        color = RdTextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }

            if (subValue != null) {
                Text(
                    text = subValue,
                    color = RdTextSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}
