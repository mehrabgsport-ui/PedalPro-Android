# PedalPro Android

Official Android app wrapper for https://pedalpro.ir/

## Release 1.4.23
- applicationId: `ir.pedalpro.app`
- versionCode: `28`
- compileSdk / targetSdk: `36`
- minSdk: `26`
- Java: `17`
- Firebase Cloud Messaging enabled
- Native foreground GPS tracking with offline queue
- Android Back navigation follows PedalPro/WebView history before closing
- Store-safe update flow (Google Play / Cafe Bazaar / Myket)
- No `REQUEST_INSTALL_PACKAGES` permission in store builds

## Distribution
- `googlePlayRelease`: AAB/APK for Google Play when the Google Play flavor is enabled.
- `bazaarRelease`: APK for Cafe Bazaar.
- `myketRelease`: APK for Myket.

Cafe Bazaar and Myket builds share the same application ID and core app code, but each build opens its own store for updates.

Release artifacts are built by GitHub Actions. Final production artifacts must be signed with the permanent PedalPro release/upload key; the private key must never be committed to this public repository.
