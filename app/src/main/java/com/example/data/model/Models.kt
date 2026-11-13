package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TransactionType(val value: String) {
  EXPENSE("expense"),
  INCOME("income");

  companion object {
    fun fromValue(value: String): TransactionType =
      if (value.equals("income", ignoreCase = true)) INCOME else EXPENSE
  }
}

enum class CategoryScope {
  EXPENSE,
  INCOME,
  BOTH,
}

data class CategoryMeta(
  val id: String,
  val label: String,
  val colorHex: Long,
  val scope: CategoryScope,
) {
  val color: Color
    get() = Color(colorHex)
}

object PulseCategories {
  val all: List<CategoryMeta> = listOf(
    CategoryMeta("food", "Food & Dining", 0xFFF97316, CategoryScope.EXPENSE),
    CategoryMeta("housing", "Housing & Rent", 0xFF6366F1, CategoryScope.EXPENSE),
    CategoryMeta("transport", "Transport", 0xFF06B6D4, CategoryScope.EXPENSE),
    CategoryMeta("utilities", "Bills & Utilities", 0xFFEAB308, CategoryScope.EXPENSE),
    CategoryMeta("shopping", "Shopping", 0xFFEC4899, CategoryScope.EXPENSE),
    CategoryMeta("entertainment", "Entertainment", 0xFFA855F7, CategoryScope.EXPENSE),
    CategoryMeta("health", "Health & Fitness", 0xFF10B981, CategoryScope.EXPENSE),
    CategoryMeta("salary", "Salary", 0xFF22C55E, CategoryScope.INCOME),
    CategoryMeta("freelance", "Freelance", 0xFF14B8A6, CategoryScope.INCOME),
    CategoryMeta("other", "Other", 0xFF64748B, CategoryScope.BOTH),
  )

  val expenseCategories: List<CategoryMeta> =
    all.filter { it.scope != CategoryScope.INCOME }

  val validIds: Set<String> = all.map { it.id }.toSet()
  val validExpenseIds: Set<String> = expenseCategories.map { it.id }.toSet()

  private val byId: Map<String, CategoryMeta> = all.associateBy { it.id }

  fun find(id: String): CategoryMeta =
    byId[id] ?: CategoryMeta(id, id.replaceFirstChar { it.uppercase() }, 0xFF64748B, CategoryScope.BOTH)

  fun forType(type: TransactionType): List<CategoryMeta> =
    when (type) {
      TransactionType.EXPENSE -> all.filter { it.scope == CategoryScope.EXPENSE || it.scope == CategoryScope.BOTH }
      TransactionType.INCOME -> all.filter { it.scope == CategoryScope.INCOME || it.scope == CategoryScope.BOTH }
    }
}

enum class CurrencyCode(val code: String, val languageTag: String, val symbol: String) {
  INR("INR", "en-IN", "₹"),
  USD("USD", "en-US", "$"),
  EUR("EUR", "de-DE", "€"),
  GBP("GBP", "en-GB", "£");

  companion object {
    fun fromCode(code: String): CurrencyCode =
      entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: INR
  }
}

@IgnoreExtraProperties
data class Expense(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val amount: Double = 0.0,
  val type: String = "expense",
  val category: String = "food",
  val date: String = "",
  val note: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

typealias TransactionEntity = Expense

@IgnoreExtraProperties
data class BudgetEntity(
  @DocumentId val id: String = "",
  val userId: String = "",
  val category: String = "",
  val limit: Double = 0.0,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)
