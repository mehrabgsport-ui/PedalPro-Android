# PedalPro Android

Official Android app wrapper for https://pedalpro.ir/

## Release 1.4.0
- applicationId: `ir.pedalpro.app`
- versionCode: `5`
- compileSdk / targetSdk: `36`
- minSdk: `26`
- Java: `17`
- Firebase Cloud Messaging enabled
- Native foreground GPS tracking with offline queue
- Android Back navigation follows PedalPro/WebView history before closing
- Store-safe update flow (Google Play / Cafe Bazaar)
- No `REQUEST_INSTALL_PACKAGES` permission in store builds

## Distribution
- `googlePlayRelease`: AAB/APK for Google Play.
- `bazaarRelease`: APK for Cafe Bazaar.

Release artifacts are built by GitHub Actions. Final production artifacts must be signed with the permanent PedalPro release/upload key; the private key must never be committed to this public repository.
