package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CurrencyCode
import com.example.domain.CategoryBreakdownItem
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalPulseColors
import com.example.util.FormatUtils
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

@Composable
fun CategoryDonut(
  breakdown: List<CategoryBreakdownItem>,
  totalExpense: Double,
  currency: CurrencyCode,
  modifier: Modifier = Modifier,
) {
  val pulseColors = LocalPulseColors.current
  var activeCategory by remember { mutableStateOf<String?>(null) }

  if (breakdown.isEmpty() || totalExpense <= 0.0) {
    Column(
      modifier = modifier
        .fillMaxWidth()
        .height(210.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = Icons.Outlined.DonutLarge,
        contentDescription = null,
        tint = pulseColors.mutedText.copy(alpha = 0.6f),
        modifier = Modifier.size(40.dp),
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = stringResource(R.string.empty_donut),
        style = MaterialTheme.typography.bodyMedium,
        color = pulseColors.mutedText,
        textAlign = TextAlign.Center,
      )
    }
    return
  }

  val selectedItem = breakdown.firstOrNull { it.category == activeCategory }

  // Precompute angle ranges for tap hit-testing
  val sliceAngles = remember(breakdown, totalExpense) {
    var currentStart = -90f
    breakdown.map { item ->
      val sweep = ((item.amount / totalExpense) * 360.0).toFloat()
      val triple = Triple(item, currentStart, sweep)
      currentStart += sweep
      triple
    }
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Centered Interactive Donut
    Box(
      modifier = Modifier
        .size(188.dp)
        .align(Alignment.CenterHorizontally)
        .semantics { contentDescription = "Spending by category donut chart" },
      contentAlignment = Alignment.Center,
    ) {
      Canvas(
        modifier = Modifier
          .fillMaxSize()
          .pointerInput(sliceAngles) {
            detectTapGestures { offset ->
              val cx = size.width / 2f
              val cy = size.height / 2f
              val dx = offset.x - cx
              val dy = offset.y - cy
              val dist = hypot(dx, dy)
              val outerRadius = minOf(size.width, size.height) / 2f
              val innerRadius = outerRadius * 0.45f
              if (dist in innerRadius..outerRadius) {
                var angleDeg = (atan2(dy, dx) * 180.0 / PI).toFloat()
                if (angleDeg < -90f) angleDeg += 360f
                val hit = sliceAngles.firstOrNull { (_, start, sweep) ->
                  angleDeg >= start && angleDeg <= start + sweep
                }?.first
                if (hit != null) {
                  activeCategory = if (activeCategory == hit.category) null else hit.category
                }
              } else {
                activeCategory = null
              }
            }
          },
      ) {
        val maxStroke = 24.dp.toPx()
        val inset = maxStroke / 2f + 4.dp.toPx()
        val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
        val topLeft = Offset(inset, inset)
        val gapDeg = if (sliceAngles.size > 1) 3f else 0f

        sliceAngles.forEach { (item, startAngle, rawSweep) ->
          val isActive = activeCategory == item.category
          val dimmed = activeCategory != null && !isActive
          val strokeWidth = if (isActive) 24.dp.toPx() else 18.dp.toPx()
          val sweep = (rawSweep - gapDeg).coerceAtLeast(1f)
          drawArc(
            color = Color(item.colorHex).copy(alpha = if (dimmed) 0.35f else 1f),
            startAngle = startAngle + gapDeg / 2f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
          )
        }
      }

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 24.dp),
      ) {
        Text(
          text = (selectedItem?.label ?: "Total Spent").uppercase(),
          style = MaterialTheme.typography.labelSmall,
          color = pulseColors.mutedText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = FormatUtils.formatCurrency(selectedItem?.amount ?: totalExpense, currency),
          style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (selectedItem != null) {
          Text(
            text = "${selectedItem.percentage}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }

    // Legend Rows
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      breakdown.forEach { item ->
        val isActive = activeCategory == item.category
        val rowAlpha by animateFloatAsState(
          targetValue = if (activeCategory != null && !isActive) 0.55f else 1f,
          animationSpec = tween(180),
          label = "legendAlpha",
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .background(
              if (isActive) MaterialTheme.colorScheme.surfaceVariant
              else Color.Transparent
            )
            .clickable {
              activeCategory = if (isActive) null else item.category
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
          ) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(item.colorHex).copy(alpha = rowAlpha)),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = item.label,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = rowAlpha),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = FormatUtils.formatCurrency(item.amount, currency),
              style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = rowAlpha),
            )
            Text(
              text = "${item.percentage}%",
              style = MaterialTheme.typography.labelMedium,
              color = pulseColors.mutedText,
            )
          }
        }
      }
    }
  }
}
