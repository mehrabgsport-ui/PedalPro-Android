# RC21 GPS stabilization and reference cyclist (2026-10-15)

Android branch: native/web-parity. versionCode 65. Version 2.0.0-native-rc21.

Root cause analysis from 14 Oct screenshot: heavy zigzags, repeated stationary scattering and long backtracking. Actual TrackingService already has GNSS/IMU filtering. The fix addresses short Doppler jitter bypass, unchecked initial single GPS fix, mixed cell/network fallback, and independent per-point road snapping that can alternate parallel streets.

- Two geographically consistent, fresh fine GNSS fixes needed before the first anchor. At 1s sampling, min-distance=0 is essential while stationary.
- Network/passive provider cannot persist a bicycle point; fused high accuracy remains active; Android GPS provider fallback when Fused unavailable.
- Device-reported speed evidence alone cannot unlock motion unless next-fix geometry supports movement outside uncertainty. Standstill jitter suppression uses reported accuracies, not a constant tiny circle.
- Existing IMU stillness guard, one-point quarantine, speed plausibility checks, session cookies, offline retry and stop-flush preserved.
- Persist only filtered device positions, not independent road-matched positions. Snapping unrelated points between nearby streets is not a safe substitute for path following.
- Rejected noisy fixes stay in raw diagnostic capture, and never reach stored route or cumulative distance. Distances are computed downstream from accepted points.
- Old route polylines get conservative display-only bounce removal; stored points and historical totals are NOT retroactively rewritten.
- The 2D overhead cyclist is a transparent VectorDrawable approximating user-supplied green-jersey, blue-bike reference. Marker orientation updates based on heading minus camera bearing, in 10-degree buckets.
- Pure-Java regression suite tests initial GNSS lock, spikes and stationary jitter.
- No production server changes. Signed store artifacts are internal candidate builds only; on-device riding with permission/battery settings is still required.

No claim of using Strava proprietary algorithms. Strava docs acknowledge bad GPS data and smoothing; exact internal thresholds are not public.
