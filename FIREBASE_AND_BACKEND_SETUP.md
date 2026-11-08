# SAIFI NATIONAL LUBRICANTS (SNL) — B2B Android Platform & Signed APK Distribution Guide

**Company Name:** SAIFI NATIONAL LUBRICANTS  
**Brand Name:** SNL  
**Application Name:** SNL Lubricants (`com.aistudio.snllubricants.pkb2b`)  
**Headquarters:** Karachi, Pakistan (`+92 310 100 52 53`)  
**Currency:** Pakistani Rupees (`PKR`)

---

## 1. Provisioned Firebase & Signing Configuration

- **Firebase Project ID:** `gen-lang-client-0464874979`
- **Named Firestore Database ID:** `ai-studio-android-snllubri-b54ea3ff-ffda-44d7-add0-db278c0f997e`
- **Registered Signing Certificate Fingerprints (in `app/google-services.json`):**
  - **SHA-1:** `FF:56:41:3E:AD:EA:55:61:E6:11:C4:73:B6:24:31:0B:FA:94:A6:D1`
  - **SHA-256:** `6D:97:1D:41:D0:B8:E4:EC:1D:05:DF:1E:94:AD:DF:B6:75:F4:99:ED:7A:61:95:EB:52:3A:3B:FC:18:4D:40:75`
- **Authentication:** Google Sign-In via Jetpack `CredentialManager` (`GetSignInWithGoogleOption` / `GetGoogleIdOption`)
- **AI Multimodal OCR:** Firebase AI Logic (`Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel("gemini-3.8-flash")`)
- **Bootstrapped Super Admin Email:** `muhammad.dh52@gmail.com`

---

## 2. How to Distribute & Side-Load the Signed APK to Dealers and Distributors

### Method 1: In-App 1-Click Signed APK Sharing (Super Admin Dashboard & Settings)
1. Open the **SNL Lubricants** app signed in as Super Admin (`muhammad.dh52@gmail.com`).
2. On the **Super Admin Command Center (HQ)** dashboard (or under **Inventory & Reports → WhatsApp & Company Settings**), tap **"Export & Share Signed .APK File"** (`Share Signed APK`).
3. The app packages the installed signed APK as **`SNL_Lubricants_B2B_v1.0_Signed.apk`** and opens the Android Share sheet so you can send the `.apk` file directly to Dealers and Distributors via **WhatsApp**, **Google Drive**, **Email**, or save it to your phone's **Downloads** folder.
4. Tap **"WhatsApp Install Guide"** to send bilingual (Urdu & English) side-loading instructions to your customers.

### Method 2: Download from Google AI Studio Toolbar
1. In the top-right corner of Google AI Studio, open the **Settings / Export** menu.
2. Select **Download APK** (or export the project ZIP and locate `app/build/outputs/apk/debug/app-debug.apk`).

### Method 3: Command-Line Signed Build (`scripts/build-signed-apk.sh`)
- **Standard Firebase-Registered Signed APK (`app-debug.apk`):**
  ```bash
  bash scripts/build-signed-apk.sh
  ```
  Output: `app/build/outputs/apk/debug/app-debug.apk` (pre-signed with the SHA-1/SHA-256 certificate registered in `app/google-services.json`, so Google Sign-In works immediately on all side-loaded devices).

- **Custom Upload Keystore Release Build (`app-release.apk`):**
  `app/build.gradle.kts` is pre-configured with a `release` signing config that reads `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` (alias `upload`), plus production R8/ProGuard keep rules in `app/proguard-rules.pro`:
  ```bash
  export KEYSTORE_PATH="/path/to/my-upload-key.jks"
  export STORE_PASSWORD="your_store_password"
  export KEY_PASSWORD="your_key_password"
  gradle :app:assembleRelease
  ```
  > **Important for Google Sign-In on Custom Release Keystores:** If you sign with a new custom `.jks` key, add its `SHA-1` and `SHA-256` fingerprints to your Firebase Project Settings (`gen-lang-client-0464874979`) and download the updated `google-services.json` so Google Sign-In (`CredentialManager`) authorizes the new release certificate.

---

## 3. Pre-Loaded SNL Sole Distributor Pakistan Price List (03-11-26)

All **59 products** across **7 categories** (`MIX Brands`, `M. Cycle`, `GASOLINE / CNG`, `DIESEL`, `FLUIDS`, `HYD. OIL`, `GEAR OIL`) extracted from your attached SNL price list (`app/src/main/res/drawable/img_snl_pricelist_source.jpg`) are included in `AttachedPriceListCatalog.kt`:
- **Rows 1–33 and 37–59:** Exact PKR prices from the `Sole Distributor` column (`Reborn 700ml x 12 = PKR 5,105` through `Gear Oil 75W-85 20 Ltr = PKR 11,000`).
- **Rows 34, 35, 36 (`Liquix L-7000+ SP 5w30` in `1ltr x 12`, `3ltr x 4`, `4 ltr x 4`):** Marked as `-` in the source image and flagged as `isUncertainOrMissingPrice = true` in the AI Price List Image Reader for Admin review.

---

## 4. Deploying Cloud Functions (`functions/`)

```bash
cd functions
npm install
npm run build
firebase deploy --only functions
```
