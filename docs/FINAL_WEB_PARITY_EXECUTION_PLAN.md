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
