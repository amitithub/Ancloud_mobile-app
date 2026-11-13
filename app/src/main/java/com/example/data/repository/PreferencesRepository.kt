package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.CurrencyCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pulsePrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "pulse_prefs")

data class UserPreferences(
  val darkMode: Boolean? = null, // null = follow system
  val currency: CurrencyCode = CurrencyCode.INR,
)

class PreferencesRepository(private val context: Context) {
  private object Keys {
    val DARK_MODE = stringPreferencesKey("dark_mode") // "dark", "light", "system"
    val CURRENCY = stringPreferencesKey("currency")
  }

  val preferences: Flow<UserPreferences> = context.pulsePrefsDataStore.data.map { prefs ->
    val darkMode = when (prefs[Keys.DARK_MODE]) {
      "dark" -> true
      "light" -> false
      else -> null
    }
    val currency = CurrencyCode.fromCode(prefs[Keys.CURRENCY] ?: CurrencyCode.INR.code)
    UserPreferences(darkMode = darkMode, currency = currency)
  }

  suspend fun setDarkMode(dark: Boolean) {
    context.pulsePrefsDataStore.edit { prefs ->
      prefs[Keys.DARK_MODE] = if (dark) "dark" else "light"
    }
  }

  suspend fun setCurrency(currency: CurrencyCode) {
    context.pulsePrefsDataStore.edit { prefs ->
      prefs[Keys.CURRENCY] = currency.code
    }
  }
}
