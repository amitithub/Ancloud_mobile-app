package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.TransactionEntity
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseRepositoryRuleTest : FirestoreEmulatorTestBase() {

  private fun sampleTransaction(
    title: String = "Organic Groceries",
    amount: Double = 3450.5,
  ) = TransactionEntity(
    title = title,
    amount = amount,
    type = "expense",
    category = "food",
    date = "2026-11-12",
    note = "Weekly stockup",
  )

  @Test
  fun createGetAndObserveTransaction_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = FirebaseRepository(firestore, auth)

    val id = withTimeout(DEFAULT_TIMEOUT_MS) { repo.createTransaction(sampleTransaction()).getOrThrow() }
    assertTrue(id.isNotBlank())

    val listOneShot = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getTransactions(uid).getOrThrow() }
    assertTrue(listOneShot.any { it.id == id })

    val observed = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeTransactions(uid).first { items -> items.any { it.id == id } }
    }
    val item = observed.first { it.id == id }
    assertEquals("Organic Groceries", item.title)
    assertEquals(3450.5, item.amount, 0.001)
    assertEquals(uid, item.userId)
  }

  @Test
  fun updateAndDeleteTransaction_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = FirebaseRepository(firestore, auth)

    val id = withTimeout(DEFAULT_TIMEOUT_MS) { repo.createTransaction(sampleTransaction()).getOrThrow() }
    val updated = sampleTransaction(title = "Team Dinner", amount = 1850.0).copy(id = id)
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.updateTransaction(updated).getOrThrow() }

    val fetched = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getTransactionById(uid, id).getOrThrow() }
    assertNotNull(fetched)
    assertEquals("Team Dinner", fetched?.title)
    assertEquals(1850.0, fetched?.amount ?: 0.0, 0.001)

    withTimeout(DEFAULT_TIMEOUT_MS) { repo.deleteTransaction(id).getOrThrow() }
    val afterDelete = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getTransactionById(uid, id).getOrThrow() }
    assertNull(afterDelete)
  }

  @Test
  fun setBudget_createAndUpdate_persistsLimit() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = FirebaseRepository(firestore, auth)

    withTimeout(DEFAULT_TIMEOUT_MS) { repo.setBudget("food", 8000.0).getOrThrow() }
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.setBudget("food", 9500.0).getOrThrow() }

    val budgets = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeBudgets(uid).first { list -> list.any { it.category == "food" && it.limit == 9500.0 } }
    }
    assertEquals(9500.0, budgets.first { it.category == "food" }.limit, 0.001)
  }

  @Test
  fun getTransactionById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = FirebaseRepository(firestore, auth)
    val txId = withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.createTransaction(sampleTransaction()).getOrThrow() }

    signInTestUser(BOB_EMAIL)
    val bobRepo = FirebaseRepository(firestore, auth)
    val result = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getTransactionById(aliceUid, txId) }
    assertTrue(result.isFailure)
    val fsErr = result.exceptionOrNull()?.unwrapFirestoreException()
    assertNotNull(fsErr)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
  }

  @Test
  fun observeTransactions_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = FirebaseRepository(firestore, auth)

    var thrown: Throwable? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeTransactions("some_user_id").first() }
    } catch (t: Throwable) {
      thrown = t
    }
    assertNotNull(thrown)
    val fsErr = thrown?.unwrapFirestoreException()
    assertNotNull("Expected nested FirebaseFirestoreException, got $thrown", fsErr)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
  }

  @Test
  fun expenseCrud_usingAuthContext_createsReadsUpdatesDeletes() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = FirebaseRepository(firestore, auth)
    assertTrue(repo.isAuthenticated())
    assertEquals(uid, repo.currentUserId())

    val expenseId = withTimeout(DEFAULT_TIMEOUT_MS) { repo.createExpense(sampleTransaction(title = "Metro Card")).getOrThrow() }
    val fetched = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getExpenseById(expenseId).getOrThrow() }
    assertNotNull(fetched)
    assertEquals("Metro Card", fetched?.title)

    val list = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getExpenses().getOrThrow() }
    assertTrue(list.any { it.id == expenseId })

    val observed = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeExpenses().first { items -> items.any { it.id == expenseId } }
    }
    assertTrue(observed.any { it.id == expenseId })

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updateExpense(sampleTransaction(title = "Metro Pass", amount = 1200.0).copy(id = expenseId)).getOrThrow()
    }
    val updated = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getExpenseById(expenseId).getOrThrow() }
    assertEquals("Metro Pass", updated?.title)

    withTimeout(DEFAULT_TIMEOUT_MS) { repo.deleteExpense(expenseId).getOrThrow() }
    val afterDelete = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getExpenseById(expenseId).getOrThrow() }
    assertNull(afterDelete)
  }

  @Test
  fun createAndReadExpenses_unauthenticated_returnsFailure() = runBlocking {
    auth.signOut()
    val repo = FirebaseRepository(firestore, auth)
    assertNull(repo.currentUserId())
    assertTrue(!repo.isAuthenticated())

    val createRes = repo.createExpense(sampleTransaction())
    val getRes = repo.getExpenses()
    assertTrue(createRes.isFailure)
    assertTrue(getRes.isFailure)
    assertTrue(createRes.exceptionOrNull() is IllegalStateException)
    assertTrue(getRes.exceptionOrNull() is IllegalStateException)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_pulse@test.com"
    const val BOB_EMAIL = "bob_pulse@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
