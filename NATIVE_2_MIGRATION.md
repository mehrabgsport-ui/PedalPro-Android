# PedalPro Native Android 2.0

Branch: `native/android-2.0.0`

Restore branch: `backup/pre-native-2.0.0-20261013`

## Goal

Replace the WebView application shell with a fully native Android client while preserving the current backend, user data, GPS tracking service, Firebase messaging, BLE sensor support and store flavors.

## Migration order

1. Native shell and navigation — started.
2. Native authentication/session API.
3. Native dashboard/bootstrap models.
4. Native ride start GPS preflight.
5. TrackingService integration with native ride state.
6. Native Neshan map.
7. Live rides and rider navigation.
8. Routes / activities / club / challenges.
9. Performance / replay / heatmaps.
10. Social / chat / AI advisor.
11. Safety / privacy / Beacon / SOS.
12. Settings / app updates / diagnostics.
13. Remove WebView code after parity tests.

## Safety

The old Android app is preserved at commit:
`66025d094cd8770ce897658d6e817c2922f50a22`

Restore branch:
`backup/pre-native-2.0.0-20261013`

No production user database migration is required for this branch.
