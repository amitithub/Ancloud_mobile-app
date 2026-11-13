package com.example.data

import android.content.Context
import com.example.R
import com.example.data.model.BudgetEntity
import com.example.data.model.Expense
import com.example.data.model.PulseCategories
import com.example.data.model.TransactionEntity
import com.example.domain.AnalyticsEngine
import com.example.domain.ValidatedTransaction
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Firestore repository in the `data` package providing CRUD operations for transaction
 * ([TransactionEntity] / [Expense]) and budget ([BudgetEntity]) objects.
 *
 * Supports constructor dependency injection (`FirebaseRepository(db, auth)`) as well as a
 * thread-safe singleton (`FirebaseRepository.getInstance(context)`).
 */
class FirebaseRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {

  constructor(context: Context) : this(
    db = FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    auth = Firebase.auth,
  )

  fun currentUserId(): String? = auth.currentUser?.uid

  fun isAuthenticated(): Boolean = auth.currentUser != null

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  private fun unauthenticatedFailure(): Result<Nothing> =
    Result.failure(IllegalStateException("User must be signed in with Google before accessing Firestore."))

  private fun transactionsCollection(userId: String) =
    db.collection("users").document(userId).collection("transactions")

  private fun budgetsCollection(userId: String) =
    db.collection("users").document(userId).collection("budgets")

  // --- TRANSACTION READ ---

  fun observeTransactions(userId: String): Flow<List<TransactionEntity>> {
    val path = "users/$userId/transactions"
    return transactionsCollection(userId)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          TransactionEntity::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        ).sortedByDescending { it.date }
      }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
          handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
        throw error
      }
  }

  suspend fun getTransactions(userId: String): Result<List<TransactionEntity>> {
    val path = "users/$userId/transactions"
    return try {
      val snapshot = transactionsCollection(userId).get().await()
      val items = snapshot.toObjects(
        TransactionEntity::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
      ).sortedByDescending { it.date }
      Result.success(items)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, path)
      Result.failure(e)
    }
  }

  suspend fun getTransactionById(userId: String, transactionId: String): Result<TransactionEntity?> {
    val docRef = transactionsCollection(userId).document(transactionId)
    return try {
      val snapshot = docRef.get().await()
      Result.success(
        snapshot.toObject(
          TransactionEntity::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      Result.failure(e)
    }
  }

  // --- TRANSACTION CREATE ---

  suspend fun createTransaction(transaction: TransactionEntity): Result<String> {
    val uid = auth.currentUser?.uid ?: return unauthenticatedFailure()
    val docRef = if (transaction.id.isNotBlank()) {
      transactionsCollection(uid).document(transaction.id)
    } else {
      transactionsCollection(uid).document()
    }
    val payload = mapOf(
      "userId" to uid,
      "title" to transaction.title.trim(),
      "amount" to AnalyticsEngine.roundCurrency(transaction.amount),
      "type" to transaction.type,
      "category" to transaction.category,
      "date" to transaction.date.trim(),
      "note" to transaction.note.trim().take(250),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    return try {
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun addTransaction(draft: ValidatedTransaction): Result<String> {
    val uid = auth.currentUser?.uid ?: return unauthenticatedFailure()
    return createTransaction(
      TransactionEntity(
        userId = uid,
        title = draft.title,
        amount = draft.amount,
        type = draft.type,
        category = draft.category,
        date = draft.date,
        note = draft.note,
      )
    )
  }

  // --- TRANSACTION UPDATE ---

  suspend fun updateTransaction(transaction: TransactionEntity): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return unauthenticatedFailure()
    if (transaction.id.isBlank()) {
      return Result.failure(IllegalArgumentException("Transaction ID must not be blank for update"))
    }
    val docRef = transactionsCollection(uid).document(transaction.id)
    val updates = mapOf(
      "title" to transaction.title.trim(),
      "amount" to AnalyticsEngine.roundCurrency(transaction.amount),
      "type" to transaction.type,
      "category" to transaction.category,
      "date" to transaction.date.trim(),
      "note" to transaction.note.trim().take(250),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    return try {
      docRef.update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateTransaction(transactionId: String, draft: ValidatedTransaction): Result<Unit> {
    return updateTransaction(
      TransactionEntity(
        id = transactionId,
        title = draft.title,
        amount = draft.amount,
        type = draft.type,
        category = draft.category,
        date = draft.date,
        note = draft.note,
      )
    )
  }

  // --- TRANSACTION DELETE ---

  suspend fun deleteTransaction(transactionId: String): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return unauthenticatedFailure()
    if (transactionId.isBlank()) {
      return Result.failure(IllegalArgumentException("Transaction ID must not be blank for delete"))
    }
    val docRef = transactionsCollection(uid).document(transactionId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  // --- EXPENSE CRUD (WITH AUTH CONTEXT) ---

  fun observeExpenses(): Flow<List<Expense>> = flow {
    emitAll(observeTransactions(requireUserId()))
  }

  fun observeExpenses(userId: String): Flow<List<Expense>> = observeTransactions(userId)

  suspend fun getExpenses(): Result<List<Expense>> {
    val uid = auth.currentUser?.uid ?: return unauthenticatedFailure()
    return getTransactions(uid)
  }

  suspend fun getExpenses(userId: String): Result<List<Expense>> = getTransactions(userId)

  suspend fun getExpenseById(expenseId: String): Result<Expense?> {
    val uid = auth.currentUser?.uid ?: return unauthenticatedFailure()
    if (expenseId.isBlank()) {
      return Result.failure(IllegalArgumentException("Expense ID must not be blank"))
    }
    return getTransactionById(uid, expenseId)
  }

  suspend fun getExpenseById(userId: String, expenseId: String): Result<Expense?> =
    getTransactionById(userId, expenseId)

  suspend fun createExpense(expense: Expense): Result<String> = createTransaction(expense)

  suspend fun addExpense(draft: ValidatedTransaction): Result<String> = addTransaction(draft)

  suspend fun updateExpense(expense: Expense): Result<Unit> = updateTransaction(expense)

  suspend fun updateExpense(expenseId: String, draft: ValidatedTransaction): Result<Unit> =
    updateTransaction(expenseId, draft)

  suspend fun deleteExpense(expenseId: String): Result<Unit> = deleteTransaction(expenseId)

  // --- BUDGETS ---

  fun observeBudgets(userId: String): Flow<List<BudgetEntity>> {
    val path = "users/$userId/budgets"
    return budgetsCollection(userId)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          BudgetEntity::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
          handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
        throw error
      }
  }

  suspend fun setBudget(category: String, limit: Double): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return unauthenticatedFailure()
    if (category !in PulseCategories.validExpenseIds) {
      return Result.failure(IllegalArgumentException("Invalid expense category: $category"))
    }
    if (!limit.isFinite() || limit < 0.0) {
      return Result.failure(IllegalArgumentException("Budget limit must be zero or positive"))
    }
    val rounded = AnalyticsEngine.roundCurrency(limit)
    val docRef = budgetsCollection(uid).document(category)
    return try {
      val existing = docRef.get().await()
      if (existing.exists()) {
        docRef.update(
          mapOf(
            "limit" to rounded,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } else {
        docRef.set(
          mapOf(
            "userId" to uid,
            "category" to category,
            "limit" to rounded,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  companion object {
    @Volatile
    private var instance: FirebaseRepository? = null

    fun getInstance(context: Context): FirebaseRepository {
      return instance ?: synchronized(this) {
        instance ?: FirebaseRepository(context.applicationContext).also { instance = it }
      }
    }
  }
}
