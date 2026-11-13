package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FirebaseRepository
import com.example.data.model.BudgetEntity
import com.example.data.model.CurrencyCode
import com.example.data.model.Expense
import com.example.data.model.PulseCategories
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.UserPreferences
import com.example.domain.AnalyticsEngine
import com.example.domain.MonthlySummary
import com.example.domain.ValidationResult
import com.example.util.FormatUtils
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PulseTab {
  OVERVIEW,
  ACTIVITY,
  ANALYTICS,
  BUDGETS,
}

enum class TransactionFilterType {
  ALL,
  EXPENSE,
  INCOME,
}

sealed interface DataStatus {
  data object Loading : DataStatus
  data object Ready : DataStatus
  data class Error(val message: String) : DataStatus
}

data class PulseUiState(
  val selectedTab: PulseTab = PulseTab.OVERVIEW,
  val selectedMonth: String = FormatUtils.currentMonthPrefix(), // "" means All Time
  val searchQuery: String = "",
  val filterType: TransactionFilterType = TransactionFilterType.ALL,
  val filterCategory: String = "all",
  val currency: CurrencyCode = CurrencyCode.INR,
  val darkMode: Boolean? = null,
  val dataStatus: DataStatus = DataStatus.Loading,
  val filteredTransactions: List<Expense> = emptyList(),
  val allScopedTransactions: List<Expense> = emptyList(),
  val summary: MonthlySummary = MonthlySummary(),
  val isMutating: Boolean = false,
  val snackbarMessage: String? = null,
)

private data class FilterControls(
  val selectedTab: PulseTab = PulseTab.OVERVIEW,
  val selectedMonth: String = FormatUtils.currentMonthPrefix(),
  val searchQuery: String = "",
  val filterType: TransactionFilterType = TransactionFilterType.ALL,
  val filterCategory: String = "all",
  val isMutating: Boolean = false,
  val snackbarMessage: String? = null,
)

private sealed interface StreamState<out T> {
  data object Loading : StreamState<Nothing>
  data class Ready<T>(val data: T) : StreamState<T>
  data class Error(val message: String) : StreamState<Nothing>
}

class PulseViewModel(
  private val firebaseRepository: FirebaseRepository,
  private val preferencesRepository: PreferencesRepository,
  private val currentUserId: String,
) : ViewModel() {

  private val controls = MutableStateFlow(FilterControls())

  private val transactionsStream: StateFlow<StreamState<List<Expense>>> =
    firebaseRepository.observeExpenses(currentUserId)
      .map<List<Expense>, StreamState<List<Expense>>> { StreamState.Ready(it) }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing transactions", rootError)
        emit(StreamState.Error(rootError.message ?: "Failed to load transactions"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = StreamState.Loading,
      )

  private val budgetsStream: StateFlow<StreamState<List<BudgetEntity>>> =
    firebaseRepository.observeBudgets(currentUserId)
      .map<List<BudgetEntity>, StreamState<List<BudgetEntity>>> { StreamState.Ready(it) }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing budgets", rootError)
        emit(StreamState.Error(rootError.message ?: "Failed to load budgets"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = StreamState.Loading,
      )

  val uiState: StateFlow<PulseUiState> = combine(
    controls,
    transactionsStream,
    budgetsStream,
    preferencesRepository.preferences,
  ) { ctrl, txState, budgetState, prefs ->
    buildUiState(ctrl, txState, budgetState, prefs)
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
    initialValue = PulseUiState(),
  )

  private fun buildUiState(
    ctrl: FilterControls,
    txState: StreamState<List<Expense>>,
    budgetState: StreamState<List<BudgetEntity>>,
    prefs: UserPreferences,
  ): PulseUiState {
    val status = when {
      txState is StreamState.Error -> DataStatus.Error(txState.message)
      budgetState is StreamState.Error -> DataStatus.Error(budgetState.message)
      txState is StreamState.Loading || budgetState is StreamState.Loading -> DataStatus.Loading
      else -> DataStatus.Ready
    }

    val allTx = (txState as? StreamState.Ready)?.data ?: emptyList()
    val budgetsMap = ((budgetState as? StreamState.Ready)?.data ?: emptyList())
      .associate { it.category to it.limit }

    val monthScoped = if (ctrl.selectedMonth.isNotBlank()) {
      allTx.filter { it.date.startsWith(ctrl.selectedMonth) }
    } else {
      allTx
    }

    val query = ctrl.searchQuery.trim().lowercase()
    val filtered = monthScoped.filter { tx ->
      val typeMatches = when (ctrl.filterType) {
        TransactionFilterType.ALL -> true
        TransactionFilterType.EXPENSE -> tx.type == "expense"
        TransactionFilterType.INCOME -> tx.type == "income"
      }
      val catMatches = ctrl.filterCategory == "all" || tx.category == ctrl.filterCategory
      val searchMatches = if (query.isEmpty()) {
        true
      } else {
        val catLabel = PulseCategories.find(tx.category).label.lowercase()
        tx.title.lowercase().contains(query) ||
          tx.note.lowercase().contains(query) ||
          catLabel.contains(query)
      }
      typeMatches && catMatches && searchMatches
    }.sortedByDescending { it.date }

    val summary = AnalyticsEngine.computeSummary(
      transactions = allTx,
      budgets = budgetsMap,
      monthPrefix = ctrl.selectedMonth,
    )

    return PulseUiState(
      selectedTab = ctrl.selectedTab,
      selectedMonth = ctrl.selectedMonth,
      searchQuery = ctrl.searchQuery,
      filterType = ctrl.filterType,
      filterCategory = ctrl.filterCategory,
      currency = prefs.currency,
      darkMode = prefs.darkMode,
      dataStatus = status,
      filteredTransactions = filtered,
      allScopedTransactions = monthScoped,
      summary = summary,
      isMutating = ctrl.isMutating,
      snackbarMessage = ctrl.snackbarMessage,
    )
  }

  fun selectTab(tab: PulseTab) {
    controls.value = controls.value.copy(selectedTab = tab)
  }

  fun stepMonth(delta: Int) {
    val current = controls.value.selectedMonth
    val next = FormatUtils.stepMonth(current, delta)
    controls.value = controls.value.copy(selectedMonth = next)
  }

  fun toggleAllTimeMonth() {
    val current = controls.value.selectedMonth
    val next = if (current.isBlank()) FormatUtils.currentMonthPrefix() else ""
    controls.value = controls.value.copy(selectedMonth = next)
  }

  fun setSearchQuery(query: String) {
    controls.value = controls.value.copy(searchQuery = query)
  }

  fun setFilterType(type: TransactionFilterType) {
    controls.value = controls.value.copy(filterType = type)
  }

  fun setFilterCategory(category: String) {
    controls.value = controls.value.copy(filterCategory = category)
  }

  fun clearSnackbar() {
    controls.value = controls.value.copy(snackbarMessage = null)
  }

  fun setCurrency(currency: CurrencyCode) {
    viewModelScope.launch {
      preferencesRepository.setCurrency(currency)
    }
  }

  fun toggleDarkMode(currentlyDark: Boolean) {
    viewModelScope.launch {
      preferencesRepository.setDarkMode(!currentlyDark)
    }
  }

  suspend fun saveTransaction(
    editingId: String?,
    title: String,
    amountText: String,
    type: String,
    category: String,
    date: String,
    note: String,
  ): Result<Unit> {
    return when (val validation = AnalyticsEngine.validateTransactionInput(title, amountText, type, category, date, note)) {
      is ValidationResult.Invalid -> Result.failure(IllegalArgumentException(validation.errors.first()))
      is ValidationResult.Valid -> {
        controls.value = controls.value.copy(isMutating = true)
        val result = if (editingId.isNullOrBlank()) {
          firebaseRepository.addExpense(validation.data).map { }
        } else {
          firebaseRepository.updateExpense(editingId, validation.data)
        }
        controls.value = controls.value.copy(
          isMutating = false,
          snackbarMessage = if (result.isSuccess) {
            if (editingId.isNullOrBlank()) "Transaction added" else "Transaction updated"
          } else {
            result.exceptionOrNull()?.localizedMessage ?: "Failed to save transaction"
          },
        )
        result
      }
    }
  }

  fun deleteTransaction(id: String) {
    viewModelScope.launch {
      controls.value = controls.value.copy(isMutating = true)
      val res = firebaseRepository.deleteExpense(id)
      controls.value = controls.value.copy(
        isMutating = false,
        snackbarMessage = if (res.isSuccess) "Transaction deleted" else "Could not delete transaction",
      )
    }
  }

  fun saveBudget(category: String, limit: Double) {
    viewModelScope.launch {
      controls.value = controls.value.copy(isMutating = true)
      val res = firebaseRepository.setBudget(category, limit)
      controls.value = controls.value.copy(
        isMutating = false,
        snackbarMessage = if (res.isSuccess) "Budget updated" else "Failed to update budget",
      )
    }
  }

  fun applySuggestedBudgets() {
    val suggested = mapOf(
      "food" to 8000.0,
      "housing" to 18000.0,
      "transport" to 3500.0,
      "utilities" to 3000.0,
      "shopping" to 5000.0,
      "entertainment" to 2500.0,
      "health" to 2500.0,
      "other" to 2000.0,
    )
    viewModelScope.launch {
      controls.value = controls.value.copy(isMutating = true)
      var allOk = true
      for ((cat, limit) in suggested) {
        if (firebaseRepository.setBudget(cat, limit).isFailure) {
          allOk = false
        }
      }
      controls.value = controls.value.copy(
        isMutating = false,
        snackbarMessage = if (allOk) "Suggested monthly budgets applied" else "Some budgets failed to save",
      )
    }
  }

  fun buildCsvExport(): String {
    return AnalyticsEngine.transactionsToCsv(uiState.value.filteredTransactions)
  }

  fun showMessage(msg: String) {
    controls.value = controls.value.copy(snackbarMessage = msg)
  }

  private companion object {
    const val TAG = "PulseVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
