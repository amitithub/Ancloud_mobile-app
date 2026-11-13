package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CurrencyCode
import com.example.domain.BudgetHealthStatus
import com.example.domain.BudgetStatusItem
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalPulseColors
import com.example.util.FormatUtils

@Composable
fun BudgetsSection(
  budgetStatuses: List<BudgetStatusItem>,
  currency: CurrencyCode,
  isWide: Boolean,
  onSaveBudget: (String, Double) -> Unit,
  onApplySuggested: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var editingCategory by remember { mutableStateOf<String?>(null) }
  var draftLimit by remember { mutableStateOf("") }
  val allUnconfigured = budgetStatuses.all { it.status == BudgetHealthStatus.UNCONFIGURED }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.monthly_budgets),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
      )
      if (allUnconfigured) {
        OutlinedButton(
          onClick = onApplySuggested,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("apply_suggested_budgets_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.apply_suggested_budgets),
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }
    }

    if (isWide) {
      val rows = budgetStatuses.chunked(2)
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { pair ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            pair.forEach { item ->
              BudgetCard(
                item = item,
                currency = currency,
                isEditing = editingCategory == item.category,
                draftLimit = draftLimit,
                onDraftLimitChange = { draftLimit = it },
                onStartEdit = {
                  editingCategory = item.category
                  draftLimit = if (item.limit > 0.0) item.limit.toInt().toString() else ""
                },
                onCancelEdit = { editingCategory = null },
                onCommitEdit = {
                  val numeric = draftLimit.trim().toDoubleOrNull()
                  if (numeric != null && numeric >= 0.0) {
                    onSaveBudget(item.category, numeric)
                  }
                  editingCategory = null
                },
                modifier = Modifier.weight(1f),
              )
            }
            if (pair.size == 1) {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        budgetStatuses.forEach { item ->
          BudgetCard(
            item = item,
            currency = currency,
            isEditing = editingCategory == item.category,
            draftLimit = draftLimit,
            onDraftLimitChange = { draftLimit = it },
            onStartEdit = {
              editingCategory = item.category
              draftLimit = if (item.limit > 0.0) item.limit.toInt().toString() else ""
            },
            onCancelEdit = { editingCategory = null },
            onCommitEdit = {
              val numeric = draftLimit.trim().toDoubleOrNull()
              if (numeric != null && numeric >= 0.0) {
                onSaveBudget(item.category, numeric)
              }
              editingCategory = null
            },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}

@Composable
private fun BudgetCard(
  item: BudgetStatusItem,
  currency: CurrencyCode,
  isEditing: Boolean,
  draftLimit: String,
  onDraftLimitChange: (String) -> Unit,
  onStartEdit: () -> Unit,
  onCancelEdit: () -> Unit,
  onCommitEdit: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val pulseColors = LocalPulseColors.current
  val barColor = when (item.status) {
    BudgetHealthStatus.EXCEEDED -> pulseColors.expense
    BudgetHealthStatus.WARNING -> pulseColors.warning
    BudgetHealthStatus.HEALTHY -> pulseColors.income
    BudgetHealthStatus.UNCONFIGURED -> pulseColors.mutedText.copy(alpha = 0.4f)
  }
  val badgeText = when (item.status) {
    BudgetHealthStatus.EXCEEDED -> "Over Budget"
    BudgetHealthStatus.WARNING -> "Near Limit"
    BudgetHealthStatus.HEALTHY -> "On Track"
    BudgetHealthStatus.UNCONFIGURED -> "No Limit"
  }
  val badgeContainer = when (item.status) {
    BudgetHealthStatus.EXCEEDED -> pulseColors.expense.copy(alpha = 0.14f)
    BudgetHealthStatus.WARNING -> pulseColors.warning.copy(alpha = 0.14f)
    BudgetHealthStatus.HEALTHY -> pulseColors.income.copy(alpha = 0.14f)
    BudgetHealthStatus.UNCONFIGURED -> pulseColors.pillBackground
  }
  val badgeTextColor = when (item.status) {
    BudgetHealthStatus.EXCEEDED -> pulseColors.expense
    BudgetHealthStatus.WARNING -> pulseColors.warning
    BudgetHealthStatus.HEALTHY -> pulseColors.income
    BudgetHealthStatus.UNCONFIGURED -> pulseColors.mutedText
  }

  val animatedProgress by animateFloatAsState(
    targetValue = (item.utilization.toFloat() / 100f).coerceIn(0f, 1f),
    animationSpec = tween(350),
    label = "budgetProgress",
  )

  Card(
    modifier = modifier.testTag("budget_card_${item.category}"),
    shape = MaterialTheme.shapes.medium,
    border = BorderStroke(1.dp, pulseColors.cardBorder),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier = Modifier
              .size(10.dp)
              .clip(CircleShape)
              .background(Color(item.colorHex)),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = item.label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Surface(
          shape = RoundedCornerShape(50),
          color = badgeContainer,
        ) {
          Text(
            text = badgeText,
            style = MaterialTheme.typography.labelSmall,
            color = badgeTextColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      if (isEditing) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          OutlinedTextField(
            value = draftLimit,
            onValueChange = onDraftLimitChange,
            label = { Text("Monthly Limit") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
              .weight(1f)
              .testTag("budget_limit_input_${item.category}"),
          )
          IconButton(
            onClick = onCommitEdit,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("budget_save_${item.category}"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Check,
              contentDescription = "Save budget for ${item.label}",
              tint = pulseColors.income,
            )
          }
          IconButton(
            onClick = onCancelEdit,
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = "Cancel editing budget",
              tint = pulseColors.mutedText,
            )
          }
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Spent ${FormatUtils.formatCurrency(item.spent, currency)}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
          )
          TextButton(
            onClick = onStartEdit,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("budget_edit_${item.category}"),
          ) {
            Text(
              text = if (item.limit > 0.0) {
                "Limit: ${FormatUtils.formatCurrency(item.limit, currency)}"
              } else {
                "Set limit"
              },
              style = MaterialTheme.typography.labelMedium,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.Outlined.Edit,
              contentDescription = "Edit limit for ${item.label}",
              modifier = Modifier.size(14.dp),
            )
          }
        }
      }

      // Progress Bar
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(8.dp)
          .clip(RoundedCornerShape(50))
          .background(pulseColors.pillBackground),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth(animatedProgress)
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(barColor),
        )
      }
    }
  }
}
