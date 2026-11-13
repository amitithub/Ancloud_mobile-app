package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CurrencyCode
import com.example.domain.DailyTrendPoint
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalPulseColors
import com.example.util.FormatUtils
import kotlin.math.max

@Composable
fun CashflowChart(
  dailyTrend: List<DailyTrendPoint>,
  currency: CurrencyCode,
  modifier: Modifier = Modifier,
) {
  val pulseColors = LocalPulseColors.current
  var selectedIndex by remember(dailyTrend) { mutableStateOf<Int?>(null) }

  if (dailyTrend.isEmpty()) {
    Column(
      modifier = modifier
        .fillMaxWidth()
        .height(210.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = Icons.Outlined.BarChart,
        contentDescription = null,
        tint = pulseColors.mutedText.copy(alpha = 0.6f),
        modifier = Modifier.size(40.dp),
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = stringResource(R.string.empty_cashflow),
        style = MaterialTheme.typography.bodyMedium,
        color = pulseColors.mutedText,
        textAlign = TextAlign.Center,
      )
    }
    return
  }

  val maxVal = remember(dailyTrend) {
    dailyTrend.maxOfOrNull { max(it.income, it.expense) }?.coerceAtLeast(1.0) ?: 1.0
  }
  val selectedDay = selectedIndex?.let { dailyTrend.getOrNull(it) }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        LegendDot(color = pulseColors.income, label = stringResource(R.string.income_label))
        LegendDot(color = pulseColors.expense, label = stringResource(R.string.expenses_label))
      }

      if (selectedDay != null) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
          Text(
            text = "${FormatUtils.formatShortDate(selectedDay.date)}: +${
              FormatUtils.formatCurrency(selectedDay.income, currency)
            } / -${FormatUtils.formatCurrency(selectedDay.expense, currency)}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
      }
    }

    val groupWidthDp = 48.dp
    val minChartWidthDp = 300.dp
    val computedWidth = (groupWidthDp * dailyTrend.size).coerceAtLeast(minChartWidthDp)
    val gridColor = pulseColors.cardBorder
    val incomeColor = pulseColors.income
    val expenseColor = pulseColors.expense

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
    ) {
      Canvas(
        modifier = Modifier
          .width(computedWidth)
          .height(164.dp)
          .semantics { contentDescription = "Daily income versus expense cashflow bar chart" }
          .pointerInput(dailyTrend) {
            detectTapGestures { offset ->
              val groupPx = size.width / dailyTrend.size.toFloat()
              val tapped = (offset.x / groupPx).toInt().coerceIn(0, dailyTrend.lastIndex)
              selectedIndex = if (selectedIndex == tapped) null else tapped
            }
          },
      ) {
        val chartHeight = size.height - 12.dp.toPx()
        val topPad = 10.dp.toPx()
        val usableHeight = (chartHeight - topPad).coerceAtLeast(1f)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { ratio ->
          val y = chartHeight - ratio * usableHeight
          drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect,
          )
        }

        val groupPx = size.width / dailyTrend.size.toFloat()
        val barWidth = 12.dp.toPx()
        val gapBetweenBars = 4.dp.toPx()
        val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

        dailyTrend.forEachIndexed { idx, day ->
          val center = idx * groupPx + groupPx / 2f
          val alpha = if (selectedIndex != null && selectedIndex != idx) 0.4f else 1f

          val incHeight = ((day.income / maxVal) * usableHeight).toFloat()
            .let { if (day.income > 0.0) it.coerceAtLeast(4.dp.toPx()) else 0f }
          val expHeight = ((day.expense / maxVal) * usableHeight).toFloat()
            .let { if (day.expense > 0.0) it.coerceAtLeast(4.dp.toPx()) else 0f }

          if (incHeight > 0f) {
            drawRoundRect(
              color = incomeColor.copy(alpha = alpha),
              topLeft = Offset(center - barWidth - gapBetweenBars / 2f, chartHeight - incHeight),
              size = Size(barWidth, incHeight),
              cornerRadius = cornerRadius,
            )
          }

          if (expHeight > 0f) {
            drawRoundRect(
              color = expenseColor.copy(alpha = alpha),
              topLeft = Offset(center + gapBetweenBars / 2f, chartHeight - expHeight),
              size = Size(barWidth, expHeight),
              cornerRadius = cornerRadius,
            )
          }
        }
      }

      // Day labels aligned under each group
      Row(
        modifier = Modifier.width(computedWidth),
      ) {
        dailyTrend.forEachIndexed { idx, day ->
          val isSelected = selectedIndex == idx
          Text(
            text = day.date.takeLast(2),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else pulseColors.mutedText,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Composable
private fun LegendDot(
  color: androidx.compose.ui.graphics.Color,
  label: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(
      modifier = Modifier
        .size(9.dp)
        .clip(RoundedCornerShape(3.dp))
        .background(color),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = LocalPulseColors.current.mutedText,
    )
  }
}
