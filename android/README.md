# Harness Engineering (Android)

Mobile WebView wrapper for the full Harness Engineering training site:

**https://training.harnessengineering.com/**

Play / app identity:

- Launcher / display name: `Harness Engineering`
- Package / applicationId (unchanged for Play continuity): `com.harnessengineering.hesolardesignsuite`
- Version in this tree: `1.10.0` (`versionCode` 1646)

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
```

Debug APK:

`app/build/outputs/apk/debug/app-debug.apk`

(Debug builds use applicationId `com.harnessengineering.hesolardesignsuite.debug` so they can sit beside the Play Store install.)

## Signing / Play upload (release AAB)

Signing keys are **not** in this repo. Do not commit `*.jks`, `*.keystore`, or `keystore.properties`.

Create a local `keystore.properties` (or point to one) with:

```properties
storeFile=/absolute/path/to/he-upload-key.jks
storePassword=********
keyAlias=hesolar
keyPassword=********
applicationId=com.harnessengineering.hesolardesignsuite
versionCode=1646
```

Supply that file via any one of:

1. `export HE_KEYSTORE_PROPERTIES=/absolute/path/to/keystore.properties`
2. `./gradlew bundleRelease -Phe.keystore.properties=/absolute/path/to/keystore.properties`
3. In `local.properties`: `he.keystore.properties=/absolute/path/to/keystore.properties`
4. Copy/symlink as `android/keystore.properties` (gitignored)

Then:

```bash
./gradlew bundleRelease
# AAB: app/build/outputs/bundle/release/app-release.aab

./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

Upload the signed AAB to Google Play Console (Play App Signing).
