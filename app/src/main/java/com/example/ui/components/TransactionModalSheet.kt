package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.PulseCategories
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionType
import com.example.ui.theme.LocalPulseColors
import com.example.util.FormatUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionModalSheet(
  initialTx: TransactionEntity?,
  isSaving: Boolean,
  onDismiss: () -> Unit,
  onSave: suspend (
    editingId: String?,
    title: String,
    amountText: String,
    type: String,
    category: String,
    date: String,
    note: String,
  ) -> Result<Unit>,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  val pulseColors = LocalPulseColors.current

  var type by remember(initialTx) {
    mutableStateOf(TransactionType.fromValue(initialTx?.type ?: "expense"))
  }
  var title by remember(initialTx) { mutableStateOf(initialTx?.title ?: "") }
  var amount by remember(initialTx) {
    mutableStateOf(initialTx?.amount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "")
  }
  var category by remember(initialTx) { mutableStateOf(initialTx?.category ?: "food") }
  var date by remember(initialTx) { mutableStateOf(initialTx?.date ?: FormatUtils.currentIsoDate()) }
  var note by remember(initialTx) { mutableStateOf(initialTx?.note ?: "") }
  var errorMsg by remember(initialTx) { mutableStateOf<String?>(null) }
  var showDatePicker by remember { mutableStateOf(false) }

  val availableCategories = remember(type) { PulseCategories.forType(type) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 24.dp)
        .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(if (initialTx != null) R.string.edit_transaction else R.string.new_transaction),
          style = MaterialTheme.typography.titleLarge,
        )
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close modal")
        }
      }

      // Expense / Income Segmented Toggle
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(pulseColors.pillBackground)
          .padding(4.dp),
      ) {
        TransactionType.entries.forEach { t ->
          val selected = type == t
          Box(
            modifier = Modifier
              .weight(1f)
              .minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(12.dp))
              .background(
                if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
              )
              .clickable {
                type = t
                val valid = PulseCategories.forType(t)
                if (valid.none { it.id == category }) {
                  category = valid.first().id
                }
              }
              .testTag("type_toggle_${t.value}"),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = t.value.replaceFirstChar { it.uppercase() },
              style = MaterialTheme.typography.labelLarge,
              color = if (selected) MaterialTheme.colorScheme.onSurface else pulseColors.mutedText,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
          }
        }
      }

      if (errorMsg != null) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = pulseColors.expense.copy(alpha = 0.12f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = errorMsg ?: "",
            color = pulseColors.expense,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          )
        }
      }

      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Title") },
        placeholder = { Text("e.g., Organic Groceries") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("tx_title_input"),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = amount,
          onValueChange = { amount = it },
          label = { Text("Amount") },
          placeholder = { Text("0.00") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .weight(1f)
            .testTag("tx_amount_input"),
        )

        OutlinedButton(
          onClick = { showDatePicker = true },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .padding(top = 6.dp)
            .testTag("tx_date_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.CalendarToday,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = date,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
          )
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "Category",
          style = MaterialTheme.typography.labelMedium,
          color = pulseColors.mutedText,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          availableCategories.forEach { cat ->
            val selected = category == cat.id
            FilterChip(
              selected = selected,
              onClick = { category = cat.id },
              label = { Text(cat.label) },
              leadingIcon = {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(cat.colorHex)),
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              ),
              modifier = Modifier.testTag("tx_cat_${cat.id}"),
            )
          }
        }
      }

      OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        label = { Text("Note (optional)") },
        placeholder = { Text("Details or tags...") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("tx_note_input"),
      )

      Button(
        onClick = {
          scope.launch {
            errorMsg = null
            val res = onSave(
              initialTx?.id,
              title,
              amount,
              type.value,
              category,
              date,
              note,
            )
            if (res.isSuccess) {
              onDismiss()
            } else {
              errorMsg = res.exceptionOrNull()?.message ?: "Please check your inputs."
            }
          }
        },
        enabled = !isSaving,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .minimumInteractiveComponentSize()
          .testTag("save_tx_button"),
      ) {
        Text(
          text = if (isSaving) "Saving..." else stringResource(R.string.save_transaction),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }

  if (showDatePicker) {
    val initialMillis = remember(date) {
      runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
          timeZone = TimeZone.getTimeZone("UTC")
        }.parse(date)?.time
      }.getOrNull()
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { millis ->
              val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
              }
              date = fmt.format(Date(millis))
            }
            showDatePicker = false
          },
        ) {
          Text("OK")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) {
          Text("Cancel")
        }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }
}
