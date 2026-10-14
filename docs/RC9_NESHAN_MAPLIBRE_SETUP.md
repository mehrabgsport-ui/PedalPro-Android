# PedalPro RC9 — Neshan official MapLibre SDK (2026-10-14)

This file replaces RC8 expired-legacy SDK requirements.

## Verified Neshan documentation
- Current setup: https://platform.neshan.org/docs/sdk/android/installation/
- Introduction: https://platform.neshan.org/docs/sdk/android/neshan-android-sdk-getting-started/
- Official styles: https://platform.neshan.org/docs/sdk/android/features/android-sdk-change-map-style/
- Marker API: https://platform.neshan.org/docs/sdk/android/features/android-sdk-add-marker/
- Expired installation (DO NOT USE for current code): https://platform.neshan.org/docs/sdk/android/legacy/installation/

## Platform and authorization
- Package `ir.pedalpro.app`, Android `2.0.0-native-rc9`, versionCode `53`.
- Neshan **MOBILE** API key must be obtained from the existing Android MOBILE credential
  and registered in GitHub repository -> Settings -> Secrets and variables ->
  Actions -> New repository secret -> name `NESHAN_MOBILE_API_KEY`.
- Do not put mobile key in Git source or a message. Build pipeline inserts it into
  AndroidManifest `org.maplibre.android.API_KEY` metadata, as required officially.
- The Android MOBILE key is packaged into APK for use by SDK. Restrict by Android
  package and signing SHA1 in Neshan platform. It is not a server-only secret.
- Server routing API key remains independently stored and never embedded in APK.
- Prior `neshan.licence` and legacy `org.neshan.mapsdk.MapView` do not apply.

## Native implementation
- `NeshanMapLibreBridge` with `MapLibre.getInstance`, native `MapView`,
  Activity lifecycle propagation, vector style `dark.json` / `light.json`,
  user/target markers and separate recorded/suggested native polylines.
- Native camera `CameraPosition` uses ~57 degree pitch, 17 zoom level,
  heading-up from previously validated GPS bearing; 35-m forward look-ahead.
- User pan suspends follow; Recenter resumes it.
- Do not falsely label fallback as Neshan. Without key, release uses explicitly
  labelled XYZ fallback; no map key/license is inferred from device settings.
- Rendering and route data are separate: the Neshan Services direction endpoint
  currently supplies a car-road proposal, not a proven bicycle route.
- No WebView. No GPS, service, distance, speed, elevation or user-data changes.

## Required verification
1. CI native adapter Java compile with dummy key to check type/API compatibility
   only; **never publish** that test key or an APK generated using it.
2. CI real signed Bazaar/Myket build with actual MOBILE secret configured.
3. Inspect manifest, package/version, signatures, and actual Neshan map vector
   tile rendering on Android devices. Verify dark/day toggle, tilt, zoom, pan.
4. Test 30+ minutes low-speed cycling, pauses, resume, intermittent network,
   recorded track alignment and live/group display.
5. Compare overlays to supplied Neshan reference screenshots.
6. Keep PR Draft and do not merge to main or publish automatically.
