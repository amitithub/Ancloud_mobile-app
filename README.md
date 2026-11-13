# Expense Tracker by AN Cloud

An open-source Android expense and budget tracker built with Kotlin, Jetpack Compose, Material 3, Firebase Authentication (Google Sign-In via Credential Manager), and Cloud Firestore.

## Features
- **Overview & Bento Dashboard**: Net balance, monthly income/expense totals, and savings rate.
- **Interactive Analytics**: Custom Compose grouped Cashflow Trend bar chart and Category Breakdown donut chart.
- **Monthly Category Budgets**: Configurable limits with real-time utilization status (`On Track`, `Near Limit`, `Over Budget`).
- **Transaction Management**: Add, edit, delete, search, filter by type/category/month, and export CSV with formula-injection protection.
- **Multi-Currency & Theme Support**: Supports `INR`, `USD`, `EUR`, and `GBP`, plus Light/Dark mode persisted with DataStore.
- **Cloud Sync**: User-isolated Cloud Firestore data (`users/{uid}/transactions` and `users/{uid}/budgets`) backed by strict Firestore Security Rules.

## Installing from Releases
1. Download the latest `.apk` from the **Releases** page (or track this repository using [Obtainium](https://obtainium.imranr.dev/)).
2. Allow installing from your browser/files app if prompted on Android.

## Release Build & Firebase Setup (Maintainers)
If you build and sign your own release APK/AAB:
1. Extract your release keystore's SHA-1 fingerprint:
   ```bash
   keytool -list -v -keystore your-release-key.jks -alias upload
   ```
2. Open **Firebase Console** → Project `gen-lang-client-0052022860` → **Project Settings** → Android app (`com.aistudio.pulseexpense.k9m4x2`) and add your release **SHA-1** (and **SHA-256**) fingerprint so Google Sign-In works on release installs.
3. Build a signed release APK or bundle in Android Studio, or supply environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) when running `gradle :app:assembleRelease`.

## License
Licensed under the [MIT License](LICENSE).
