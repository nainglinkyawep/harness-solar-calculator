# Harness Engineering – Solar Calculator / HE Solar Design Suite

This repository now includes:

1. **Android APK wrapper** (`android/`) — Play Store app **HE Solar Design Suite** (`com.harnessengineering.hesolardesignsuite`) that loads the full site **https://training.harnessengineering.com** in a mobile WebView.
2. Placeholder HTML (`index.html`) pointing at related calculator artifacts.

## Android app (primary deliverable)

See [`android/README.md`](android/README.md) for build and test instructions.

```bash
cd android
./gradlew assembleDebug
```

## Site content

The training / shop / suite website is hosted separately (WordPress) at https://training.harnessengineering.com and is **not** source-controlled in this repo. Mobile CSS/layout for the site must be changed on that host (or its theme), not here.

## Live HTML preview

After enabling **GitHub Pages**:

- Settings → Pages → Source = Deploy from a branch → main / root
- https://nainglinkyawep.github.io/harness-solar-calculator/
