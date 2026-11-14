# Harness Engineering (Android)

Mobile WebView wrapper for the full Harness Engineering training site:

**https://training.harnessengineering.com/**

Play / app identity:

- Launcher / display name: `Harness Engineering`
- Package / applicationId (unchanged for Play continuity): `com.harnessengineering.hesolardesignsuite`
- Version in this tree: `1.10.0` (`versionCode` 200)

The WordPress / WooCommerce site itself is **not** in this repository. This project only ships the Android shell that loads and navigates the live site.

## Features

- Loads the full training site as the main screen (shop, courses, HE Solar Design Suite pages, account, etc.)
- Keeps `*.harnessengineering.com` navigation inside the WebView
- Opens tel/mailto/external domains in the system browser / apps
- Hardware back = WebView history
- Safe-area / system-bar padding, swipe-to-refresh, loading bar, offline/error retry
- File downloads via `DownloadManager`
- Optional deep links to `https://training.harnessengineering.com/...`

## Build

Requirements: JDK 17+, Android SDK 34.

```bash
cd android
# optional: echo "sdk.dir=$HOME/android-sdk" > local.properties
./gradlew assembleDebug
# release (unsigned unless you configure signing):
./gradlew assembleRelease
```

Debug APK:

`app/build/outputs/apk/debug/app-debug.apk`

(Debug builds use applicationId `com.harnessengineering.hesolardesignsuite.debug` so they can sit beside the Play Store install.)

Release APK (for Play upload after signing):

`app/build/outputs/apk/release/app-release-unsigned.apk`

## Signing / Play upload

Signing keys are **not** in this repo. Configure `signingConfigs` in `app/build.gradle.kts` (or Play App Signing) before uploading a release AAB/APK.

Suggested release command once a keystore is available:

```bash
./gradlew bundleRelease
```

## Test checklist

1. Install debug APK on a device/emulator (API 24+).
2. Confirm home loads `training.harnessengineering.com`.
3. Navigate shop, courses, and suite pages without leaving the app.
4. Tap an external link → opens browser.
5. Press system back → previous in-site page.
6. Airplane mode → error panel + Retry.
7. Trigger a file download if the site offers one.
