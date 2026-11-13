package com.example.domain

import com.example.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsEngineTest {

  @Test
  fun roundCurrency_avoidsFloatingPointDrift() {
    assertEquals(0.3, AnalyticsEngine.roundCurrency(0.1 + 0.2), 0.0)
    assertEquals(20.0, AnalyticsEngine.roundCurrency(19.999), 0.0)
  }

  @Test
  fun validateTransactionInput_rejectsInvalidAndAcceptsValid() {
    val invalid = AnalyticsEngine.validateTransactionInput(
      title = "",
      amountText = "-50",
      type = "transfer",
      category = "unknown",
      date = "bad-date",
      note = "",
    )
    assertTrue(invalid is ValidationResult.Invalid)
    assertTrue((invalid as ValidationResult.Invalid).errors.size >= 4)

    val valid = AnalyticsEngine.validateTransactionInput(
      title = "  Coffee  ",
      amountText = "145.678",
      type = "expense",
      category = "food",
      date = "2026-11-10",
      note = "  Morning latte ",
    )
    assertTrue(valid is ValidationResult.Valid)
    val data = (valid as ValidationResult.Valid).data
    assertEquals("Coffee", data.title)
    assertEquals(145.68, data.amount, 0.0)
    assertEquals("Morning latte", data.note)
  }

  @Test
  fun computeSummary_computesMonthlyKpisAndBudgetStatuses() {
    val txs = listOf(
      TransactionEntity(id = "1", title = "Salary", amount = 10000.0, type = "income", category = "salary", date = "2026-11-01"),
      TransactionEntity(id = "2", title = "Groceries", amount = 1600.0, type = "expense", category = "food", date = "2026-11-03"),
      TransactionEntity(id = "3", title = "Cab", amount = 1200.0, type = "expense", category = "transport", date = "2026-11-03"),
      TransactionEntity(id = "4", title = "Old Month", amount = 9000.0, type = "expense", category = "food", date = "2026-10-15"),
    )
    val budgets = mapOf("food" to 2000.0, "transport" to 1000.0)

    val summary = AnalyticsEngine.computeSummary(txs, budgets, "2026-11")
    assertEquals(10000.0, summary.totalIncome, 0.0)
    assertEquals(2800.0, summary.totalExpense, 0.0)
    assertEquals(7200.0, summary.netBalance, 0.0)
    assertEquals(72.0, summary.savingsRate, 0.0)

    val foodBudget = summary.budgetStatuses.first { it.category == "food" }
    val transportBudget = summary.budgetStatuses.first { it.category == "transport" }
    assertEquals(BudgetHealthStatus.WARNING, foodBudget.status)
    assertEquals(BudgetHealthStatus.EXCEEDED, transportBudget.status)
  }

  @Test
  fun transactionsToCsv_neutralizesFormulaInjectionAndEscapesCommas() {
    val csv = AnalyticsEngine.transactionsToCsv(
      listOf(
        TransactionEntity(
          id = "1",
          date = "2026-11-10",
          title = "=CMD()",
          type = "expense",
          category = "other",
          amount = 50.0,
          note = "a, b",
        )
      )
    )
    assertTrue(csv.contains("'=CMD()"))
    assertTrue(csv.contains("\"a, b\""))
  }
}
