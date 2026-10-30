# RC7 Native Map Regression Fix — 2026-10-14

Baseline backup: `backup/pre-navigation-rc7-20261014`, branch `native/web-parity`.
Device video: `1000152337.mp4` (12.6 seconds). The clip showed partly rendered OSM streets, disappearing backgrounds and an essentially flat camera; rider GPS was stopped. No real moving-bike heading could be tested in the clip.

## Fixes
1. `NativeMainActivity`: retain the existing PedalNeshanMapSurface when re-rendering the *same active ride*; detach from old parent before reattachment. Keep pending tile callbacks with their original map instead of constantly constructing a new map. Throttle ride route fetch to 20 seconds, while keeping GPS source logic completely unchanged.
2. `NativeRouteView`: replace plain 0.86 Y scaling with a projective homography giving converging road perspective; inverse-project the on-screen rider at 68% height so the location remains aligned when heading changes. Heading only follows valid movement bearings. Pan/zoom/recenter stay available.
3. `PedalMapTiles`: larger logical tile display size, center-priority requests, max 18 queued/in flight, cached 1-2-level ancestor fallback, inverse-projected tile extent and weak references to all attached maps for redraw. Tile URLs still require a licensed provider for production; the development fallback is public OSM.
4. `PedalNeshanMapSurface`: connect the *licensed* Neshan MapView to its documented `setTilt` and `setBearing` API; no fake credential activation. A MOBILE key in Keystore alone does not provide the Android licence.

## Required acceptance
- Same final commit must pass `Native RC QA` and `Android Store Release Build`, with separate Bazaar/Myket signed APK artifacts and package/version/certificate checks.
- Install on a real Android phone and confirm the map never turns black after refresh; street imagery is available around the rider; 10-15 seconds of pan/zoom/navigation still shows tiles; GPS stop/resume does not reset recorded route.
- With a moving rider, heading-up direction should settle without jumps; without valid bearing the map stays North-Up *with pitch*.
- Genuine Neshan layer and tilt only when a `neshan.licence` is obtained for `ir.pedalpro.app` and marketplace signing SHA1. The license is not embedded in GitHub or this default APK.
- A raster homography is **not** a full terrain/3D engine; Neshan SDK or a correctly licensed MapLibre vector style is required for authentic 3D camera/POI parity.
- No authorization to merge or release to marketplaces.
