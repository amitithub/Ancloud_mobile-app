// Firebase Auth (Google Sign-In) & Named Cloud Firestore client for Expense Tracker by AN Cloud
import { initializeApp } from "https://www.gstatic.com/firebasejs/11.1.0/firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  onAuthStateChanged,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  signOut,
} from "https://www.gstatic.com/firebasejs/11.1.0/firebase-auth.js";
import {
  getFirestore,
  collection,
  doc,
  getDoc,
  addDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  onSnapshot,
  serverTimestamp,
} from "https://www.gstatic.com/firebasejs/11.1.0/firebase-firestore.js";

import { FIREBASE_CONFIG, FIRESTORE_DATABASE_ID } from "./config.js";
import { VALID_EXPENSE_CATEGORY_IDS, roundCurrency } from "./analytics.js";

const app = initializeApp(FIREBASE_CONFIG);
const auth = getAuth(app);
const db = getFirestore(app, FIRESTORE_DATABASE_ID);
const googleProvider = new GoogleAuthProvider();
googleProvider.setCustomParameters({ prompt: "select_account" });

export function initAuthRedirectCheck() {
  return getRedirectResult(auth).catch((err) => {
    console.warn("Google redirect auth check failed:", err);
    return null;
  });
}

export function subscribeAuthState(callback) {
  return onAuthStateChanged(auth, callback);
}

export async function signInWithGoogle() {
  try {
    return await signInWithPopup(auth, googleProvider);
  } catch (err) {
    if (
      err?.code === "auth/popup-blocked" ||
      err?.code === "auth/operation-not-supported-in-this-environment"
    ) {
      await signInWithRedirect(auth, googleProvider);
      return null;
    }
    throw err;
  }
}

export async function signOutUser() {
  await signOut(auth);
}

export function formatAuthError(err) {
  const code = err?.code || "";
  if (code === "auth/unauthorized-domain") {
    const host = window.location.hostname || "this domain";
    return `Domain "${host}" is not authorized for Google Sign-In. Add it in Firebase Console → Authentication → Settings → Authorized domains.`;
  }
  if (code === "auth/popup-closed-by-user") {
    return "Sign-in window was closed before completing.";
  }
  if (code === "auth/network-request-failed") {
    return "Network error while contacting Google Sign-In. Please check your connection.";
  }
  return err?.message || "Failed to sign in with Google.";
}

function transactionsCol(userId) {
  return collection(db, "users", userId, "transactions");
}

function budgetsCol(userId) {
  return collection(db, "users", userId, "budgets");
}

export function observeTransactions(userId, onData, onError) {
  return onSnapshot(
    transactionsCol(userId),
    (snapshot) => {
      const items = snapshot.docs
        .map((d) => ({ id: d.id, ...d.data() }))
        .sort((a, b) => String(b.date || "").localeCompare(String(a.date || "")));
      onData(items);
    },
    (err) => {
      console.error("Firestore transactions listener error:", err);
      onError?.(err);
    }
  );
}

export function observeBudgets(userId, onData, onError) {
  return onSnapshot(
    budgetsCol(userId),
    (snapshot) => {
      const items = snapshot.docs.map((d) => ({ id: d.id, ...d.data() }));
      onData(items);
    },
    (err) => {
      console.error("Firestore budgets listener error:", err);
      onError?.(err);
    }
  );
}

export async function createTransaction(draft) {
  const uid = auth.currentUser?.uid;
  if (!uid) throw new Error("Sign in with Google before saving records.");

  const payload = {
    userId: uid,
    title: draft.title,
    amount: roundCurrency(draft.amount),
    type: draft.type,
    category: draft.category,
    date: draft.date,
    note: draft.note || "",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  const ref = await addDoc(transactionsCol(uid), payload);
  return ref.id;
}

export async function updateTransaction(txId, draft) {
  const uid = auth.currentUser?.uid;
  if (!uid) throw new Error("Sign in with Google before updating records.");
  if (!txId) throw new Error("Transaction ID is required.");

  const ref = doc(db, "users", uid, "transactions", txId);
  await updateDoc(ref, {
    title: draft.title,
    amount: roundCurrency(draft.amount),
    type: draft.type,
    category: draft.category,
    date: draft.date,
    note: draft.note || "",
    updatedAt: serverTimestamp(),
  });
}

export async function deleteTransaction(txId) {
  const uid = auth.currentUser?.uid;
  if (!uid) throw new Error("Sign in with Google before deleting records.");
  if (!txId) throw new Error("Transaction ID is required.");

  const ref = doc(db, "users", uid, "transactions", txId);
  await deleteDoc(ref);
}

export async function setBudget(category, limit) {
  const uid = auth.currentUser?.uid;
  if (!uid) throw new Error("Sign in with Google before saving budgets.");
  if (!VALID_EXPENSE_CATEGORY_IDS.has(category)) {
    throw new Error(`Invalid expense category: ${category}`);
  }
  const num = Number(limit);
  if (!Number.isFinite(num) || num < 0 || num > 100_000_000) {
    throw new Error("Budget limit must be between 0 and 100,000,000.");
  }

  const rounded = roundCurrency(num);
  const ref = doc(db, "users", uid, "budgets", category);
  const existing = await getDoc(ref);
  if (existing.exists()) {
    await updateDoc(ref, {
      limit: rounded,
      updatedAt: serverTimestamp(),
    });
  } else {
    await setDoc(ref, {
      userId: uid,
      category,
      limit: rounded,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  }
}
