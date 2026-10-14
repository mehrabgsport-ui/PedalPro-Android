# RC16 — GPS transmission and live recorded-polyline regression

- Native-only tracking requests now use the same PHP session cookie as PedalProApiClient. The stale WebView CookieManager path was removed from TrackingService.
- A 2xx API reply with `ok:false` or malformed JSON no longer removes a queued location point. Pending points retry every 15 seconds while the tracking service is active, even if no more fixes arrive.
- Every TrackingService location broadcast now carries its ride ID and distinguishes accepted recorded points from stationary-anchor updates. NativeMainActivity draws only accepted points, deduplicates them by timestamp and skips stale server snapshots.
- **Unchanged:** GPS filtering, recorded-point sampling, speed/distance calculation, Neshan map algorithms, server endpoint names, and user data.
- Build version: `2.0.0-native-rc16` (code `60`). PR #29 remains Draft; no merge or store publishing.
- QA still required on a real device with location permissions, >150m outdoor ride, mobile connectivity and temporary network loss. Verify `ride_details` contains nonzero recorded distance and speed, persisted polyline and correct history after stopping. CI success is not a substitute for this acceptance.
- Historical zero-distance ride root cause cannot be established without its GPS diagnostic log and server response.
