# PedalPro Native Web-Parity Execution Plan

Source of truth: PedalPro Web 4.7.46
Work branch: `native/web-parity`
Protected baseline: `backup/pre-native-web-parity`
Rule: no merge to `main` before device test and explicit approval.

## Phase 1 — Stabilize baseline and CI
Status: DONE
- Keep Native-only architecture; no WebView.
- Keep backend/API/user-data compatibility.
- Build Bazaar and Myket release variants.
- Native RC QA must pass after each batch.
- Current verified head at phase close: `397fe5c62c28dbad400fad74491706a2141390ba`.
- Native RC QA: success.
- Android Store Release Build: success.

## Phase 2 — Global shell parity
Status: DONE
- Header geometry, logo, account/admin/menu controls.
- Drawer grouping, order, guest/member/admin visibility.
- Exact Native install-policy behavior: hide web install links.
- Bottom dock geometry, center ride control, labels and states.
- Server-status placement.
- Replace temporary emoji/glyph icons with source-equivalent native vectors where source paths are available.
- Align common card/input/button tokens.
- Final source glyph treatment, signed-in account state, admin group styling and common input skin aligned.

## Phase 3 — Core public pages
Status: DONE
- Home hero, quick cards, live rides, upcoming activity, site feed, report, top riders.
- Activities/events list/detail/register/gallery.
- Routes list/detail.
- Members and member detail.
- Gallery.
- About.
- Full user guide and PDF target.
- Canonical event cards and event-detail modal, registration/attendance and event gallery.
- Canonical ride selector with ride-details API, map, metrics and speed chart.
- Mobile members list/detail and merged event/ride gallery.
- About page mobile glass layout and canonical guide PDF target.
- Verified Native RC QA and Android Store Release Build.
- Current batch complete: mobile single-column home report, corrected guide PDF target, member cards linked to native member detail.
- Final audit complete: Home, Activities/Events, Routes, Members, Gallery, About and the 12-section Guide are present in Native UI and use the canonical web data/actions.
- Phase 3 closed with successful Native RC QA and Android Store Release Build.

## Phase 4 — Account and personal data
Status: DONE
- Login/signup/reset modal parity.
- Profile editor.
- Phone verification and admin manual phone.
- Social/sport profile links.
- Medals and challenge summary.
- Account privacy/deletion/logout.
- Bikes.
- Body/Fitness data.
- App/version and phone settings.
- Profile summary/edit flow, phone verification, social links, medals/challenges and account actions aligned.
- Bikes list/editor, member bike details, body metrics summary/history/chart and store-channel update page aligned.
- Native RC QA and Android Store Release Build verified successful at phase close.

## Phase 5 — Ride and live-operation screens
Status: IN PROGRESS
- Ride start/pause/resume/finish UI.
- Low-power OLED screen.
- Live rider detail and live navigation.
- Group navigation.
- Beacon/SOS/Privacy Zones.
- Cadence/Power sensors.
- Ride detail, comments, photos, publication/privacy.
- Preserve existing GPS/tracking/map algorithms unless separately authorized.

### Phase 5 — UI-only batch 5A (2026-10-14)
- `79f5be8d34fd`: OLED pause/resume accessibility label follows the visible control state; enforce white system bar icons on the black OLED surface.
- `85d7dbfe2934`: Ride detail privacy and live-navigation actions now use native vector icons instead of emoji glyphs.
- Scope: presentation/accessibility only. No changes to GPS, tracking, map calculations, API/backend, or user data.
- Source check: both committed files inspected after push; static parity guards passed.
- CI: Native RC QA / Android Store Release Build results for this batch are **NOT YET VERIFIED**. The available commit status endpoint returned no check contexts, and the workflow-run lookup excludes push-triggered runs. Do not declare this batch release-validated or close Phase 5 until both build results are independently confirmed.
- Remaining Phase 5: source-by-source comparison and verified release build for start/finish, OLED, live/group navigation, Beacon/SOS/privacy, sensors, ride detail, photos, comments, and publication states.

### Phase 5 — UI state, navigation, sensors, safety batch 5B–5D (2026-10-14)
- `f53b3cdd3bd0`: OLED accepts initial paused state, presents the matching play/pause icon and returns elapsed display time plus final pause state.
- `c2e24ce7860d`: parent screen receives the OLED result; native display clock avoids resetting backward during refresh.
- `838cecf536f5`, `39a6c7751636`: live/group heading glyph replaced with accessible native vector, without changing route coordinates, GPS or map algorithms.
- `2fa8e37b70ec`: native sensor scan/connect button skin and vector icon parity.
- `2e16515dec94`: Beacon link creation visibly disabled when there is no selectable active ride.
- `f923661c73fa`: OLED receives currently displayed elapsed seconds, rather than a stale cached base duration.
- `dfebdf7ca964`: add regression guards to Native RC QA; existing Android unit tests, lint and APK smoke-build steps retained.
- Source review: six affected native UI files passed balanced-delimiter syntax screening and cross-file UI-state contract assertions; this **does not** constitute a Gradle/Android build.
- CI verification: this environment cannot retrieve push-triggered workflow runs; combined commit statuses do not expose the native QA/release workflow checks. **Native RC QA and Android Store Release Build remain UNVERIFIED on these new commits**.
- Source-of-truth limitation: the named Web 4.7.46 PPUP restore file was not found in accessible Library or connected Drive. Exact pixel/state parity cannot be certified without the reference content.
- Current Phase 5 code inventory: ride controls in NativeMainActivity; OLED in LowPowerRideActivity; live/group navigation in NativeLiveNavigationActivity; SOS/Beacon/privacy zones in NativeSafetyActivity; BLE sensor UI in NativeMainActivity; ride detail/comments/photos/privacy in NativeRideDetailActivity, NativeRidePhotoController and NativeRidePrivacyDialog.
- Phase 5 remains IN PROGRESS until CI is verified and device-based Web 4.7.46 screen/state parity is audited. Do not mark DONE or merge to main prematurely.

## Phase 6 — Pro and community features
Status: IN PROGRESS
- Segments and leaderboard.
- Personal/club/global Heatmap.
- Training Load.
- Replay.
- Flyover.
- Weather/elevation analysis.
- Offline routes.
- Maintenance.
- Social feed / Follow / Kudos.
- Route recommendations.
- AI advisor.
- Group chat.
- Challenges, detail, participation, editor.

## Phase 7 — Administration
Status: IN PROGRESS
- General site settings.
- Appearance settings.
- Medals.
- Users/roles.
- FarazSMS status, lines, OTP patterns, custom/pattern/bulk sending and logs.
- App release manager with chunked APK upload.
- App notifications/history.
- Firebase/FCM configuration and test.
- System status.

### Phases 6–7 — Native feature hardening (2026-10-14)
- `5b029e4f75df`: reject invalid segment/offline route selections; confirm local cache deletion.
- `1fae37437f12`, `2ff2a4f6785c`: vector Kudos glyph and native on/off state, unchanged Follow/Kudos API actions.
- `a32b8efd49d9`, `571282f599fa`: admin user editor preserves account activity/privacy instead of forcing fields and offers one-tap profile prefill with blank password.
- `bf0a676ed6d2`, `5e0458a2d77d`: main admin SMS drawer opens the full NativeSiteSettingsActivity SMS tab instead of read-only summary.
- `9d4d0d11bf12`: release-upload UI data captured before IO; missing release response handled; reject empty APK.
- Native release manager/chunk upload, FCM, notifications, site appearance/medals/users, social/feed, Replay/Flyover/Heatmap and other Pro screens were found in the repository; **code presence is not device parity certification**.
- Outstanding Phase 7: verify pattern/bulk SMS action contracts and rollback against exact Web 4.7.46 backend; provider/store sign-off pending.

### Phase 8 — Structural QA scaffolding (2026-10-14)
- `5e08d4c600a9`: added `ci/native_web_parity_checks.sh` to guard native architecture and cross-screen contracts.
- `eac15d570bcc`: native QA runs the independent structural audit before Android test/lint/APK smoke builds.
- `f15b6ecdb641`: comprehensive QA matrix in `docs/NATIVE_WEB_4_7_46_PARITY_QA_MATRIX.md`.
- Build results for these commits are **NOT VERIFIED** through the currently accessible GitHub connector. Do not mark DONE or claim signed APK delivery.
- Exact 4.7.46 PPUP and reference screenshots were not found in accessible sources. Pixel parity and all seven guest/member/admin/device acceptance matrices remain outstanding.
- Phase 8 stays PENDING for final sign-off until preceding stages pass independent release/device verification.

### Phase 6 — Pro/communications hardening batch 6B (2026-10-14)
- `653abc911e88`: fixed actual Replay playback cursor stall; cancel offscreen Replay/Flyover animation and discard stale async tab responses.
- `234b2eaf682d`: Replay and Flyover playback controls use native vector Play/Pause icons and state labels.
- `eae61fe6f5e6`: dedicated Native AI activity prevents duplicate sends and late history response erasure; preserves unsent prompt when network fails.
- `63485d647aaa`, `59163db1d458`: group/private chat room IDs are captured before asynchronous calls; late responses cannot render private messages in another room; polling pauses offscreen.
- `0eb83687effa`: primary NativeMainActivity AI advisor also ignores late history, supports both existing history envelope keys, and restores pending text after failures.
- Outstanding: on-device chat/privacy verification, animation timing and Web 4.7.46 reference screenshots. No GPS, Tracking or map calculation changes.

### Phase 7 — FCM form-state hardening batch 7B (2026-10-14)
- `3df0d5a9de4b`: snapshot FCM form fields and service credentials on the main thread before remote save/clear actions; clear in-memory credentials on UI thread after server confirmation.
- Outstanding: Firebase service-account test, APK publishing/signature verification, backend contract verification for bulk/pattern SMS and release rollback.

### Phase 8 — Expanded regression contracts batch 8B (2026-10-14)
- `a491c761fd3d`: added 12 UI invariant guards covering replay progress, stale AI/chat responses, offscreen polling, Firebase snapshot and Kudos state.
- Source-level review of six changed Java classes reported balanced braces/parentheses/brackets, and 34 source contract predicates were found in their current committed files; **these are static checks only**.
- Native RC QA and Android Store Release Build remain UNVERIFIED for current HEAD. No device validation, production-signing certificate verification or final signed APK delivery.
- Full visual parity continues to require the exact PedalPro Web 4.7.46 reference archive/screenshots.

### Phase 5/8 — User screenshot map and navigation batch (2026-10-14)
- Confirmed root cause of the blank GPS map: `NativeRouteView` was a grid-only custom `Canvas` renderer with **no background tile requests**. Missing street imagery is not itself a GPS/website API failure.
- `e537392217c4`, `7282db3c75db`, `12724cac1b35`: added a native XYZ HTTPS map tile backdrop, cache and Mercator-aligned overlays, with Gradle-configurable map URL; existing GPS/TrackingService/backend point processing unchanged.
- `abf1ce5c6632`, `331d9b725286`: configurable attribution, HTTP map permission-error messages and tile request backoff. Public OSM raster tiles are a **test-only fallback**; secure licensed provider configuration remains a production requirement.
- `2daaea85677f`: seed the map camera from the already-validated start GPS fix before the server route is fetched.
- `2e0bf39c609e`: native map drag, pinch/double-tap zoom and long-press reset (camera only).
- `7decfbe7fb78`, `53e2144ce5cd`, `bbea1f35c9d0`, `cc2e5c7d36c8`: removed duplicate ride form heading, sanitized `null` upcoming destination text, replaced emoji nav/drawer icons and aligned RTL select typography.
- `3615ac670475`, `7273f0340ffe`: added scrolling clearance below main/secondary screens so the raised dock does not hide trailing controls.
- `718a2e969088`, `cbe80555e3d7`: map integration, provider contract and acceptance tests in `docs/NATIVE_MAP_SETUP_AND_DIAGNOSIS.md`.
- `6a067588e20c` code checkpoint: Native RC QA **SUCCESS** <https://github.com/mehrabgsport-ui/PedalPro-Android/actions/runs/38527023417> and signed store APK build **SUCCESS** <https://github.com/mehrabgsport-ui/PedalPro-Android/actions/runs/38527023446>; signed Bazaar/Myket artifacts were generated. Follow-up touch gestures must pass CI at their newer HEAD separately.
- User screenshots are from the running Android app, **not an independently exported canonical Web 4.7.46 source**. Exact pixel/UI states and real map tile/gesture response remain unverified on device. Do not close Phase 5/8 prematurely and do not merge/publish without approval.

### Phases 5/8 — Neshan Services road-navigation batch RC3 (2026-10-14)
- User activated Neshan **service** permissions (no-traffic directions, traffic-aware directions, point-to-road map matching) in their account; **the actual API key was not shared with the app**.
- `2876896f545a`, `82c8571d5e4c`: store suggested road-polyline geometry separately from recorded GPS polyline, with a shared native map-surface adapter.
- `973514660600`: AES/GCM Android Keystore storage for device-local Neshan service key; never hardcode or commit keys.
- `8fa23ce26221`: bounded Neshan V2 directions HTTP client, original street-polyline decoding and safe response parsing.
- `05c3d1849dc2`, `776806df0284`: live/group navigation screen offers opt-in road suggestions, local credential dialog, 45-second request cooldown, 80m position-change recalc, invalid route cleanup and offscreen polling pause. Direct geodesic fallback preserved and explicit car-route/cycling caveats shown.
- `28030ed251cd`: nine regression guards for key storage, road overlay and original GPS separation.
- `92e0680fa4ae`, `387f94e55096`: Android 2.0.0-native-rc3/versionCode 47 and QA workflow enforcement.
- `eecb7156671d`: evidence and remaining provider/device constraints documented in `docs/NESHAN_NATIVE_PARITY_AUDIT.md`.
- Neshan `neshan.licence` (for actual Android SDK map imagery) still unavailable; the default signed APK uses XYZ tiles and no Neshan SDK. Service key must be entered locally for V2 road suggestions, or connected through a future authorized server-side proxy. No backend endpoint was invented.
- **Open hard gates:** live Neshan API test with newly created key, Android licence-enabled SDK build, true bicycle-safe directions, real-device validation, exact Web 4.7.46 screenshot parity, owner approval before merge.

### Phase 5/7/8 — RC4 Neshan settings and full-screen ride UI (2026-10-14)
- `aedf94d5616c`: Android Keystore encrypted **MOBILE** token slot added alongside separate Services key (no token included in source).
- `a3f50aefef18`, `3de0ed258d6b`: full `NativeAppSettingsActivity` with distinct map keys, privacy, default road guidance preference, Android settings shortcut, and admin-only site tab links.
- `62933abdb96a`: native site-settings direct deep-link routing to all 9 sections, including SEO/Robots. `seo_robots` is the *search crawler* setting; not an invented bot-manager API.
- `73cdae2e251c`, `98cf0b06c330`: app-settings navigation exposed from profile, drawer, phone preferences and live navigation; route preferences and key access centralized.
- `c42147f822f9`, `33f2e8dc86e`, `f9e395fcaf85`: active ride map moved from a 280dp scroll card to an immersive native full-screen map with floating title/state/actions, on-map zoom controls, bottom telemetry HUD and system-back behavior. Original pause, stop, sharing, OLED and GPS hooks retained, not reimplemented.
- `c7d7c52e67a0`: new static CI source contracts for app settings/security, admin tabs and immersive native ride rendering.
- `113f3ca67577`, `aa402914b5ba`: RC4 `2.0.0-native-rc4` / code 48 build and CI version enforcement.
- `3eda7126ab11`: developer/QA trace in `docs/RC4_APP_SETTINGS_AND_RIDE_UI.md`.
- **Known gaps:** entering the MOBILE key does not independently activate the license-bound Neshan Android MapView; precise pixel parity and provider maps still need physical-device testing and signed SDK license, true cycling route matching and shared backend safety gates. More web-only admin/bot contract fields must be discovered from actual server sources; no arbitrary API actions were invented.
- CI and store artifact status for latest RC4 HEAD must be verified before declaring the build ready; `main` stays untouched.

## Phase 8 — Final parity QA and delivery
Status: PENDING
- Audit every route/state against Web 4.7.46 source.
- Remove remaining generic/raw-JSON UI.
- Check RTL, spacing, typography, colors, radii and states.
- Verify guest/member/admin access.
- Verify Android Back/modal/drawer/navigation behavior.
- Run Native RC QA and signed Bazaar/Myket release build.
- Device screenshot comparison for final visual acceptance.
- Produce final signed artifacts.
- Do not merge to main until explicit approval.

## Execution rule
Each phase is split into small commits. After each batch:
1. build,
2. fix all compile/QA failures,
3. mark the batch complete,
4. continue to the next batch without mixing unrelated changes.
