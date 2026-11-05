# GitHub Actions Build & Google Play Publishing Guide

This guide covers every one-time preparation step and recurring release procedure required to build signed **Rimalux** (`com.cptodix.rimalux.oled.wallpaper`) packages and publish them directly from GitHub Actions using standard, well-documented actions.

---

## 1. Overview of the Two Manual Workflows & Ready-Made Actions

Both GitHub Actions workflows are **strictly manually triggered** (`workflow_dispatch`) from the **Actions** tab on GitHub—neither workflow ever runs automatically on pushes or pull requests.

| Workflow | File | Source Built | Generated Files | Purpose |
|---|---|---|---|---|
| **1. Build & Publish APK/AAB to GitHub Release** | `.github/workflows/release-github.yml` | Latest Git semantic version tag (`vX.Y.Z`) | `rimalux_v<version>_<YYYYMMDD>.apk`<br>`rimalux_v<version>_<YYYYMMDD>.aab`<br>`rimalux_v<version>_<YYYYMMDD>_mapping.txt` | Phone sideloading (`.apk`) + **first-time manual upload** to Google Play Console (`.aab`) + GitHub Releases archive |
| **2. Build & Publish to Google Play Console** | `.github/workflows/publish-google-play.yml` | Latest Git semantic version tag (`vX.Y.Z`) by default (or optional `git_ref` override) | `rimalux_v<version>_<YYYYMMDD>.aab` | Automated direct publishing to Google Play Console (`internal`, `alpha`, `beta`, or `production`) for **all subsequent releases** (uploads AAB with R8 deobfuscation mapping + native debug symbols) |
| **3. Bump Version & Create Tag** | `.github/workflows/version-increment.yml` | Current branch | Updated `build.gradle.kts` + new Git tag (`vX.Y.Z`) | Increments `versionCode` and `versionName` (`patch`, `minor`, or `major`), commits, and pushes a release tag |
| **4. Run Pipeline Back to Back** | `.github/workflows/workflow-orchestrator.yml` | Current branch | New version tag + published `.aab` | Chains Workflow 3 (bump & tag) directly into Workflow 2 (build & upload to Google Play) in one click |

### Ready-Made GitHub Actions Used

Instead of custom shell/Python plumbing, both workflows compose battle-tested, well-documented official and community actions:

| Action | Used In | Role |
|---|---|---|
| [`actions/checkout@v4`](https://github.com/actions/checkout) | Workflow 1 & 2 | Clones repository history/tags and checks out the target release tag cleanly |
| [`actions-ecosystem/action-get-latest-tag@v1`](https://github.com/actions-ecosystem/action-get-latest-tag) | Workflow 1 & 2 | Resolves the latest semantic version Git tag (`vX.Y.Z`) automatically |
| [`actions/setup-java@v4`](https://github.com/actions/setup-java) | Workflow 1 & 2 | Provisions Eclipse Temurin JDK 21 |
| [`android-actions/setup-android@v3`](https://github.com/android-actions/setup-android) | Workflow 1 & 2 | Configures Android SDK command-line tools and environment paths |
| [`gradle/actions/setup-gradle@v4`](https://github.com/gradle/actions) | Workflow 1 & 2 | Installs Gradle `9.3.1`, manages dependency caching, and generates build summaries |
| [`timheuer/base64-to-file@v2`](https://github.com/timheuer/base64-to-file) | Workflow 1 & 2 | Decodes `UPLOAD_KEYSTORE_BASE64` into an isolated runner temp file (`RUNNER_TEMP`) |
| [`actions/upload-artifact@v4`](https://github.com/actions/upload-artifact) | Workflow 1 & 2 | Attaches signed `.apk` / `.aab` packages to the GitHub Actions workflow run |
| [`softprops/action-gh-release@v2`](https://github.com/softprops/action-gh-release) | Workflow 1 | Creates/updates the GitHub Release, generates release notes, and uploads `.apk` + `.aab` |
| [`google-github-actions/auth@v2`](https://github.com/google-github-actions/auth) | Workflow 2 | Official Google Cloud authentication action (supports both **Workload Identity Federation OIDC** and **Service Account JSON**) and auto-cleans temporary credential files |
| [`r0adkll/upload-google-play@v1`](https://github.com/r0adkll/upload-google-play) | Workflow 2 | Uploads the signed `.aab` and localized release notes (`distribution/whatsnew`) to Google Play Console using credentials from `google-github-actions/auth@v2` |

> **Why is the very first Google Play upload manual?**
> Google Play's Developer API does **not** permit creating a brand-new app or uploading `versionCode = 1` via API before an app's package name (`com.cptodix.rimalux.oled.wallpaper`) and upload signing certificate are registered in Google Play Console.
> - **Release #1 (`v1.0.0`, `versionCode = 1`)**: Run **Workflow 1**, download `rimalux_v1.0.0_<YYYYMMDD>.aab` from GitHub Releases, and upload it manually inside Google Play Console once.
> - **Release #2+ (`v1.0.1+`, `versionCode = 2+`)**: Run **Workflow 2** to build and publish directly to Google Play Console from GitHub! (You can still run **Workflow 1** anytime you also want `.apk` + `.aab` posted to GitHub Releases.)

---

## 2. Step 1 — Generate & Back Up Your Upload Keystore

### Critical Alias Requirement (`upload`)

In `app/build.gradle.kts`, the release signing configuration expects the key alias **`upload`** and reads `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` from environment variables:

```kotlin
signingConfigs {
  create("release") {
    val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
    storeFile = file(keystorePath)
    storePassword = System.getenv("STORE_PASSWORD")
    keyAlias = "upload"
    keyPassword = System.getenv("KEY_PASSWORD")
  }
}
```

### 2.1 Generate a New Upload Keystore

Run this command locally on a machine with JDK 17/21 installed (`keytool` comes bundled with the JDK):

```bash
keytool -genkeypair -v \
  -storetype PKCS12 \
  -keystore my-upload-key.jks \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

- Enter a strong password when prompted (for PKCS12 keystores, `STORE_PASSWORD` and `KEY_PASSWORD` use the same password).
- Fill in your creator/studio certificate fields (e.g., `CN=Cptodix Studio`, `O=Cptodix Studio`).
- **Back up `my-upload-key.jks` and its password immediately** in an offline password manager / encrypted vault. Never commit `my-upload-key.jks` to Git (`.gitignore` excludes `*.jks`).

*(Optional)* **If you already have an existing keystore with a different alias**, rename its alias to `upload` before encoding:

```bash
keytool -changealias \
  -keystore my-upload-key.jks \
  -alias YOUR_OLD_ALIAS \
  -destalias upload
```

### 2.2 Base64-Encode the Keystore for `timheuer/base64-to-file@v2`

Convert `my-upload-key.jks` into a single-line Base64 string:

- **Linux:**
  ```bash
  base64 -w 0 my-upload-key.jks > keystore.b64.txt
  ```
- **macOS:**
  ```bash
  base64 -i my-upload-key.jks | tr -d '\n' > keystore.b64.txt
  ```
- **Windows (PowerShell):**
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("my-upload-key.jks")) | Set-Content -NoNewline keystore.b64.txt
  ```

*(Delete `keystore.b64.txt` right after pasting its contents into GitHub Secrets in Step 2.)*

---

## 3. Step 2 — Configure GitHub Repository Permissions & Signing Secrets

### 3.1 Verify GitHub Actions Workflow Permissions

1. Open your GitHub repository -> **Settings** -> **Actions** -> **General**.
2. Scroll to **Workflow permissions**, select **Read and write permissions**, and click **Save** (required so `softprops/action-gh-release@v2` in Workflow 1 can create GitHub Releases and attach `.apk` / `.aab` assets).

### 3.2 Add Signing Secrets (Used by Workflow 1 & Workflow 2)

Go to **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret** and add:

| Secret Name | Used By | Value to Paste |
|---|---|---|
| `UPLOAD_KEYSTORE_BASE64` | Workflow 1 & 2 | Single-line Base64 text from `keystore.b64.txt` (decoded at build time by `timheuer/base64-to-file@v2`) |
| `STORE_PASSWORD` | Workflow 1 & 2 | Keystore password chosen when creating `my-upload-key.jks` |
| `KEY_PASSWORD` | Workflow 1 & 2 | Key password for alias `upload` (same as `STORE_PASSWORD` for standard PKCS12 keystores) |

*(Google Play authentication secrets for `google-github-actions/auth@v2` in Workflow 2 are added in **Step 4** below—you can run Workflow 1 right away with just the three secrets above!)*

---

## 4. Step 3 — First Build (`v1.0.0`) & Manual Initial Upload to Google Play Console

### 4.1 Tag `v1.0.0` and Run Workflow 1

1. Confirm `app/build.gradle.kts` has:
   ```kotlin
   versionCode = 1
   versionName = "1.0.0"
   ```
2. Create and push your initial semantic version Git tag:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
3. In GitHub, open **Actions** -> **Build & Publish APK/AAB to GitHub Release** -> click **Run workflow** -> **Run workflow**.
4. Once finished (~3–5 minutes), open your repository's **Releases** page on GitHub. Both signed packages will be attached:
   - `rimalux_v1.0.0_<YYYYMMDD>.apk` — copy to your Android phone to sideload and verify the release APK.
   - `rimalux_v1.0.0_<YYYYMMDD>.aab` — keep ready for your first manual Google Play Console upload below.

### 4.2 Complete First-Time Setup in Google Play Console

1. Sign in to [Google Play Console](https://play.google.com/console) and click **Create app**:
   - **App name:** `Rimalux`
   - **Default language:** `English (United States) – en-US`
   - **App or game:** `App`
   - **Free or paid:** `Free`
2. Open **Monitor and improve -> Policy and programs -> App content** (or the **Dashboard** setup checklist) and complete each declaration for Rimalux:
   - **Privacy Policy:** Provide a public HTTPS URL hosting your privacy policy stating that Rimalux synthesizes wallpapers locally on-device and collects, stores, or transmits zero personal data.
   - **App access:** Select *"All functionality in my app is available without any access restrictions"*.
   - **Ads:** Select *"No, my app does not contain ads"*.
   - **Content rating:** Complete the questionnaire under *Utility / Productivity / Personalization* (no user-generated online content, violence, gambling, or location sharing).
   - **Target audience and content:** Select target age groups **`18 and over`** (or `13–15`, `16–17`, `18 and over` — avoid under-13 age groups unless enrolling in Google Play's Families policy program).
   - **Data safety:** Rimalux itself runs 100% locally on-device with zero developer servers, analytics, or ad SDKs; only the official **Google Play Billing Library** communicates with Google Play for voluntary tip purchases (declare according to Google Play's [Play Billing Data Safety guide](https://developer.android.com/google/play/billing/data-safety)).
   - **Government / Financial / Health apps:** Select **No** / None.
3. Open **Grow users -> Store presence -> Main store listing** and upload required graphics:
   - **Short description** (`<= 80` chars) & **Full description** (`<= 4,000` chars).
   - **App icon:** `512 × 512 px` 32-bit PNG.
   - **Feature graphic:** `1024 × 500 px` PNG or JPEG.
   - **Phone screenshots:** 2–8 screenshots (`9:16` or `16:9`, 320–3840 px) + optional 7-inch / 10-inch tablet screenshots.
4. **Upload the First `.aab` Manually (Registers Package Name & Upload Key):**
   - Navigate to **Test and release -> Testing -> Internal testing** (recommended first for immediate Play Store install testing; or Closed testing / Production).
   - Click **Create new release**.
   - Ensure **Play App Signing** is enabled (Google manages the final distribution key and registers your `upload` certificate as the authorized **Upload Key**).
   - Upload `rimalux_v1.0.0_<YYYYMMDD>.aab` downloaded from **Workflow 1**.
   - Enter release notes, click **Next** -> **Save** (and **Start rollout to Internal testing** once required dashboard items are complete).

> **Note for Personal Play Developer Accounts (Created After Nov 13, 2023):**
> Google requires new personal developer accounts to run a **Closed testing** track with at least **12 opted-in testers for 14 continuous days** before unlocking Production access. You can publish updates directly to `internal` or `alpha` (Closed testing) via **Workflow 2** throughout that testing period.

### 4.3 Configure "Send a Thanks" Tip Product IDs in Google Play Console

Once an `.aab` built with Google Play Billing (`com.android.vending.BILLING` + `com.android.billingclient:billing-ktx`) has been uploaded to any track (e.g., Internal testing) and a **Payments profile / Merchant account** is linked under **Settings -> Payments profile**, Google Play Console unlocks **Monetize with Play -> Products -> In-app products (One-time products)**.

Create and **Activate** these **5 One-Time Products** using the exact Product IDs below (defined in `TipProduct.kt`):

| Tip Option in App | Product ID (Exact) | Product Name Example | Description Example | Reference Price (USD) |
|---|---|---|---|---|
| `$1` (`btn_tip_1`) | `rimalux_tip_1` | `Small Thanks ($1 Tip)` | `Voluntary $1 appreciation tip for Rimalux.` | `$1.00` |
| `$3` (`btn_tip_3`) | `rimalux_tip_3` | `Kind Thanks ($3 Tip)` | `Voluntary $3 appreciation tip for Rimalux.` | `$3.00` |
| `$5` (`btn_tip_5`) | `rimalux_tip_5` | `Warm Thanks ($5 Tip)` | `Voluntary $5 appreciation tip for Rimalux.` | `$5.00` |
| `$10` (`btn_tip_10`) | `rimalux_tip_10` | `Generous Thanks ($10 Tip)` | `Voluntary $10 appreciation tip for Rimalux.` | `$10.00` |
| `$20` (`btn_tip_20`) | `rimalux_tip_20` | `Super Thanks ($20 Tip)` | `Voluntary $20 appreciation tip for Rimalux.` | `$20.00` |

- **Consumable / Repeat Tipping**: `PlayBillingManager.kt` automatically calls `consumeAsync` as soon as a purchase reaches `PURCHASED` state, which both acknowledges the transaction with Google Play (preventing 3-day auto-refunds) and allows supporters to tip again later if they choose.
- **Automatic Local Currency Display**: When connected to Google Play Store, the `Send a Thanks` dialog automatically displays Google Play's localized currency price (`formattedPrice`, e.g., `Rp 16.000`, `$1.00`, `€1,09`) for each active product ID.
- *(Optional)* If Google Play Console prompts for **Purchase options** on One-Time Products, create a standard **Buy** purchase option, mark it **Backwards compatible**, set regional prices, and click **Activate**.

---

## 5. Step 4 — Set Up `google-github-actions/auth` & Google Play Console Access (Workflow 2)

Once `com.cptodix.rimalux.oled.wallpaper` has had its first `.aab` uploaded manually, configure Google Cloud and Google Play Console so `google-github-actions/auth@v2` + `r0adkll/upload-google-play@v1` can publish subsequent updates directly.

### 5.1 Enable the Google Play Developer API & Create a Service Account

1. Open [Google Cloud Console](https://console.cloud.google.com/) and create a project (e.g., `rimalux-play-publishing`) or select an existing project.
2. Enable the **Google Play Android Developer API**:
   - Navigate to **APIs & Services -> Library**.
   - Search for **`Google Play Android Developer API`**, open it, and click **Enable**.
3. Create the Service Account:
   - Navigate to **IAM & Admin -> Service Accounts** -> click **Create service account**.
   - **Service account name:** `rimalux-github-publisher`
   - Click **Create and continue** -> skip optional GCP IAM roles (**Continue**) -> click **Done**.
   - Copy the generated email address (`rimalux-github-publisher@<project-id>.iam.gserviceaccount.com`).

### 5.2 Authenticate `google-github-actions/auth@v2` (Choose Option A or Option B)

Workflow 2 automatically detects whichever authentication method you configure in GitHub Secrets:

#### Option A (Recommended by Google — Keyless OIDC via Workload Identity Federation)
No long-lived JSON key is ever created or stored. GitHub exchanges a short-lived OIDC token (`permissions: id-token: write`) directly with Google Cloud:

1. In Google Cloud Console (or Cloud Shell), create a Workload Identity Pool & GitHub OIDC Provider restricted to your repository (`<GITHUB_OWNER>/<GITHUB_REPO>`):
   ```bash
   PROJECT_ID="your-gcp-project-id"
   SA_EMAIL="rimalux-github-publisher@${PROJECT_ID}.iam.gserviceaccount.com"
   GITHUB_REPO="your-github-username/your-repo-name"

   gcloud services enable iamcredentials.googleapis.com --project="${PROJECT_ID}"

   gcloud iam workload-identity-pools create "github-pool" \
     --project="${PROJECT_ID}" \
     --location="global" \
     --display-name="GitHub Actions Pool"

   POOL_ID=$(gcloud iam workload-identity-pools describe "github-pool" \
     --project="${PROJECT_ID}" --location="global" --format="value(name)")

   gcloud iam workload-identity-pools providers create-oidc "github-provider" \
     --project="${PROJECT_ID}" \
     --location="global" \
     --workload-identity-pool="github-pool" \
     --display-name="GitHub OIDC Provider" \
     --attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository" \
     --attribute-condition="assertion.repository == '${GITHUB_REPO}'" \
     --issuer-uri="https://token.actions.githubusercontent.com"

   gcloud iam service-accounts add-iam-policy-binding "${SA_EMAIL}" \
     --project="${PROJECT_ID}" \
     --role="roles/iam.workloadIdentityUser" \
     --member="principalSet://iam.googleapis.com/${POOL_ID}/attribute.repository/${GITHUB_REPO}"

   gcloud iam workload-identity-pools providers describe "github-provider" \
     --project="${PROJECT_ID}" \
     --location="global" \
     --workload-identity-pool="github-pool" \
     --format="value(name)"
   ```
2. Add two GitHub Repository Secrets (**Settings -> Secrets and variables -> Actions**):
   - **`GCP_WORKLOAD_IDENTITY_PROVIDER`**: Full provider resource name printed by the last command above (`projects/<PROJECT_NUMBER>/locations/global/workloadIdentityPools/github-pool/providers/github-provider`).
   - **`GCP_SERVICE_ACCOUNT`**: `rimalux-github-publisher@<project-id>.iam.gserviceaccount.com`.

#### Option B (Quick Setup — Service Account JSON Key)
If you prefer a single secret instead of Workload Identity Federation:

1. In Google Cloud Console -> **IAM & Admin -> Service Accounts**, click `rimalux-github-publisher` -> **Keys** tab -> **Add key -> Create new key** -> **JSON** -> **Create**.
2. Open the downloaded `.json` file in a text editor, copy its **entire raw JSON contents**, and add one GitHub Repository Secret:
   - **`GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`**: Raw JSON contents of the downloaded service account key.
3. Delete the downloaded `.json` file from your local disk. *(Leave `GCP_WORKLOAD_IDENTITY_PROVIDER` unset so Workflow 2 uses `credentials_json` automatically.)*

### 5.3 Grant Release Permissions in Google Play Console

1. Open [Google Play Console](https://play.google.com/console) -> **Users and permissions** -> **Invite new users**.
2. **Email address:** Paste `rimalux-github-publisher@<project-id>.iam.gserviceaccount.com` *(leave access expiry unchecked)*.
3. Under **Permissions -> App permissions**, click **Add app**, select **Rimalux (`com.cptodix.rimalux.oled.wallpaper`)**, click **Apply**, and enable:
   - `View app information (read-only)`
   - `Release to production, exclude devices, and use Play App Signing`
   - `Release apps to testing tracks`
   - `Manage testing tracks and edit tester lists`
4. Click **Apply** -> **Invite user** -> **Send invite**.
   - Service accounts activate immediately without email confirmation; wait **5–15 minutes** for Google Play's permission cache to propagate before running Workflow 2 for the first time.

---

## 6. Step 5 — Publishing Subsequent Updates (`v1.0.1`, `v1.1.0`, …)

### 6.1 Bump Version & Update Release Notes

1. Edit `app/build.gradle.kts` and increment **both** `versionCode` and `versionName`:
   ```kotlin
   versionCode = 2          // MUST increase strictly on every Google Play upload
   versionName = "1.0.1"    // Also updates the Studio sheet "Rimalux v1.0.1..." footer automatically
   ```
2. Edit `distribution/whatsnew/whatsnew-en-US` with concise release notes (`<= 500` characters per language file, automatically picked up by `r0adkll/upload-google-play@v1` via `whatsNewDirectory: distribution/whatsnew`).
   - When you add additional Play Store languages later (e.g., `ja-JP`, `de-DE`), place matching `whatsnew-<BCP47>` files in `distribution/whatsnew/`.
3. Commit and push the version bump + semantic version tag:
   ```bash
   git add app/build.gradle.kts distribution/whatsnew/
   git commit -m "chore(release): v1.0.1 (versionCode 2)"
   git push origin main

   git tag v1.0.1
   git push origin v1.0.1
   ```

### 6.2 Trigger Workflow 2 (`publish-google-play.yml`)

1. In GitHub, open **Actions** -> **Build & Publish to Google Play Console** -> click **Run workflow**.
2. Select your release parameters:
   - **`track`**: `internal` (instant testing), `alpha` (Closed testing), `beta` (Open testing), or `production`.
   - **`release_status`**:
     - `completed` — Full rollout (use once your app has completed its initial Google Play review).
     - `draft` — **Required if your app is still in "Draft" status** awaiting its first Play Store review.
     - `inProgress` — Staged percentage rollout (requires `user_fraction`, e.g., `0.2` for `20%`).
     - `halted` — Uploads a halted release.
   - **`user_fraction`**: Decimal between `0.01` and `0.99` (only used when `release_status` is `inProgress`).
   - **`in_app_update_priority`**: `0` (default) to `5`.
   - **`git_ref`**: Leave blank to automatically build from the latest semantic version tag (`v1.0.1`), or enter a specific tag/branch override.
3. Click **Run workflow**.
   - Workflow 2 builds `rimalux_v1.0.1_<YYYYMMDD>.aab`, archives it as a workflow artifact, authenticates via `google-github-actions/auth@v2`, and publishes directly to Google Play Console via `r0adkll/upload-google-play@v1`.

*(Optional)* Whenever you also want `.apk` + `.aab` binaries published on GitHub **Releases** for phone sideloading, run **Build & Publish APK/AAB to GitHub Release** (**Workflow 1**) as well.

---

## 7. Troubleshooting Reference

| Error Message | Cause | Fix |
|---|---|---|
| `No semantic version tag (e.g., v1.0.0) found` | No SemVer tag (`vX.Y.Z`) has been pushed to GitHub | Run `git tag v1.0.0 && git push origin v1.0.0` |
| `Failed to read key upload from store` / Gradle `signReleaseBundle` failure | Keystore alias is not `upload`, or `STORE_PASSWORD` / `KEY_PASSWORD` / `UPLOAD_KEYSTORE_BASE64` is mismatched | Confirm `keytool -list -keystore my-upload-key.jks -alias upload` succeeds locally, re-encode with `base64`, and update GitHub Secrets |
| `google-github-actions/auth failed: Specify one of "workload_identity_provider" or "credentials_json"` | Neither `GCP_WORKLOAD_IDENTITY_PROVIDER` nor `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` was added to GitHub Secrets | Add either Option A secrets (`GCP_WORKLOAD_IDENTITY_PROVIDER` + `GCP_SERVICE_ACCOUNT`) or Option B secret (`GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`) |
| `Version code X has already been used` | Google Play already received an `.aab` with that `versionCode` | Increment `versionCode` in `app/build.gradle.kts`, commit, push a new tag, and re-run |
| `Only releases with status draft may be created on draft app` | App has not finished its initial Google Play review yet | Re-run Workflow 2 with `release_status` set to **`draft`**, then complete initial rollout inside Play Console |
| `The current user has insufficient permissions to perform the requested operation` (HTTP 403) | Service Account lacks App Permissions in Play Console, API isn't enabled, or permissions are still propagating | Check **Play Console -> Users and permissions**, verify **Google Play Android Developer API** is enabled in the same GCP project, and wait 10–15 minutes |
| `Package not found: com.cptodix.rimalux.oled.wallpaper` (HTTP 404) | First `.aab` was never uploaded manually in Google Play Console | Upload `rimalux_v1.0.0_<YYYYMMDD>.aab` from **Workflow 1** manually in Google Play Console once |
| `WhatsNew text is too long` | A `distribution/whatsnew/whatsnew-*` file exceeds Google Play's 500-character limit | Shorten the text in `distribution/whatsnew/whatsnew-en-US` to `<= 500` characters |
| `Tidak ada file deobfuscation yang berkaitan dengan App Bundle ini` / Missing deobfuscation file warning | Release build was created without R8 code shrinking and without deobfuscation `mapping.txt` | Resolved: `isMinifyEnabled = true` is configured in `app/build.gradle.kts` release buildType with `-keepattributes SourceFile,LineNumberTable`. AGP automatically packages `mapping.txt` into the `.aab` metadata, and `publish-google-play.yml` uploads it via `r0adkll/upload-google-play@v1`. |
| `App Bundle ini berisi kode native, dan Anda belum mengupload simbol debug` / Missing native debug symbols warning | App dependencies contain prebuilt native `.so` libraries (e.g. ProfileInstaller, graphics) without unstripped debug symbols attached to the bundle | Resolved: `ndk { debugSymbolLevel = "FULL" }` is configured in `app/build.gradle.kts` release buildType. AGP automatically packages the native symbol table into the `.aab` metadata so Play Console can symbolicate native crashes and ANRs. |
| `NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []` | R8 minification stripped the reflective constructor of Room's generated `WorkDatabase_Impl` on app startup | Resolved: Added keep rules in `app/proguard-rules.pro` for `* extends androidx.room.RoomDatabase`, `androidx.work.impl.WorkDatabase_Impl`, workers, and `androidx.startup.Initializer`. |
