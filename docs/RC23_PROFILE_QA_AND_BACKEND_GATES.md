# RC23 — Interactive ride profile performance and time correctness

Branch: `native/web-parity`. Target version: `2.0.0-native-rc23` (`versionCode 67`).

## Verified source defects and narrow fixes
- Time profile formerly divided any timestamp by 1000, even when backend sent epoch **seconds**. Normalizes seconds/milliseconds and parses recorded_at for display only.
- Time profile repeatedly parsed strings while drawing on the Android UI thread. Build per-sample elevation/speed/elapsed time caches on data load.
- Route profiles with many thousands of points drew every vertex on every gesture. Render at most ~2 vertices/pixel without downsampling the selectable GPS source data.
- Scrubbing invoked continuous 250 ms map camera animations. Bound camera updates to at most about 9/s and always deliver the final finger-up location.
- Add standalone JVM time regression tests in CI. No changes to TrackingService, accepted GPS points, backend statistics, recorded user data, or WebView.

## Pending / NOT verified
- Physical-device rendering, swipe tests, GPS pause/resume and 30-minute idling, map authorization and live vector tiles.
- RC22 staging PHP package `PedalPro_RC22_Backend_Staging_Patch.zip` was not available in this GitHub repository or connected file search. Deployment and privacy SQL checks of `pp_live_riders` require authorized access to the actual PHP/MySQL code.
- App-side `pp_live_riders` expects GET response `{"riders":[{"ride_id":1,"lat":35.0,"lng":51.0,"display_name":"Rider","speed_kmh":10.0,"age_seconds":2}]}`. The server must authenticate, enforce SQL row-level access, return only latest active points, exclude the user's own position where appropriate, and cap stale age to <=45s.
- Never merge PR #29 or publish signed APKs without explicit owner approval and phone + staging acceptance.
