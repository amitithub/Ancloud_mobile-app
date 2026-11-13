const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function nowPastDate() {
  return new Date(Date.now() - 2000);
}

function validTx(uid, overrides = {}) {
  return {
    userId: uid,
    title: "Organic Groceries",
    amount: 3450.5,
    type: "expense",
    category: "food",
    date: "2026-11-12",
    note: "Weekly stockup",
    createdAt: nowPastDate(),
    updatedAt: nowPastDate(),
    ...overrides,
  };
}

function validBudget(uid, category = "food", overrides = {}) {
  return {
    userId: uid,
    category,
    limit: 8000,
    createdAt: nowPastDate(),
    updatedAt: nowPastDate(),
    ...overrides,
  };
}

test("Unauthenticated user cannot read or write transactions or budgets", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx1").set(validTx(ALICE_UID))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("budgets").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("budgets").doc("food").set(validBudget(ALICE_UID))
  );
});

test("Owner can create, read, update, and delete own transactions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1");

  await assertSucceeds(txRef.set(validTx(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("transactions").get());
  await assertSucceeds(
    txRef.update({
      title: "Team Dinner",
      amount: 1200,
      updatedAt: nowPastDate(),
    })
  );
  await assertSucceeds(txRef.delete());
});

test("Cross-user access to transactions and budgets is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_alice").set(validTx(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("budgets").doc("food").set(validBudget(ALICE_UID))
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("transactions").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_alice").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_alice").update({
      title: "Hacked",
      updatedAt: nowPastDate(),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_alice").delete());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("budgets").doc("food").get());
});

test("Validation rejects ghost fields, negative amounts, bad dates, and userId spoofing", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("transactions");

  // Ghost field
  await assertFails(col.doc("tx_ghost").set(validTx(ALICE_UID, { isVerified: true })));
  // Negative amount
  await assertFails(col.doc("tx_neg").set(validTx(ALICE_UID, { amount: -10 })));
  // Invalid date format
  await assertFails(col.doc("tx_date").set(validTx(ALICE_UID, { date: "12-11-2026" })));
  // Spoofed userId
  await assertFails(col.doc("tx_spoof").set(validTx(BOB_UID)));
  // Invalid category
  await assertFails(col.doc("tx_cat").set(validTx(ALICE_UID, { category: "crypto" })));
});

test("Update rejects immutable createdAt or userId tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_imm");
  await assertSucceeds(txRef.set(validTx(ALICE_UID)));

  await assertFails(
    txRef.update({
      userId: BOB_UID,
      updatedAt: nowPastDate(),
    })
  );
  await assertFails(
    txRef.update({
      createdAt: new Date(1000),
      updatedAt: nowPastDate(),
    })
  );
});

test("Budgets validate expense category matching path ID and non-negative limit", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const budgetsCol = aliceDb.collection("users").doc(ALICE_UID).collection("budgets");

  await assertSucceeds(budgetsCol.doc("food").set(validBudget(ALICE_UID, "food")));
  await assertSucceeds(budgetsCol.doc("food").update({ limit: 9500, updatedAt: nowPastDate() }));

  // Path mismatch
  await assertFails(budgetsCol.doc("food").set(validBudget(ALICE_UID, "transport")));
  // Income category cannot have a budget
  await assertFails(budgetsCol.doc("salary").set(validBudget(ALICE_UID, "salary")));
  // Negative limit
  await assertFails(budgetsCol.doc("transport").set(validBudget(ALICE_UID, "transport", { limit: -1 })));
});
