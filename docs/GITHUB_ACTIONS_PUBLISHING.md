# GitHub Actions Build & Google Play Publishing Guide

This guide covers every one-time setup step and recurring release procedure required to build signed **Rimalux** (`com.cptodix.rimalux.oled.wallpaper`) packages and publish them directly from GitHub Actions.

---

## 1. Overview of the Two Manual Workflows

Both GitHub Actions workflows are **strictly manually triggered** (`workflow_dispatch`) from the **Actions** tab on GitHub—neither workflow ever runs automatically on commits or pull requests.

| Workflow | File | Source Built | Generated Files | Purpose |
|---|---|---|---|---|
| **1. Build & Publish APK/AAB to GitHub Release** | `.github/workflows/release-github.yml` | Latest Git version tag (`vX.Y.Z`) | `rimalux_v<version>_<YYYYMMDD>.apk`<br>`rimalux_v<version>_<YYYYMMDD>.aab` | Phone sideloading (`.apk`) + **first-time manual upload** to Google Play Console (`.aab`) + public/private GitHub Release archive |
| **2. Build & Publish to Google Play Console** | `.github/workflows/publish-google-play.yml` | Latest Git version tag (`vX.Y.Z`) by default (or optional tag/branch override) | `rimalux_v<version>_<YYYYMMDD>.aab` | Automated direct upload to Google Play Console (`internal`, `alpha`, `beta`, or `production`) for **all subsequent releases** |

> **Why is the very first Google Play upload manual?**
> Google Play's Developer API does **not** permit creating a brand-new app or uploading `versionCode = 1` via API before an app's package name (`com.cptodix.rimalux.oled.wallpaper`) and upload signing certificate are registered in Google Play Console.
> - **Release #1 (`v1.0.0`, `versionCode = 1`)**: Run **Workflow 1**, download `rimalux_v1.0.0_<YYYYMMDD>.aab` from GitHub Releases, and upload it manually inside Google Play Console.
> - **Release #2+ (`v1.0.1+`, `versionCode = 2+`)**: Run **Workflow 2** to build and publish directly to Google Play Console from GitHub! (You can also run **Workflow 1** anytime you want `.apk` + `.aab` posted to GitHub Releases.)

---

## 2. Step 1 — Generate & Back Up Your Upload Keystore

### Critical Alias Requirement (`upload`)

In `app/build.gradle.kts`, the release signing configuration requires the key alias to be named **`upload`**:

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

Run this command locally on a machine with JDK 17/21 installed (`keytool` is included with the JDK):

```bash
keytool -genkeypair -v \
  -storetype PKCS12 \
  -keystore my-upload-key.jks \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

- Enter a strong password when prompted (for PKCS12 keystores, `STORE_PASSWORD` and `KEY_PASSWORD` are the same password).
- Fill in your creator/studio certificate fields (e.g., `CN=Cptodix Studio`, `O=Cptodix Studio`, etc.).
- **Back up `my-upload-key.jks` and its passwords immediately** in an offline password manager / encrypted vault. Never commit `my-upload-key.jks` to Git (`.gitignore` excludes `*.jks`).

*(Optional)* **If you already have an existing keystore with a different alias**, rename its alias to `upload` before encoding:

```bash
keytool -changealias \
  -keystore my-upload-key.jks \
  -alias YOUR_OLD_ALIAS \
  -destalias upload
```

### 2.2 Base64-Encode the Keystore for GitHub Secrets

Convert `my-upload-key.jks` into a single Base64 text string and copy it to your clipboard:

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

## 3. Step 2 — Configure GitHub Repository Settings & Secrets

### 3.1 Verify GitHub Actions Permissions

1. Open your GitHub repository -> **Settings** -> **Actions** -> **General**.
2. Scroll to **Workflow permissions** and select **Read and write permissions**, then click **Save** (this allows Workflow 1's built-in `GITHUB_TOKEN` to create GitHub Releases and attach `.apk` / `.aab` assets).

### 3.2 Add Repository Secrets

Go to **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret** and add:

| Secret Name | Used By | Value to Paste |
|---|---|---|
| `UPLOAD_KEYSTORE_BASE64` | Workflow 1 & 2 | Contents of `keystore.b64.txt` (Base64 string of `my-upload-key.jks` containing alias `upload`) |
| `STORE_PASSWORD` | Workflow 1 & 2 | Keystore password chosen when creating `my-upload-key.jks` |
| `KEY_PASSWORD` | Workflow 1 & 2 | Key password for alias `upload` (same as `STORE_PASSWORD` for standard PKCS12 keystores) |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | Workflow 2 only | Full raw JSON contents of your Google Cloud Service Account key (created in **Step 4** below — you can run Workflow 1 before adding this fourth secret!) |

---

## 4. Step 3 — First Build (`v1.0.0`) & Manual Initial Upload to Google Play Console

### 4.1 Tag `v1.0.0` and Run Workflow 1

1. Confirm `app/build.gradle.kts` has:
   ```kotlin
   versionCode = 1
   versionName = "1.0.0"
   ```
2. Create and push your initial Git version tag:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
3. In GitHub, open **Actions** -> **Build & Publish APK/AAB to GitHub Release** -> click **Run workflow** -> **Run workflow**.
4. Once complete (~3–5 minutes), open your repository's **Releases** page on GitHub. You will find both signed files ready for download:
   - `rimalux_v1.0.0_<YYYYMMDD>.apk` — copy to your Android phone to sideload and test the exact release build.
   - `rimalux_v1.0.0_<YYYYMMDD>.aab` — keep ready for your first Google Play Console upload below.

### 4.2 Complete First-Time Setup in Google Play Console

1. Sign in to [Google Play Console](https://play.google.com/console) and click **Create app**:
   - **App name:** `Rimalux`
   - **Default language:** `English (United States) – en-US`
   - **App or game:** `App`
   - **Free or paid:** `Free`
2. Open **Monitor and improve -> Policy and programs -> App content** (or the **Dashboard** setup checklist) and complete each declaration for Rimalux:
   - **Privacy Policy:** Provide a public HTTPS URL hosting your privacy policy stating that Rimalux generates wallpapers locally on-device and collects, stores, or transmits zero personal data.
   - **App access:** Select *"All functionality in my app is available without any access restrictions"* (no login/credentials required).
   - **Ads:** Select *"No, my app does not contain ads"*.
   - **Content rating:** Complete the questionnaire under the *Utility / Productivity / Personalization* category (no user-generated online content, violence, gambling, or location sharing).
   - **Target audience and content:** Select target age groups **`18 and over`** (or `13–15`, `16–17`, `18 and over` — avoid selecting under-13 age groups unless you also want to enroll in Google Play's Families policy program).
   - **Data safety:** Declare **No** to *"Does your app collect or share any of the required user data types?"* (Rimalux does not request `INTERNET` permission and performs zero analytics or network tracking).
   - **Government / Financial / Health apps:** Select **No** / None.
3. Open **Grow users -> Store presence -> Main store listing** and upload required graphics:
   - **Short description** (up to 80 characters) & **Full description** (up to 4,000 characters).
   - **App icon:** `512 × 512 px` 32-bit PNG.
   - **Feature graphic:** `1024 × 500 px` PNG or JPEG.
   - **Phone screenshots:** 2–8 screenshots (`9:16` or `16:9`, 320–3840 px) + optional 7-inch / 10-inch tablet screenshots.
4. **Upload the First `.aab` Manually (Registers Package Name & Upload Key):**
   - Go to **Test and release -> Testing -> Internal testing** (recommended first so you can test Play Store installation immediately; or go to Closed testing / Production).
   - Click **Create new release**.
   - Ensure **Play App Signing** is enabled (Google manages the final distribution key and registers the `upload` certificate inside your `.aab` as your authorized **Upload Key**).
   - Upload `rimalux_v1.0.0_<YYYYMMDD>.aab` downloaded from **Workflow 1**.
   - Enter release notes, click **Next** -> **Save** (and **Start rollout to Internal testing** once all required dashboard tasks are green).

> **Note for Personal Play Developer Accounts (Created After Nov 13, 2023):**
> Google requires new personal developer accounts to run a **Closed testing** track with at least **12 opted-in testers for 14 continuous days** before unlocking Production access. You can push updates directly to `internal` or `alpha` (Closed testing) via **Workflow 2** throughout that testing period!

---

## 5. Step 4 — Set Up Google Cloud Service Account for Automated Publishing (Workflow 2)

Once `com.cptodix.rimalux.oled.wallpaper` exists in Google Play Console with at least one manually uploaded `.aab`, connect Google Cloud so GitHub Actions can upload future releases directly:

### 5.1 Enable the API & Create a Service Account in Google Cloud Console

1. Open [Google Cloud Console](https://console.cloud.google.com/) and create a project (e.g., `rimalux-play-publishing`) or select an existing project.
2. Enable the **Google Play Android Developer API**:
   - Navigate to **APIs & Services -> Library**.
   - Search for **`Google Play Android Developer API`**, open it, and click **Enable**.
3. Create the Service Account:
   - Navigate to **IAM & Admin -> Service Accounts** -> click **Create service account**.
   - **Service account name:** `rimalux-github-publisher`
   - Click **Create and continue** -> skip optional GCP IAM roles (**Continue**) -> click **Done**.
   - Copy the generated email address (looks like `rimalux-github-publisher@<project-id>.iam.gserviceaccount.com`).
4. Create and download the JSON key:
   - Click on the newly created service account -> open the **Keys** tab.
   - Click **Add key -> Create new key** -> select **JSON** -> click **Create**.
   - Open the downloaded `.json` file in a text editor, copy its **entire raw JSON content**, and save it as the `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` secret in your GitHub repository (**Settings -> Secrets and variables -> Actions**).
   - Delete the downloaded `.json` file from your computer once saved in GitHub Secrets.

### 5.2 Grant Release Permissions in Google Play Console

1. Open [Google Play Console](https://play.google.com/console) -> click **Users and permissions** in the left sidebar.
2. Click **Invite new users**.
3. **Email address:** Paste the service account email (`rimalux-github-publisher@...iam.gserviceaccount.com`). *(Leave "Set access expiry date" unchecked.)*
4. Under **Permissions -> App permissions**, click **Add app**, select **Rimalux (`com.cptodix.rimalux.oled.wallpaper`)**, click **Apply**, and check:
   - `View app information (read-only)`
   - `Release to production, exclude devices, and use Play App Signing`
   - `Release apps to testing tracks`
   - `Manage testing tracks and edit tester lists`
5. Click **Apply** -> **Invite user** -> **Send invite**.
   - Service accounts join immediately without needing to click an invitation email.
   - Allow **5–15 minutes** for Google Play's API permission cache to propagate before triggering Workflow 2 for the first time.

---

## 6. Step 5 — Publishing Subsequent Updates (`v1.0.1`, `v1.1.0`, …)

For every new release of Rimalux, follow this checklist:

### 6.1 Bump Version & Update Release Notes

1. Edit `app/build.gradle.kts` and increment **both** `versionCode` and `versionName`:
   ```kotlin
   versionCode = 2          // MUST increase by at least +1 on every Google Play upload
   versionName = "1.0.1"    // Automatically updates the in-app "Rimalux v1.0.1..." footer too
   ```
2. Update `distribution/whatsnew/whatsnew-en-US` with concise release notes for this version:
   - **Strict 500-character limit** per language file (Workflow 2 automatically checks character count before building so Google Play never rejects your upload).
   - If you add localized Play Store listings later (e.g., `ja-JP` or `de-DE`), simply add matching files such as `distribution/whatsnew/whatsnew-ja-JP`.
3. Commit and push the version bump + Git tag:
   ```bash
   git add app/build.gradle.kts distribution/whatsnew/
   git commit -m "chore(release): v1.0.1 (versionCode 2)"
   git push origin main

   git tag v1.0.1
   git push origin v1.0.1
   ```

### 6.2 Trigger Workflow 2 (Publish to Google Play Console)

1. In GitHub, open **Actions** -> **Build & Publish to Google Play Console** -> click **Run workflow**.
2. Configure the workflow inputs:
   - **`track`**:
     - `internal` — Instant rollout to internal testers (no review wait; best for smoke-testing).
     - `alpha` — Closed testing track.
     - `beta` — Open testing track.
     - `production` — Live Google Play Store release.
   - **`release_status`**:
     - `completed` — Standard full rollout once your app has passed its initial Google Play review.
     - `draft` — **Use `draft` if your app is still in "Draft" state / awaiting its very first Google Play review** (Google Play's API requires `draft` releases until the app has been published at least once).
     - `inProgress` — Staged rollout to a percentage of users (requires setting `user_fraction`, e.g. `0.2` for `20%`).
     - `halted` — Uploads a halted release.
   - **`user_fraction`**: Leave empty unless `release_status` is `inProgress`.
   - **`in_app_update_priority`**: `0` (default) to `5`.
   - **`git_ref`**: Leave empty to automatically build from the **latest version tag** (`v1.0.1`), or enter a specific tag/branch name if needed.
3. Click **Run workflow**.
   - Workflow 2 builds `rimalux_v1.0.1_<YYYYMMDD>.aab`, archives it as a downloadable GitHub Actions artifact, and uploads it directly to Google Play Console!

*(Optional)* Whenever you also want `.apk` + `.aab` binaries published on your GitHub **Releases** tab for sideloading, go to **Actions -> Build & Publish APK/AAB to GitHub Release** and click **Run workflow**.

---

## 7. Troubleshooting Reference

| Error Message | Cause | Fix |
|---|---|---|
| `No version tag (e.g., v1.0.0) was found in this repository` | No `vX.Y.Z` tag has been pushed to GitHub yet | Run `git tag v1.0.0 && git push origin v1.0.0` |
| `Keystore check failed: ensure STORE_PASSWORD is valid and the keystore contains key alias 'upload'` | Wrong `STORE_PASSWORD` or keystore was created with an alias other than `upload` | Verify `STORE_PASSWORD` secret, or rename alias to `upload` via `keytool -changealias` and re-upload `UPLOAD_KEYSTORE_BASE64` |
| `Version code X has already been used` | Google Play already received an `.aab` with the same `versionCode` | Increment `versionCode` in `app/build.gradle.kts`, commit, create a new tag (or move the tag if unreleased), and re-run |
| `Only releases with status draft may be created on draft app` | App has not completed its initial Google Play review / store setup yet | Re-run Workflow 2 with `release_status` set to **`draft`**, then click **Review and roll out** once inside Play Console |
| `The current user has insufficient permissions to perform the requested operation` (HTTP 403) | Service Account hasn't been granted App Permissions in Play Console, API isn't enabled, or Play permissions are still propagating | Check **Play Console -> Users and permissions**, verify **Google Play Android Developer API** is enabled in the same GCP project, and wait 10–15 minutes |
| `Package not found: com.cptodix.rimalux.oled.wallpaper` (HTTP 404) | First `.aab` was never uploaded manually in Google Play Console | Upload `rimalux_v1.0.0_<YYYYMMDD>.aab` from **Workflow 1** manually in Google Play Console once |
