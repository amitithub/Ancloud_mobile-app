# Expense Tracker by AN Cloud

An open-source Android and Web expense & budget tracker powered by Firebase Authentication (Google Sign-In) and Cloud Firestore.

## Features
- **Cross-Platform Cloud Sync**: Android app and Web app share the same Firebase project (`gen-lang-client-0052022860`) and named Cloud Firestore database (`ai-studio-android-pulse-bcf75b98-6330-4cca-9a86-4ddb27931403`).
- **Overview & Bento Dashboard**: Net balance, monthly income/expense totals, and savings rate.
- **Interactive Analytics**: Grouped Cashflow Trend bar chart and Category Breakdown donut chart.
- **Monthly Category Budgets**: Configurable limits with real-time utilization status (`On Track`, `Near Limit`, `Over Budget`).
- **Transaction Management**: Add, edit, delete, search, filter by type/category/month, and export CSV with formula-injection protection.
- **Multi-Currency & Theme Support**: Supports `INR`, `USD`, `EUR`, and `GBP`, plus Light/Dark mode.

## Web App Hosting (`web/` Folder)
The `web/` folder is zero-build static ESM HTML/CSS/JS using relative asset paths, so you can host it anywhere with no build step:

1. **GitHub Pages**:
   - Push this repository to GitHub (**Settings → Push to GitHub** in Google AI Studio).
   - In your GitHub repo, open **Settings → Pages**, deploy from branch (e.g., via a static site workflow or serving `/web`), or set `/web` as your publish root.
2. **Cloudflare Pages**:
   - Connect your pushed GitHub repository in **Cloudflare Pages** (or upload the `web/` folder via Direct Upload).
   - Set **Build command** to blank (none) and **Build output directory** to `web`.
3. **Firebase Hosting**:
   - `firebase.json` is pre-configured with `"public": "web"`; run `firebase deploy --only hosting`.
4. **Enable Google Sign-In on Your Web Domain (Required)**:
   - Open **Firebase Console → Project `gen-lang-client-0052022860` → Authentication → Settings → Authorized domains** and add your live domain (e.g., `<username>.github.io`, `<app>.pages.dev`, or `<project>.web.app`).

## Android Releases & Setup
- Download the latest APK from **Releases** (or track updates via [Obtainium](https://obtainium.imranr.dev/)).
- For release-signed APKs/AABs, add your release keystore's **SHA-1** fingerprint in **Firebase Console → Project Settings → Android App (`com.aistudio.pulseexpense.k9m4x2`)**.

## License
Licensed under the [MIT License](LICENSE).
