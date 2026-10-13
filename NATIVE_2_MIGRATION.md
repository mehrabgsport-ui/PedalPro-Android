# PedalPro Native Android 2.0

Branch: `native/android-2.0.0`

Restore branch: `backup/pre-native-2.0.0-20261013`

Protected pre-native commit:
`66025d094cd8770ce897658d6e817c2922f50a22`

## Current native implementation

Completed in dev2:

- Native launcher; the launcher Activity creates no WebView.
- Native PHP session/cookie + CSRF transport to `pedalpro.ir/api.php`.
- Native login/logout.
- Native dashboard.
- Native activities and club events.
- Event registration/unregistration.
- Native ride start GPS preflight.
- Ride record starts only after a fresh accurate location fix and explicit user confirmation.
- Existing Android foreground `TrackingService` reused directly by Native UI.
- Native ride live stats and finish flow.
- Existing GPS V2 filtering / Fused Location / raw fallback retained.
- Native live riders list.
- Neshan app handoff for live rider and event meeting-point navigation.
- Native club members.
- Native challenges and join/leave.
- Native performance summary.
- Native BLE Cadence / Power UI using existing `CyclingSensorManager`.
- Native BLE Heart Rate UI using existing `HeartRateBleManager`.
- Sensor managers now expose native callbacks and no longer require JavaScript to display data.
- Native AI advisor/history.
- Native group chat/messages.
- Native profile editing.
- Native bike management.
- Native body metrics.
- Firebase messaging, store flavors, diagnostics and update components preserved.

## Neshan embedded native map

The embedded Neshan Android SDK itself requires an app-specific Neshan licence file bound to package name and signing SHA1.
The repository currently contains no `neshan.licence` resource, so dev2 keeps route/navigation handoff to the official Neshan app while the rest of the application remains fully native.

Release certificate SHA-256:
`69:08:5C:67:2A:B5:C1:E4:ED:A8:AA:4A:BC:12:A2:94:0A:EB:53:F1:15:07:C5:F9:F8:7B:B2:19:D9:A1:D6:F7`

## Remaining parity work

- Embedded Neshan Native Map SDK after valid licence resource is available.
- Native route polyline/detail/replay over Neshan map.
- In-app moving-target navigation over embedded map.
- Full private chat controls/replies/read receipts.
- Event galleries/camera upload.
- Admin-only site management screens (remain website-side; not required for store-member app).
- Final visual polish and device test matrix.

## Safety

The production `main` branch is unchanged by this migration.
Do not merge the draft Native PR until parity/device tests pass.
