# PedalPro Native RC37 — completion gate

Branch: `native/web-parity` (PR #29 remains Draft; `main` untouched).
Android: `2.0.0-native-rc37`, `versionCode 81`, `applicationId ir.pedalpro.app`.
Web reference: `4.7.46`.

## Implemented in RC37

1. **Stale GPS start feedback** — Before submitting `start_ride`, revalidate preflight-fix age <=12s and accuracy <=35m; if expired, instruct the rider to repeat preflight. Prevent silent Start no-op and old map anchor. The TrackingService acquisition and calculation algorithms are unchanged.
2. **Live ride detail performance** — `ride_details?point_mode=live` requests up to 900 sampled display points rather than 5000; storage and original GPS data are not changed.
3. **No redundant full bootstrap every 8s** — active detail polling only requests ride details; auth, edits, history and permissions still follow server.
4. **Reuse map/chart** — update metrics and only redraw a route after latest GPS point ID changes. No full `PedalNeshanMapSurface` recreation or comments/photos reload on each live poll.
5. **Sequential live polling** — next request schedules after response rather than independently every 8s. Completed rides stop polling.
6. **RC36 preserved** — start foreground recorder once server confirms ride ID, restart same existing ride if tracking service fails, distinguish unverified phone from GPS failure; finishing isn't marked failed when only post-stop refresh fails.

## Covered by CI
- `ci/native_web_parity_checks.sh`: RC36 + RC37 contracts.
- Android unit tests, lint, source QA, Bazaar/Myket manifest smoke APKs and release signer workflow, on the SAME final HEAD.

## Pre-release gates not possible to certify from repository alone

- **Production backend deployment**: RC38 PHP/SQL lives in Staging only; real `pedalpro.ir/api.php` was not changed. Android does not call Staging-only `pause_ride` / `resume_ride` actions.
- **Phone/physical GPS**: test 1 short outdoor ride (start, background, pause/resume, finish, reopen details; GPS line, speed/elapsed); verify Neshan native tiles and session/token errors with cellular network changes.
- **Group proximity and partial-credit**: membership join timestamp/bounds and accurate distance credit while joining halfway are **NOT CERTIFIED**. The server's attendance + group distance allocation still needs interval attribution and isolated Staging tests before production.
- **Admin Bale**: Staging `api.php` includes `bale_status`, `bale_connected_users`, `bale_save_settings`, `bale_custom_send`, `bale_custom_history`; NativeSiteSettingsActivity already calls those routes. Running on production/actual token delivery and template/cron settings is not certified.
- **Visual identity**: pixel parity with Web `4.7.46` cannot be certified without exact web screenshots/snapshot; matching native UI source code is not proof.
- **Release signing**: market APK signature must match existing store certificate on-device or marketplace upload; GH CI using provided secrets does not prove compatibility with historical published certificate.
- **Distribution**: only an owner-approved marketplace submission should be published. PR #29 stays Draft; no merge or store release is automatic.

## Acceptance stop conditions
Do not ship if GPS recorder fails to enter foreground, map is blank on the real device, unsent points disappear after offline/restart, private group ride position appears to non-viewers, or market signing identity mismatches. Minor cosmetic differences and professional Strava-grade GPS filtering can follow in subsequent releases.
