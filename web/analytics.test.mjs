import { test } from "node:test";
import assert from "node:assert/strict";
import {
  roundCurrency,
  isValidIsoDate,
  validateTransactionInput,
  computeSummary,
  transactionsToCsv,
} from "./analytics.js";

test("roundCurrency avoids floating-point drift", () => {
  assert.equal(roundCurrency(0.1 + 0.2), 0.3);
  assert.equal(roundCurrency(19.995), 20.0);
});

test("isValidIsoDate rejects rollover dates and invalid formats", () => {
  assert.equal(isValidIsoDate("2026-11-12"), true);
  assert.equal(isValidIsoDate("2026-02-30"), false);
  assert.equal(isValidIsoDate("12-11-2026"), false);
});

test("validateTransactionInput validates bounds and trims inputs", () => {
  const invalid = validateTransactionInput({
    title: "   ",
    amount: "-5",
    type: "expense",
    category: "unknown",
    date: "2026-13-01",
    note: "",
  });
  assert.equal(invalid.valid, false);
  assert.ok(invalid.errors.length >= 3);

  const valid = validateTransactionInput({
    title: "  Groceries  ",
    amount: "3450.555",
    type: "expense",
    category: "food",
    date: "2026-11-12",
    note: "  Weekly  ",
  });
  assert.equal(valid.valid, true);
  assert.equal(valid.data.title, "Groceries");
  assert.equal(valid.data.amount, 3450.56);
  assert.equal(valid.data.note, "Weekly");
});

test("computeSummary computes monthly KPIs and budget statuses", () => {
  const txs = [
    { id: "1", title: "Salary", amount: 50000, type: "income", category: "salary", date: "2026-11-01" },
    { id: "2", title: "Groceries", amount: 6500, type: "expense", category: "food", date: "2026-11-05" },
    { id: "3", title: "Rent", amount: 20000, type: "expense", category: "housing", date: "2026-11-02" },
    { id: "4", title: "Old Coffee", amount: 500, type: "expense", category: "food", date: "2026-10-15" },
  ];
  const budgets = { food: 8000, housing: 18000 };
  const summary = computeSummary(txs, budgets, "2026-11");

  assert.equal(summary.totalIncome, 50000);
  assert.equal(summary.totalExpense, 26500);
  assert.equal(summary.netBalance, 23500);
  assert.equal(summary.savingsRate, 47);
  assert.equal(summary.transactionCount, 3);

  const food = summary.budgetStatuses.find((b) => b.category === "food");
  const housing = summary.budgetStatuses.find((b) => b.category === "housing");
  assert.equal(food.status, "WARNING");
  assert.equal(housing.status, "EXCEEDED");
});

test("transactionsToCsv neutralizes spreadsheet formula injection", () => {
  const csv = transactionsToCsv([
    {
      id: "tx1",
      date: "2026-11-12",
      title: "=CMD('calc')",
      type: "expense",
      category: "food",
      amount: 120.5,
      note: "Lunch, Office",
    },
  ]);
  const lines = csv.split("\n");
  assert.equal(lines.length, 2);
  assert.ok(lines[1].includes("'=CMD('calc')"));
  assert.ok(lines[1].includes('"Lunch, Office"'));
});
