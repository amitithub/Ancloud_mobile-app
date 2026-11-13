// Reactive UI controller for Expense Tracker by AN Cloud Web App
import {
  CATEGORIES,
  SUGGESTED_BUDGETS,
  categoriesForType,
  computeSummary,
  currentMonthPrefix,
  findCategory,
  formatCurrency,
  formatMonthLabel,
  stepMonth,
  todayIsoDate,
  transactionsToCsv,
  validateTransactionInput,
} from "./analytics.js";
import { renderCashflowChart, renderCategoryDonut } from "./charts.js";
import {
  createTransaction,
  deleteTransaction,
  formatAuthError,
  initAuthRedirectCheck,
  observeBudgets,
  observeTransactions,
  setBudget,
  signInWithGoogle,
  signOutUser,
  subscribeAuthState,
  updateTransaction,
} from "./firebase.js";

const PREFS_KEY = "ancloud_expense_web_prefs";

function loadPrefs() {
  try {
    const raw = localStorage.getItem(PREFS_KEY);
    if (!raw) return { theme: "light", currency: "INR" };
    const parsed = JSON.parse(raw);
    return {
      theme: parsed.theme === "dark" ? "dark" : "light",
      currency: ["INR", "USD", "EUR", "GBP"].includes(parsed.currency)
        ? parsed.currency
        : "INR",
    };
  } catch {
    return { theme: "light", currency: "INR" };
  }
}

function savePrefs(prefs) {
  try {
    localStorage.setItem(PREFS_KEY, JSON.stringify(prefs));
  } catch {
    // Ignore storage quota errors
  }
}

function escapeHtml(str) {
  return String(str ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

const initialPrefs = loadPrefs();

const state = {
  user: null,
  tab: "overview",
  month: currentMonthPrefix(),
  searchQuery: "",
  filterType: "all",
  filterCategory: "all",
  currency: initialPrefs.currency,
  theme: initialPrefs.theme,
  transactions: [],
  budgetsMap: {},
  syncError: "",
  editingTxId: null,
  modalType: "expense",
  editingBudgetCat: null,
};

let unsubTx = null;
let unsubBudgets = null;
let toastTimer = null;

// Elements
const authScreen = document.getElementById("auth-screen");
const appShell = document.getElementById("app-shell");
const authErrorEl = document.getElementById("auth-error");
const googleSignInBtn = document.getElementById("google-signin-btn");
const authThemeBtn = document.getElementById("auth-theme-btn");

const desktopMonthHeading = document.getElementById("desktop-month-heading");
const monthToggleBtn = document.getElementById("month-toggle-btn");
const monthPrevBtn = document.getElementById("month-prev-btn");
const monthNextBtn = document.getElementById("month-next-btn");
const currencySelect = document.getElementById("currency-select");
const exportCsvBtn = document.getElementById("export-csv-btn");
const themeToggleBtn = document.getElementById("theme-toggle-btn");
const accountMenuBtn = document.getElementById("account-menu-btn");
const accountDropdown = document.getElementById("account-dropdown");
const accountEmailEl = document.getElementById("account-email");
const signOutBtn = document.getElementById("sign-out-btn");
const syncErrorBanner = document.getElementById("sync-error-banner");

const sectionKpis = document.getElementById("section-kpis");
const sectionCharts = document.getElementById("section-charts");
const sectionBudgets = document.getElementById("section-budgets");
const sectionActivity = document.getElementById("section-activity");
const activityTitle = document.getElementById("activity-title");
const activityFilters = document.getElementById("activity-filters");
const searchInput = document.getElementById("search-input");
const categoryChipsEl = document.getElementById("category-filter-chips");
const transactionsListEl = document.getElementById("transactions-list");
const budgetsGridEl = document.getElementById("budgets-grid");
const suggestedBudgetsBtn = document.getElementById("suggested-budgets-btn");

const kpiNetBalance = document.getElementById("kpi-net-balance");
const kpiSavingsBadge = document.getElementById("kpi-savings-badge");
const kpiTxCount = document.getElementById("kpi-tx-count");
const kpiIncome = document.getElementById("kpi-income");
const kpiExpense = document.getElementById("kpi-expense");
const cashflowChartContainer = document.getElementById("cashflow-chart-container");
const categoryDonutContainer = document.getElementById("category-donut-container");

const txModalBackdrop = document.getElementById("tx-modal-backdrop");
const txModalTitle = document.getElementById("tx-modal-title");
const txModalClose = document.getElementById("tx-modal-close");
const txForm = document.getElementById("tx-form");
const txFormError = document.getElementById("tx-form-error");
const txTitleInput = document.getElementById("tx-title-input");
const txAmountInput = document.getElementById("tx-amount-input");
const txDateInput = document.getElementById("tx-date-input");
const txCategorySelect = document.getElementById("tx-category-select");
const txNoteInput = document.getElementById("tx-note-input");
const txSubmitBtn = document.getElementById("tx-submit-btn");
const toastEl = document.getElementById("toast");

function applyTheme() {
  document.documentElement.classList.toggle("dark", state.theme === "dark");
}

function showToast(msg) {
  if (!toastEl) return;
  toastEl.textContent = msg;
  toastEl.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    toastEl.hidden = true;
  }, 2800);
}

function getFilteredTransactions() {
  const monthScoped = state.month
    ? state.transactions.filter((tx) => String(tx.date || "").startsWith(state.month))
    : state.transactions;

  const q = state.searchQuery.trim().toLowerCase();
  return monthScoped
    .filter((tx) => {
      const typeOk =
        state.filterType === "all" || tx.type === state.filterType;
      const catOk =
        state.filterCategory === "all" || tx.category === state.filterCategory;
      const meta = findCategory(tx.category);
      const searchOk =
        !q ||
        String(tx.title || "").toLowerCase().includes(q) ||
        String(tx.note || "").toLowerCase().includes(q) ||
        meta.label.toLowerCase().includes(q);
      return typeOk && catOk && searchOk;
    })
    .sort((a, b) => String(b.date || "").localeCompare(String(a.date || "")));
}

function renderCategoryChips() {
  const chips = [
    `<button type="button" class="cat-chip ${state.filterCategory === "all" ? "active" : ""}" data-cat-filter="all">All Categories</button>`,
    ...CATEGORIES.map(
      (c) =>
        `<button type="button" class="cat-chip ${state.filterCategory === c.id ? "active" : ""}" data-cat-filter="${c.id}">${escapeHtml(c.label)}</button>`
    ),
  ];
  categoryChipsEl.innerHTML = chips.join("");
  categoryChipsEl.querySelectorAll("[data-cat-filter]").forEach((btn) => {
    btn.addEventListener("click", () => {
      state.filterCategory = btn.dataset.catFilter;
      renderApp();
    });
  });
}

function renderTransactions(filtered) {
  const list = state.tab === "overview" ? filtered.slice(0, 6) : filtered;
  if (list.length === 0) {
    transactionsListEl.innerHTML = `
      <div class="empty-state-box">
        <span class="empty-icon" aria-hidden="true">🧾</span>
        <p>No transactions for this period yet.</p>
      </div>
    `;
    return;
  }

  transactionsListEl.innerHTML = list
    .map((tx) => {
      const meta = findCategory(tx.category);
      const isIncome = tx.type === "income";
      const sign = isIncome ? "+" : "-";
      return `
        <div class="tx-row">
          <div class="tx-left">
            <div class="tx-avatar" style="background:${meta.color}22;color:${meta.color}">
              ${escapeHtml(meta.label.charAt(0))}
            </div>
            <div class="tx-info">
              <div class="tx-title">${escapeHtml(tx.title)}</div>
              <div class="tx-sub">
                ${escapeHtml(meta.label)} • ${escapeHtml(tx.date)}${tx.note ? ` • ${escapeHtml(tx.note)}` : ""}
              </div>
            </div>
          </div>
          <div class="tx-right">
            <span class="tx-amount mono ${isIncome ? "income" : ""}">
              ${sign}${escapeHtml(formatCurrency(tx.amount, state.currency))}
            </span>
            <button type="button" class="tx-icon-action" data-edit-tx="${escapeHtml(tx.id)}" aria-label="Edit ${escapeHtml(tx.title)}">
              ✎
            </button>
            <button type="button" class="tx-icon-action delete" data-del-tx="${escapeHtml(tx.id)}" aria-label="Delete ${escapeHtml(tx.title)}">
              🗑
            </button>
          </div>
        </div>
      `;
    })
    .join("");

  transactionsListEl.querySelectorAll("[data-edit-tx]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const tx = state.transactions.find((item) => item.id === btn.dataset.editTx);
      if (tx) openModal(tx);
    });
  });

  transactionsListEl.querySelectorAll("[data-del-tx]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const id = btn.dataset.delTx;
      const tx = state.transactions.find((item) => item.id === id);
      if (!tx) return;
      if (!window.confirm(`Delete "${tx.title}"?`)) return;
      try {
        await deleteTransaction(id);
        showToast("Transaction deleted");
      } catch (err) {
        showToast(err?.message || "Could not delete transaction");
      }
    });
  });
}

function renderBudgets(budgetStatuses) {
  budgetsGridEl.innerHTML = budgetStatuses
    .map((b) => {
      const pct = b.limit > 0 ? Math.min(100, b.utilization) : 0;
      const badgeLabel =
        b.status === "EXCEEDED"
          ? `Over (${b.utilization.toFixed(0)}%)`
          : b.status === "WARNING"
          ? `Near (${b.utilization.toFixed(0)}%)`
          : b.status === "HEALTHY"
          ? `On Track (${b.utilization.toFixed(0)}%)`
          : "No Limit";

      const isEditing = state.editingBudgetCat === b.category;

      return `
        <article class="card budget-card">
          <div class="budget-top">
            <div class="budget-name">
              <span class="dot" style="background:${b.color}"></span>
              <span>${escapeHtml(b.label)}</span>
            </div>
            <span class="status-badge ${b.status}">${escapeHtml(badgeLabel)}</span>
          </div>

          <div class="progress-track">
            <div class="progress-fill ${b.status}" style="width:${pct.toFixed(1)}%"></div>
          </div>

          <div class="budget-numbers mono">
            <span>Spent: ${escapeHtml(formatCurrency(b.spent, state.currency))}</span>
            <span class="muted">Limit: ${b.limit > 0 ? escapeHtml(formatCurrency(b.limit, state.currency)) : "—"}</span>
          </div>

          ${
            isEditing
              ? `
                <div class="budget-edit-row">
                  <input
                    type="number"
                    min="0"
                    step="100"
                    class="budget-edit-input"
                    id="budget-input-${b.category}"
                    value="${b.limit > 0 ? b.limit : ""}"
                    placeholder="Monthly limit"
                  />
                  <button type="button" class="btn-primary" style="min-height:40px;padding:0 14px" data-save-budget="${b.category}">Save</button>
                  <button type="button" class="btn-secondary" style="min-height:40px" data-cancel-budget>Cancel</button>
                </div>
              `
              : `
                <div>
                  <button type="button" class="btn-secondary" data-edit-budget="${b.category}">
                    Edit Limit
                  </button>
                </div>
              `
          }
        </article>
      `;
    })
    .join("");

  budgetsGridEl.querySelectorAll("[data-edit-budget]").forEach((btn) => {
    btn.addEventListener("click", () => {
      state.editingBudgetCat = btn.dataset.editBudget;
      renderApp();
    });
  });

  budgetsGridEl.querySelectorAll("[data-cancel-budget]").forEach((btn) => {
    btn.addEventListener("click", () => {
      state.editingBudgetCat = null;
      renderApp();
    });
  });

  budgetsGridEl.querySelectorAll("[data-save-budget]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const cat = btn.dataset.saveBudget;
      const input = document.getElementById(`budget-input-${cat}`);
      const val = Number(input?.value ?? 0);
      if (!Number.isFinite(val) || val < 0) {
        showToast("Enter a valid zero or positive limit");
        return;
      }
      try {
        await setBudget(cat, val);
        state.editingBudgetCat = null;
        showToast("Budget updated");
      } catch (err) {
        showToast(err?.message || "Failed to update budget");
      }
    });
  });
}

function renderApp() {
  applyTheme();
  const isAuthed = Boolean(state.user);
  authScreen.hidden = isAuthed;
  appShell.hidden = !isAuthed;
  if (!isAuthed) return;

  // Navigation active state
  document.querySelectorAll("[data-tab]").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.tab === state.tab);
  });

  const monthLabel = formatMonthLabel(state.month);
  desktopMonthHeading.textContent = monthLabel;
  monthToggleBtn.textContent = monthLabel;
  currencySelect.value = state.currency;
  accountEmailEl.textContent = state.user?.email || state.user?.displayName || "Signed in";

  if (state.syncError) {
    syncErrorBanner.hidden = false;
    syncErrorBanner.textContent = state.syncError;
  } else {
    syncErrorBanner.hidden = true;
  }

  // Section visibility by tab
  sectionKpis.hidden = state.tab !== "overview" && state.tab !== "analytics";
  sectionCharts.hidden = state.tab !== "overview" && state.tab !== "analytics";
  sectionBudgets.hidden = state.tab !== "budgets";
  sectionActivity.hidden = state.tab !== "overview" && state.tab !== "activity";
  activityFilters.hidden = state.tab !== "activity";
  activityTitle.textContent =
    state.tab === "overview" ? "Recent Transactions" : "Transactions";

  const summary = computeSummary(
    state.transactions,
    state.budgetsMap,
    state.month
  );
  const filtered = getFilteredTransactions();

  kpiNetBalance.textContent = formatCurrency(summary.netBalance, state.currency);
  kpiSavingsBadge.textContent = `${summary.savingsRate.toFixed(1)}% savings`;
  kpiTxCount.textContent = `${summary.transactionCount} transaction${summary.transactionCount === 1 ? "" : "s"} in period`;
  kpiIncome.textContent = formatCurrency(summary.totalIncome, state.currency);
  kpiExpense.textContent = formatCurrency(summary.totalExpense, state.currency);

  if (!sectionCharts.hidden) {
    renderCashflowChart(cashflowChartContainer, summary.dailyTrend, state.currency);
    renderCategoryDonut(
      categoryDonutContainer,
      summary.categoryBreakdown,
      summary.totalExpense,
      state.currency
    );
  }

  if (!sectionBudgets.hidden) {
    renderBudgets(summary.budgetStatuses);
  }

  if (!sectionActivity.hidden) {
    if (state.tab === "activity") {
      renderCategoryChips();
    }
    renderTransactions(filtered);
  }
}

function populateModalCategories(selectedCategory) {
  const options = categoriesForType(state.modalType);
  const validMatch = options.some((c) => c.id === selectedCategory)
    ? selectedCategory
    : options[0].id;
  txCategorySelect.innerHTML = options
    .map((c) => `<option value="${c.id}">${escapeHtml(c.label)}</option>`)
    .join("");
  txCategorySelect.value = validMatch;
}

function openModal(tx = null) {
  state.editingTxId = tx?.id || null;
  state.modalType = tx?.type === "income" ? "income" : "expense";
  txModalTitle.textContent = tx ? "Edit Transaction" : "New Transaction";
  txFormError.hidden = true;
  txFormError.textContent = "";

  document.querySelectorAll("[data-tx-type]").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.txType === state.modalType);
  });

  txTitleInput.value = tx?.title || "";
  txAmountInput.value = tx?.amount ? String(tx.amount) : "";
  txDateInput.value = tx?.date || todayIsoDate();
  txNoteInput.value = tx?.note || "";
  populateModalCategories(tx?.category || (state.modalType === "income" ? "salary" : "food"));

  txModalBackdrop.hidden = false;
  setTimeout(() => txTitleInput.focus(), 40);
}

function closeModal() {
  txModalBackdrop.hidden = true;
  state.editingTxId = null;
}

function teardownListeners() {
  if (unsubTx) {
    unsubTx();
    unsubTx = null;
  }
  if (unsubBudgets) {
    unsubBudgets();
    unsubBudgets = null;
  }
}

function attachUserListeners(uid) {
  teardownListeners();
  state.syncError = "";

  unsubTx = observeTransactions(
    uid,
    (items) => {
      state.transactions = items;
      renderApp();
    },
    (err) => {
      state.syncError = err?.message || "Failed to sync transactions from Firestore.";
      renderApp();
    }
  );

  unsubBudgets = observeBudgets(
    uid,
    (items) => {
      const map = {};
      for (const b of items) {
        map[b.category || b.id] = Number(b.limit || 0);
      }
      state.budgetsMap = map;
      renderApp();
    },
    (err) => {
      state.syncError = err?.message || "Failed to sync budgets from Firestore.";
      renderApp();
    }
  );
}

// --- Event Wiring ---
function toggleTheme() {
  state.theme = state.theme === "dark" ? "light" : "dark";
  savePrefs({ theme: state.theme, currency: state.currency });
  applyTheme();
}

authThemeBtn.addEventListener("click", toggleTheme);
themeToggleBtn.addEventListener("click", toggleTheme);

googleSignInBtn.addEventListener("click", async () => {
  authErrorEl.hidden = true;
  googleSignInBtn.disabled = true;
  googleSignInBtn.textContent = "Signing in...";
  try {
    await signInWithGoogle();
  } catch (err) {
    authErrorEl.textContent = formatAuthError(err);
    authErrorEl.hidden = false;
  } finally {
    googleSignInBtn.disabled = false;
    googleSignInBtn.textContent = "Sign in with Google";
  }
});

document.querySelectorAll("[data-tab]").forEach((btn) => {
  btn.addEventListener("click", () => {
    state.tab = btn.dataset.tab;
    renderApp();
  });
});

monthPrevBtn.addEventListener("click", () => {
  state.month = stepMonth(state.month, -1);
  renderApp();
});

monthNextBtn.addEventListener("click", () => {
  state.month = stepMonth(state.month, 1);
  renderApp();
});

monthToggleBtn.addEventListener("click", () => {
  state.month = state.month ? "" : currentMonthPrefix();
  renderApp();
});

currencySelect.addEventListener("change", (e) => {
  state.currency = e.target.value;
  savePrefs({ theme: state.theme, currency: state.currency });
  renderApp();
});

exportCsvBtn.addEventListener("click", () => {
  const rows = getFilteredTransactions();
  if (rows.length === 0) {
    showToast("No transactions in this period to export");
    return;
  }
  const csv = transactionsToCsv(rows);
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `expense-tracker-an-cloud-${state.month || "all"}.csv`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
  showToast("CSV exported");
});

accountMenuBtn.addEventListener("click", (e) => {
  e.stopPropagation();
  const nextHidden = !accountDropdown.hidden;
  accountDropdown.hidden = nextHidden;
  accountMenuBtn.setAttribute("aria-expanded", String(!nextHidden));
});

document.addEventListener("click", () => {
  if (!accountDropdown.hidden) {
    accountDropdown.hidden = true;
    accountMenuBtn.setAttribute("aria-expanded", "false");
  }
});

signOutBtn.addEventListener("click", async () => {
  accountDropdown.hidden = true;
  await signOutUser();
});

searchInput.addEventListener("input", (e) => {
  state.searchQuery = e.target.value;
  renderApp();
});

document.querySelectorAll("[data-type-filter]").forEach((btn) => {
  btn.addEventListener("click", () => {
    state.filterType = btn.dataset.typeFilter;
    document.querySelectorAll("[data-type-filter]").forEach((b) => {
      b.classList.toggle("active", b === btn);
    });
    renderApp();
  });
});

suggestedBudgetsBtn.addEventListener("click", async () => {
  suggestedBudgetsBtn.disabled = true;
  try {
    await Promise.all(
      Object.entries(SUGGESTED_BUDGETS).map(([cat, limit]) => setBudget(cat, limit))
    );
    showToast("Suggested monthly budgets applied");
  } catch (err) {
    showToast(err?.message || "Failed to apply budgets");
  } finally {
    suggestedBudgetsBtn.disabled = false;
  }
});

document.getElementById("rail-add-btn").addEventListener("click", () => openModal());
document.getElementById("activity-add-btn").addEventListener("click", () => openModal());
document.getElementById("fab-add-btn").addEventListener("click", () => openModal());
txModalClose.addEventListener("click", closeModal);

txModalBackdrop.addEventListener("click", (e) => {
  if (e.target === txModalBackdrop) closeModal();
});

document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && !txModalBackdrop.hidden) closeModal();
});

document.querySelectorAll("[data-tx-type]").forEach((btn) => {
  btn.addEventListener("click", () => {
    state.modalType = btn.dataset.txType;
    document.querySelectorAll("[data-tx-type]").forEach((b) => {
      b.classList.toggle("active", b === btn);
    });
    populateModalCategories(txCategorySelect.value);
  });
});

txForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  txFormError.hidden = true;

  const check = validateTransactionInput({
    title: txTitleInput.value,
    amount: txAmountInput.value,
    type: state.modalType,
    category: txCategorySelect.value,
    date: txDateInput.value,
    note: txNoteInput.value,
  });

  if (!check.valid) {
    txFormError.textContent = check.errors[0];
    txFormError.hidden = false;
    return;
  }

  txSubmitBtn.disabled = true;
  txSubmitBtn.textContent = "Saving...";
  try {
    if (state.editingTxId) {
      await updateTransaction(state.editingTxId, check.data);
      showToast("Transaction updated");
    } else {
      await createTransaction(check.data);
      showToast("Transaction added");
    }
    closeModal();
  } catch (err) {
    txFormError.textContent = err?.message || "Failed to save transaction.";
    txFormError.hidden = false;
  } finally {
    txSubmitBtn.disabled = false;
    txSubmitBtn.textContent = "Save Transaction";
  }
});

// Boot
applyTheme();
initAuthRedirectCheck();
subscribeAuthState((user) => {
  state.user = user || null;
  if (user) {
    attachUserListeners(user.uid);
  } else {
    teardownListeners();
    state.transactions = [];
    state.budgetsMap = {};
    state.syncError = "";
  }
  renderApp();
});
