// Pure domain analytics, validation, currency formatting, and CSV export
// Mirrors Android AnalyticsEngine.kt and firestore.rules invariants.

export const CATEGORIES = [
  { id: "food", label: "Food & Dining", color: "#F97316", scope: "expense" },
  { id: "housing", label: "Housing & Rent", color: "#6366F1", scope: "expense" },
  { id: "transport", label: "Transport", color: "#06B6D4", scope: "expense" },
  { id: "utilities", label: "Bills & Utilities", color: "#EAB308", scope: "expense" },
  { id: "shopping", label: "Shopping", color: "#EC4899", scope: "expense" },
  { id: "entertainment", label: "Entertainment", color: "#A855F7", scope: "expense" },
  { id: "health", label: "Health & Fitness", color: "#10B981", scope: "expense" },
  { id: "salary", label: "Salary", color: "#22C55E", scope: "income" },
  { id: "freelance", label: "Freelance", color: "#14B8A6", scope: "income" },
  { id: "other", label: "Other", color: "#64748B", scope: "both" },
];

export const EXPENSE_CATEGORIES = CATEGORIES.filter((c) => c.scope !== "income");
export const VALID_CATEGORY_IDS = new Set(CATEGORIES.map((c) => c.id));
export const VALID_EXPENSE_CATEGORY_IDS = new Set(EXPENSE_CATEGORIES.map((c) => c.id));

const CATEGORY_MAP = new Map(CATEGORIES.map((c) => [c.id, c]));

export function findCategory(id) {
  return (
    CATEGORY_MAP.get(id) || {
      id: id || "other",
      label: id ? id.charAt(0).toUpperCase() + id.slice(1) : "Other",
      color: "#64748B",
      scope: "both",
    }
  );
}

export function categoriesForType(type) {
  if (type === "income") {
    return CATEGORIES.filter((c) => c.scope === "income" || c.scope === "both");
  }
  return CATEGORIES.filter((c) => c.scope === "expense" || c.scope === "both");
}

export const CURRENCIES = {
  INR: { code: "INR", locale: "en-IN", symbol: "₹" },
  USD: { code: "USD", locale: "en-US", symbol: "$" },
  EUR: { code: "EUR", locale: "de-DE", symbol: "€" },
  GBP: { code: "GBP", locale: "en-GB", symbol: "£" },
};

export const SUGGESTED_BUDGETS = {
  food: 8000,
  housing: 18000,
  transport: 3500,
  utilities: 3000,
  shopping: 5000,
  entertainment: 2500,
  health: 2500,
  other: 2000,
};

const ISO_DATE_RE = /^(\d{4})-(\d{2})-(\d{2})$/;

export function roundCurrency(value) {
  const num = Number(value);
  if (!Number.isFinite(num)) return 0;
  return Math.round((num + Math.sign(num) * Number.EPSILON) * 100) / 100;
}

export function isValidIsoDate(dateStr) {
  if (typeof dateStr !== "string") return false;
  const match = ISO_DATE_RE.exec(dateStr.trim());
  if (!match) return false;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  if (month < 1 || month > 12 || day < 1 || day > 31) return false;
  const probe = new Date(Date.UTC(year, month - 1, day));
  return (
    probe.getUTCFullYear() === year &&
    probe.getUTCMonth() === month - 1 &&
    probe.getUTCDate() === day
  );
}

export function todayIsoDate() {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function currentMonthPrefix() {
  return todayIsoDate().slice(0, 7);
}

export function stepMonth(monthPrefix, delta) {
  const base = monthPrefix || currentMonthPrefix();
  const [yStr, mStr] = base.split("-");
  const date = new Date(Number(yStr), Number(mStr) - 1 + delta, 1);
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  return `${y}-${m}`;
}

export function formatMonthLabel(monthPrefix) {
  if (!monthPrefix) return "All Time";
  const [yStr, mStr] = monthPrefix.split("-");
  const date = new Date(Number(yStr), Number(mStr) - 1, 1);
  if (Number.isNaN(date.getTime())) return monthPrefix;
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    year: "numeric",
  }).format(date);
}

export function formatCurrency(amount, currencyCode = "INR") {
  const cfg = CURRENCIES[currencyCode] || CURRENCIES.INR;
  return new Intl.NumberFormat(cfg.locale, {
    style: "currency",
    currency: cfg.code,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(roundCurrency(amount));
}

export function validateTransactionInput({
  title,
  amount,
  type,
  category,
  date,
  note = "",
}) {
  const errors = [];
  const cleanTitle = String(title ?? "").trim();
  if (!cleanTitle || cleanTitle.length > 100) {
    errors.push("Title is required and must be under 100 characters.");
  }

  const parsedAmount = Number(String(amount ?? "").trim());
  if (!Number.isFinite(parsedAmount) || parsedAmount <= 0 || parsedAmount > 100_000_000) {
    errors.push("Amount must be a positive number up to 100,000,000.");
  }

  if (type !== "expense" && type !== "income") {
    errors.push("Type must be expense or income.");
  }

  if (!VALID_CATEGORY_IDS.has(category)) {
    errors.push("Please select a valid category.");
  }

  const cleanDate = String(date ?? "").trim();
  if (!isValidIsoDate(cleanDate)) {
    errors.push("Date must be a valid YYYY-MM-DD value.");
  }

  const cleanNote = String(note ?? "").trim().slice(0, 250);

  if (errors.length > 0) {
    return { valid: false, errors };
  }

  return {
    valid: true,
    data: {
      title: cleanTitle,
      amount: roundCurrency(parsedAmount),
      type,
      category,
      date: cleanDate,
      note: cleanNote,
    },
  };
}

export function computeSummary(transactions, budgetsMap = {}, monthPrefix = "") {
  const scoped = monthPrefix
    ? transactions.filter((tx) => typeof tx.date === "string" && tx.date.startsWith(monthPrefix))
    : transactions;

  let totalIncome = 0;
  let totalExpense = 0;
  const categoryTotals = {};
  const dailyMap = new Map();

  for (const tx of scoped) {
    const amt = roundCurrency(tx.amount);
    const day = dailyMap.get(tx.date) || { date: tx.date, income: 0, expense: 0 };
    if (tx.type === "income") {
      totalIncome = roundCurrency(totalIncome + amt);
      day.income = roundCurrency(day.income + amt);
    } else {
      totalExpense = roundCurrency(totalExpense + amt);
      day.expense = roundCurrency(day.expense + amt);
      categoryTotals[tx.category] = roundCurrency((categoryTotals[tx.category] || 0) + amt);
    }
    dailyMap.set(tx.date, day);
  }

  const netBalance = roundCurrency(totalIncome - totalExpense);
  const savingsRate =
    totalIncome > 0
      ? Math.round(((totalIncome - totalExpense) / totalIncome) * 1000) / 10
      : 0;

  const categoryBreakdown = Object.entries(categoryTotals)
    .map(([catId, amount]) => {
      const meta = findCategory(catId);
      const limit = Number(budgetsMap[catId] || 0);
      const percentage =
        totalExpense > 0 ? Math.round((amount / totalExpense) * 1000) / 10 : 0;
      const budgetUtilization =
        limit > 0 ? Math.round((amount / limit) * 1000) / 10 : null;
      return {
        category: catId,
        label: meta.label,
        color: meta.color,
        amount,
        percentage,
        budgetLimit: limit,
        budgetUtilization,
      };
    })
    .sort((a, b) => b.amount - a.amount);

  const budgetStatuses = EXPENSE_CATEGORIES.map((cat) => {
    const spent = categoryTotals[cat.id] || 0;
    const limit = Number(budgetsMap[cat.id] || 0);
    const utilization = limit > 0 ? Math.round((spent / limit) * 1000) / 10 : 0;
    let status = "UNCONFIGURED";
    if (limit > 0) {
      if (utilization >= 100) status = "EXCEEDED";
      else if (utilization >= 75) status = "WARNING";
      else status = "HEALTHY";
    }
    return {
      category: cat.id,
      label: cat.label,
      color: cat.color,
      spent,
      limit,
      utilization,
      status,
    };
  });

  const dailyTrend = Array.from(dailyMap.values()).sort((a, b) =>
    a.date.localeCompare(b.date)
  );

  return {
    totalIncome,
    totalExpense,
    netBalance,
    savingsRate,
    transactionCount: scoped.length,
    categoryBreakdown,
    budgetStatuses,
    dailyTrend,
  };
}

function escapeCsvCell(value) {
  let str = String(value ?? "");
  if (str.length > 0 && ["=", "+", "-", "@"].includes(str[0])) {
    str = `'${str}`;
  }
  if (/[",\n\r]/.test(str)) {
    return `"${str.replace(/"/g, '""')}"`;
  }
  return str;
}

export function transactionsToCsv(transactions) {
  const headers = ["ID", "Date", "Title", "Type", "Category", "Amount", "Note"];
  const rows = transactions.map((tx) =>
    [
      tx.id,
      tx.date,
      tx.title,
      tx.type,
      tx.category,
      roundCurrency(tx.amount).toFixed(2),
      tx.note || "",
    ]
      .map(escapeCsvCell)
      .join(",")
  );
  return [headers.join(","), ...rows].join("\n");
}
