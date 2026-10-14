# RC16 — GPS transmission and live recorded-polyline regression

- Native-only tracking requests now use the same PHP session cookie as PedalProApiClient. The stale WebView CookieManager path was removed from TrackingService.
- A 2xx API reply with `ok:false` or malformed JSON no longer removes a queued location point. Pending points retry every 15 seconds while the tracking service is active, even if no more fixes arrive.
- Every TrackingService location broadcast now carries its ride ID and distinguishes accepted recorded points from stationary-anchor updates. NativeMainActivity draws only accepted points, deduplicates them by timestamp and skips stale server snapshots.
- **Unchanged:** GPS filtering, recorded-point sampling, speed/distance calculation, Neshan map algorithms, server endpoint names, and user data.
- Build version: `2.0.0-native-rc16` (code `60`). PR #29 remains Draft; no merge or store publishing.
- QA still required on a real device with location permissions, >150m outdoor ride, mobile connectivity and temporary network loss. Verify `ride_details` contains nonzero recorded distance and speed, persisted polyline and correct history after stopping. CI success is not a substitute for this acceptance.
- Historical zero-distance ride root cause cannot be established without its GPS diagnostic log and server response.

## Final synchronization safeguard
- `ACTION_PREPARE_FINISH` pauses further GPS acquisition, drains road-match output and attempts to deliver every queued point while the ride is still active on the server.
- Native stop waits up to 25 seconds for the service's readiness flag and an empty point queue before calling existing `stop_ride`; on timeout, the ride remains open and the existing tracker restarts.
- If server `stop_ride` fails, native bootstrap is consulted before resuming that same ride. This does not invent server endpoints or claim an offline ride has been synced.
- Pending GPS storage is preserved on failed upload. Device QA of network-loss/resume and activity termination remains required.

- When tracking has been manually paused while unsent points remain, finishing is blocked with an actionable message; resuming the existing GPS ride allows normal retry and subsequent finish. The app does not silently discard the paused queue.
