# PedalPro Native RC36 — ride lifecycle recovery

Branch: `native/web-parity`. Web reference: 4.7.46. Android version: `2.0.0-native-rc36`, code `80`.

## Implemented

- Once `start_ride` returns a valid `ride_id`, Native immediately starts the existing `TrackingService` before optional `bootstrap` or group setup.
- A failed post-start `bootstrap` does not orphan an active ride without the GPS service.
- If foreground-service start throws, the confirmed ride remains visible. A dialog retries the **same** ride ID rather than submitting `start_ride` again.
- Unauthorized/unverified-number errors from `start_ride` are prominently surfaced with a link to the account page. No security rule is bypassed.
- If `stop_ride` succeeds but a subsequent `bootstrap` fails, the completed ride is still considered saved and the details view opens.
- CI source checks enforce the critical ordering and error visibility.

## Unchanged / guarded

- `TrackingService` GPS filters, sampling, distance and speed calculations, queue, map rendering, privacy, backend endpoint names, and user records remain unchanged.
- RC38 `pause_ride` / `resume_ride` endpoints are installed only in Staging and **not called** from production Android builds.
- The RC38 server test schema is not a production-release contract.
- No WebView, secrets, merge to `main`, or automatic marketplace publishing.

## Verification still required

- CI `Native RC QA` and `Android Store Release Build` must succeed for the final branch HEAD.
- Check Bazaar/Myket signed artifacts (versionCode 80), keystore continuity, and install on a real Android phone.
- Test actual denied number verification, network drop *after* successful start, stopped-ride refresh failure, foreground GPS permission failures, offline queue, group live positions and Neshan vector map.
- Exact visual parity with the Web 4.7.46 reference and live GPS accuracy remain unverified.
