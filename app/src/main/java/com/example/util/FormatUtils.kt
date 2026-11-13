package com.example.util

import com.example.data.model.CurrencyCode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Currency
import java.util.Locale

object FormatUtils {

  fun formatCurrency(amount: Double, currency: CurrencyCode): String {
    val locale = Locale.forLanguageTag(currency.languageTag)
    val formatter = NumberFormat.getCurrencyInstance(locale).apply {
      this.currency = Currency.getInstance(currency.code)
      maximumFractionDigits = 2
      minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
    }
    return formatter.format(amount)
  }

  fun formatShortDate(isoDate: String): String {
    if (isoDate.length != 10) return isoDate
    return try {
      val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
      val parsed = parser.parse(isoDate) ?: return isoDate
      SimpleDateFormat("MMM d", Locale.US).format(parsed)
    } catch (_: Exception) {
      isoDate
    }
  }

  fun formatMonthLabel(monthPrefix: String): String {
    if (monthPrefix.isBlank()) return "All Time"
    return try {
      val parser = SimpleDateFormat("yyyy-MM", Locale.US)
      val parsed = parser.parse(monthPrefix) ?: return monthPrefix
      SimpleDateFormat("MMM yyyy", Locale.US).format(parsed)
    } catch (_: Exception) {
      monthPrefix
    }
  }

  fun currentMonthPrefix(): String {
    val cal = Calendar.getInstance()
    return String.format(
      Locale.US,
      "%04d-%02d",
      cal.get(Calendar.YEAR),
      cal.get(Calendar.MONTH) + 1,
    )
  }

  fun currentIsoDate(): String {
    val cal = Calendar.getInstance()
    return String.format(
      Locale.US,
      "%04d-%02d-%02d",
      cal.get(Calendar.YEAR),
      cal.get(Calendar.MONTH) + 1,
      cal.get(Calendar.DAY_OF_MONTH),
    )
  }

  fun stepMonth(monthPrefix: String, deltaMonths: Int): String {
    val base = if (monthPrefix.matches(Regex("^\\d{4}-\\d{2}$"))) monthPrefix else currentMonthPrefix()
    val parts = base.split("-")
    val cal = Calendar.getInstance().apply {
      set(Calendar.YEAR, parts[0].toInt())
      set(Calendar.MONTH, parts[1].toInt() - 1)
      set(Calendar.DAY_OF_MONTH, 1)
      add(Calendar.MONTH, deltaMonths)
    }
    return String.format(
      Locale.US,
      "%04d-%02d",
      cal.get(Calendar.YEAR),
      cal.get(Calendar.MONTH) + 1,
    )
  }
}
