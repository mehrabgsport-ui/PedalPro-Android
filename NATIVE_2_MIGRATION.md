# PedalPro Native Android 2.0

Branch: `native/android-2.0.0`

Restore branch: `backup/pre-native-2.0.0-20261013`

Protected pre-native commit:
`66025d094cd8770ce897658d6e817c2922f50a22`

## Current native implementation — dev3

- Native launcher; no WebView is created by the launcher.
- Native PHP session/cookie + CSRF API client.
- Native login/logout/dashboard.
- Native profile, bikes and body metrics.
- Native activities, ride details and route geometry renderer.
- Native GPS preflight; ride/timer only starts after a fresh accurate fix.
- Existing foreground TrackingService/Fused GPS/GNSS filtering/offline queue retained.
- Native ride finish/live stats.
- Native live rider tracking and moving-group navigation.
- Native Activity Replay animation.
- Native elevation/weather ride analysis.
- Native club members, events and challenges.
- Native weekly club leaderboard (Saturday–Friday).
- Native Training Load / Fitness / Form + FTP / Max HR settings.
- Native Segments and Segment leaderboard.
- Native Personal / Club / Global Heatmaps.
- Native BLE Cadence / Power / Heart Rate UI.
- Native social feed, Follow/Unfollow, Kudos and route recommendations.
- Native group chat.
- Native AI advisor.
- Native Safety Contacts.
- Native SOS.
- Native Beacon.
- Native Privacy Zones.
- Firebase messaging, diagnostics, store flavors and release signing preserved.

## Neshan

Route rendering and moving-target navigation are now native and contain no WebView.

The official embedded Neshan Android basemap still requires the app-specific Neshan licence resource bound to package/signing identity. The repository currently does not contain that licence file, so the official Neshan basemap SDK is intentionally not embedded yet.

Release certificate SHA-256:
`69:08:5C:67:2A:B5:C1:E4:ED:A8:AA:4A:BC:12:A2:94:0A:EB:53:F1:15:07:C5:F9:F8:7B:B2:19:D9:A1:D6:F7`

## Remaining before production 2.0

- Add official embedded Neshan basemap after valid licence is available.
- Full private-chat parity/replies/read receipts.
- Event gallery/camera upload.
- Final UI polish/accessibility.
- Real-device regression tests: GPS/background/battery, BLE, offline recovery, live navigation.
- Keep admin-only site management on the website unless a native admin app is explicitly required.

## Safety

Production `main` is unchanged.
Do not merge the draft Native PR until device/regression tests pass.
