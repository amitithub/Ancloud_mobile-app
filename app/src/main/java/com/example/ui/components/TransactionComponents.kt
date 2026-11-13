package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CurrencyCode
import com.example.data.model.PulseCategories
import com.example.data.model.TransactionEntity
import com.example.domain.MonthlySummary
import com.example.ui.TransactionFilterType
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalPulseColors
import com.example.util.FormatUtils

@Composable
fun KpiBentoRow(
  summary: MonthlySummary,
  currency: CurrencyCode,
  isWide: Boolean,
  modifier: Modifier = Modifier,
) {
  if (isWide) {
    Row(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      NetBalanceHeroCard(
        netBalance = summary.netBalance,
        savingsRate = summary.savingsRate,
        currency = currency,
        modifier = Modifier.weight(1.15f),
      )
      MetricBentoCard(
        label = stringResource(R.string.income_label),
        amount = summary.totalIncome,
        currency = currency,
        isIncome = true,
        modifier = Modifier.weight(1f),
      )
      MetricBentoCard(
        label = stringResource(R.string.expenses_label),
        amount = summary.totalExpense,
        currency = currency,
        isIncome = false,
        modifier = Modifier.weight(1f),
      )
    }
  } else {
    Column(
      modifier = modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      NetBalanceHeroCard(
        netBalance = summary.netBalance,
        savingsRate = summary.savingsRate,
        currency = currency,
        modifier = Modifier.fillMaxWidth(),
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        MetricBentoCard(
          label = stringResource(R.string.income_label),
          amount = summary.totalIncome,
          currency = currency,
          isIncome = true,
          modifier = Modifier.weight(1f),
        )
        MetricBentoCard(
          label = stringResource(R.string.expenses_label),
          amount = summary.totalExpense,
          currency = currency,
          isIncome = false,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun NetBalanceHeroCard(
  netBalance: Double,
  savingsRate: Double,
  currency: CurrencyCode,
  modifier: Modifier = Modifier,
) {
  val pulseColors = LocalPulseColors.current
  Box(
    modifier = modifier
      .clip(MaterialTheme.shapes.large)
      .background(Brush.linearGradient(listOf(pulseColors.heroStart, pulseColors.heroEnd)))
      .padding(20.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        text = stringResource(R.string.net_balance).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.8f),
      )
      Text(
        text = FormatUtils.formatCurrency(netBalance, currency),
        style = MaterialTheme.typography.displaySmall.copy(fontFamily = JetBrainsMonoFamily),
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.16f),
      ) {
        Text(
          text = "Savings rate: $savingsRate%",
          style = MaterialTheme.typography.labelMedium,
          color = Color.White,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
      }
    }
  }
}

@Composable
private fun MetricBentoCard(
  label: String,
  amount: Double,
  currency: CurrencyCode,
  isIncome: Boolean,
  modifier: Modifier = Modifier,
) {
  val pulseColors = LocalPulseColors.current
  val accent = if (isIncome) pulseColors.income else pulseColors.expense
  val icon = if (isIncome) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown

  Card(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, pulseColors.cardBorder),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = label.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          color = pulseColors.mutedText,
        )
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = accent.copy(alpha = 0.12f),
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier
              .padding(6.dp)
              .size(18.dp),
          )
        }
      }
      Text(
        text = FormatUtils.formatCurrency(amount, currency),
        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMonoFamily),
        color = accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun TransactionsCard(
  transactions: List<TransactionEntity>,
  currency: CurrencyCode,
  searchQuery: String,
  filterType: TransactionFilterType,
  onSearchChange: (String) -> Unit,
  onFilterTypeChange: (TransactionFilterType) -> Unit,
  onEditClick: (TransactionEntity) -> Unit,
  onDeleteClick: (TransactionEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  val pulseColors = LocalPulseColors.current

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, pulseColors.cardBorder),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.transactions_title),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "${transactions.size} items",
          style = MaterialTheme.typography.labelMedium,
          color = pulseColors.mutedText,
        )
      }

      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text("Search title, category, or note...") },
        leadingIcon = {
          Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(
              onClick = { onSearchChange("") },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear search")
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("search_transactions_input"),
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = filterType == TransactionFilterType.ALL,
          onClick = { onFilterTypeChange(TransactionFilterType.ALL) },
          label = { Text("All Types") },
          modifier = Modifier.testTag("filter_all"),
        )
        FilterChip(
          selected = filterType == TransactionFilterType.EXPENSE,
          onClick = { onFilterTypeChange(TransactionFilterType.EXPENSE) },
          label = { Text("Expenses") },
          modifier = Modifier.testTag("filter_expense"),
        )
        FilterChip(
          selected = filterType == TransactionFilterType.INCOME,
          onClick = { onFilterTypeChange(TransactionFilterType.INCOME) },
          label = { Text("Income") },
          modifier = Modifier.testTag("filter_income"),
        )
      }

      if (transactions.isEmpty()) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.ReceiptLong,
            contentDescription = null,
            tint = pulseColors.mutedText.copy(alpha = 0.6f),
            modifier = Modifier.size(40.dp),
          )
          Text(
            text = stringResource(R.string.empty_transactions),
            style = MaterialTheme.typography.bodyMedium,
            color = pulseColors.mutedText,
            textAlign = TextAlign.Center,
          )
        }
      } else {
        Column {
          transactions.forEachIndexed { idx, tx ->
            TransactionRow(
              tx = tx,
              currency = currency,
              onEdit = { onEditClick(tx) },
              onDelete = { onDeleteClick(tx) },
            )
            if (idx < transactions.lastIndex) {
              HorizontalDivider(color = pulseColors.cardBorder.copy(alpha = 0.6f))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TransactionRow(
  tx: TransactionEntity,
  currency: CurrencyCode,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val pulseColors = LocalPulseColors.current
  val meta = PulseCategories.find(tx.category)
  val isIncome = tx.type == "income"
  val amountColor = if (isIncome) pulseColors.income else MaterialTheme.colorScheme.onSurface
  val sign = if (isIncome) "+" else "-"

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onEdit)
      .padding(vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(Color(meta.colorHex)),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = meta.label.take(2).uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        fontWeight = FontWeight.Bold,
      )
    }

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = tx.title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = buildString {
          append(meta.label)
          append(" • ")
          append(FormatUtils.formatShortDate(tx.date))
          if (tx.note.isNotBlank()) {
            append(" • ")
            append(tx.note)
          }
        },
        style = MaterialTheme.typography.bodySmall,
        color = pulseColors.mutedText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    Column(horizontalAlignment = Alignment.End) {
      Text(
        text = "$sign${FormatUtils.formatCurrency(tx.amount, currency)}",
        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
        color = amountColor,
        fontWeight = FontWeight.Bold,
      )
      Row {
        IconButton(
          onClick = onEdit,
          modifier = Modifier
            .size(36.dp)
            .minimumInteractiveComponentSize()
            .testTag("edit_tx_${tx.id}"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = "Edit ${tx.title}",
            tint = pulseColors.mutedText,
            modifier = Modifier.size(18.dp),
          )
        }
        IconButton(
          onClick = onDelete,
          modifier = Modifier
            .size(36.dp)
            .minimumInteractiveComponentSize()
            .testTag("delete_tx_${tx.id}"),
        ) {
          Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = "Delete ${tx.title}",
            tint = pulseColors.expense,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}
