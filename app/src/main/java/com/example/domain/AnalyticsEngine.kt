package com.example.domain

import com.example.data.model.PulseCategories
import com.example.data.model.TransactionEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

data class ValidatedTransaction(
  val title: String,
  val amount: Double,
  val type: String,
  val category: String,
  val date: String,
  val note: String,
)

sealed interface ValidationResult {
  data class Valid(val data: ValidatedTransaction) : ValidationResult
  data class Invalid(val errors: List<String>) : ValidationResult
}

data class CategoryBreakdownItem(
  val category: String,
  val label: String,
  val colorHex: Long,
  val amount: Double,
  val percentage: Double,
  val budgetLimit: Double,
  val budgetUtilization: Double?,
)

enum class BudgetHealthStatus {
  UNCONFIGURED,
  HEALTHY,
  WARNING,
  EXCEEDED,
}

data class BudgetStatusItem(
  val category: String,
  val label: String,
  val colorHex: Long,
  val spent: Double,
  val limit: Double,
  val utilization: Double,
  val status: BudgetHealthStatus,
)

data class DailyTrendPoint(
  val date: String,
  val income: Double,
  val expense: Double,
)

data class MonthlySummary(
  val totalIncome: Double = 0.0,
  val totalExpense: Double = 0.0,
  val netBalance: Double = 0.0,
  val savingsRate: Double = 0.0,
  val transactionCount: Int = 0,
  val categoryBreakdown: List<CategoryBreakdownItem> = emptyList(),
  val budgetStatuses: List<BudgetStatusItem> = emptyList(),
  val dailyTrend: List<DailyTrendPoint> = emptyList(),
)

object AnalyticsEngine {
  private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")

  fun roundCurrency(value: Double): Double {
    if (!value.isFinite()) return 0.0
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()
  }

  fun isValidIsoDate(date: String): Boolean {
    if (!ISO_DATE_REGEX.matches(date)) return false
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    return try {
      sdf.parse(date) != null
    } catch (_: ParseException) {
      false
    }
  }

  fun validateTransactionInput(
    title: String,
    amountText: String,
    type: String,
    category: String,
    date: String,
    note: String,
  ): ValidationResult {
    val errors = mutableListOf<String>()
    val cleanTitle = title.trim()
    if (cleanTitle.isEmpty() || cleanTitle.length > 100) {
      errors.add("Title is required and must be under 100 characters.")
    }

    val amount = amountText.trim().toDoubleOrNull()
    if (amount == null || !amount.isFinite() || amount <= 0.0 || amount > 100_000_000.0) {
      errors.add("Amount must be a positive number.")
    }

    if (type != "expense" && type != "income") {
      errors.add("Type must be expense or income.")
    }

    if (category !in PulseCategories.validIds) {
      errors.add("Please select a valid category.")
    }

    if (!isValidIsoDate(date.trim())) {
      errors.add("Date must be a valid YYYY-MM-DD value.")
    }

    val cleanNote = note.trim().take(250)

    return if (errors.isNotEmpty()) {
      ValidationResult.Invalid(errors)
    } else {
      ValidationResult.Valid(
        ValidatedTransaction(
          title = cleanTitle,
          amount = roundCurrency(amount!!),
          type = type,
          category = category,
          date = date.trim(),
          note = cleanNote,
        )
      )
    }
  }

  fun computeSummary(
    transactions: List<TransactionEntity>,
    budgets: Map<String, Double>,
    monthPrefix: String = "",
  ): MonthlySummary {
    val scoped = if (monthPrefix.isNotBlank()) {
      transactions.filter { it.date.startsWith(monthPrefix) }
    } else {
      transactions
    }

    var totalIncome = 0.0
    var totalExpense = 0.0
    val categoryTotals = mutableMapOf<String, Double>()
    val dailyMap = mutableMapOf<String, DailyTrendPoint>()

    for (tx in scoped) {
      val currentDay = dailyMap[tx.date] ?: DailyTrendPoint(tx.date, 0.0, 0.0)
      if (tx.type == "income") {
        totalIncome = roundCurrency(totalIncome + tx.amount)
        dailyMap[tx.date] = currentDay.copy(income = roundCurrency(currentDay.income + tx.amount))
      } else {
        totalExpense = roundCurrency(totalExpense + tx.amount)
        dailyMap[tx.date] = currentDay.copy(expense = roundCurrency(currentDay.expense + tx.amount))
        categoryTotals[tx.category] = roundCurrency((categoryTotals[tx.category] ?: 0.0) + tx.amount)
      }
    }

    val netBalance = roundCurrency(totalIncome - totalExpense)
    val savingsRate = if (totalIncome > 0.0) {
      kotlin.math.round(((totalIncome - totalExpense) / totalIncome) * 1000.0) / 10.0
    } else {
      0.0
    }

    val categoryBreakdown = categoryTotals.entries
      .map { (catId, amount) ->
        val meta = PulseCategories.find(catId)
        val limit = budgets[catId] ?: 0.0
        val percentage = if (totalExpense > 0.0) {
          kotlin.math.round((amount / totalExpense) * 1000.0) / 10.0
        } else {
          0.0
        }
        val utilization = if (limit > 0.0) {
          kotlin.math.round((amount / limit) * 1000.0) / 10.0
        } else {
          null
        }
        CategoryBreakdownItem(
          category = catId,
          label = meta.label,
          colorHex = meta.colorHex,
          amount = amount,
          percentage = percentage,
          budgetLimit = limit,
          budgetUtilization = utilization,
        )
      }
      .sortedByDescending { it.amount }

    val budgetStatuses = PulseCategories.expenseCategories.map { cat ->
      val spent = categoryTotals[cat.id] ?: 0.0
      val limit = budgets[cat.id] ?: 0.0
      val utilization = if (limit > 0.0) {
        kotlin.math.round((spent / limit) * 1000.0) / 10.0
      } else {
        0.0
      }
      val status = when {
        limit <= 0.0 -> BudgetHealthStatus.UNCONFIGURED
        utilization >= 100.0 -> BudgetHealthStatus.EXCEEDED
        utilization >= 75.0 -> BudgetHealthStatus.WARNING
        else -> BudgetHealthStatus.HEALTHY
      }
      BudgetStatusItem(
        category = cat.id,
        label = cat.label,
        colorHex = cat.colorHex,
        spent = spent,
        limit = limit,
        utilization = utilization,
        status = status,
      )
    }

    val dailyTrend = dailyMap.values.sortedBy { it.date }

    return MonthlySummary(
      totalIncome = totalIncome,
      totalExpense = totalExpense,
      netBalance = netBalance,
      savingsRate = savingsRate,
      transactionCount = scoped.size,
      categoryBreakdown = categoryBreakdown,
      budgetStatuses = budgetStatuses,
      dailyTrend = dailyTrend,
    )
  }

  private fun escapeCsvCell(value: String): String {
    var str = value
    if (str.isNotEmpty() && str[0] in charArrayOf('=', '+', '-', '@')) {
      str = "'$str"
    }
    return if (str.any { it == '"' || it == ',' || it == '\n' || it == '\r' }) {
      "\"${str.replace("\"", "\"\"")}\""
    } else {
      str
    }
  }

  fun transactionsToCsv(transactions: List<TransactionEntity>): String {
    val headers = listOf("ID", "Date", "Title", "Type", "Category", "Amount", "Note")
    val rows = transactions.map { tx ->
      listOf(
        tx.id,
        tx.date,
        tx.title,
        tx.type,
        tx.category,
        String.format(Locale.US, "%.2f", tx.amount),
        tx.note,
      ).joinToString(",") { escapeCsvCell(it) }
    }
    return (listOf(headers.joinToString(",")) + rows).joinToString("\n")
  }
}
