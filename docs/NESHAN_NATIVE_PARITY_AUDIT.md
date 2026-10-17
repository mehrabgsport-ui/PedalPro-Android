# PedalPro — Native Neshan / Web 4.7.46 parity audit
**Review date:** 2026-10-14 · **Android branch:** `native/web-parity` · **Release candidate:** `2.0.0-native-rc3` (`versionCode 47`)

## Key finding

The screenshots show an incomplete raised **شروع/ادامه رکاب** CTA and mapping discrepancies. The original new-native map had no Neshan Android SDK. It initially displayed a generated grid, then a lightweight OpenStreetMap XYZ backdrop. Neither implementation is the site's **Neshan** map. The Android app uses the existing website API for PedalPro user/tracking data; that application backend is unrelated to the separate **Neshan display license** and **Neshan route-service key**.

## Implemented code in this branch

1. **Full ride CTA in both shells.** `NativeMainActivity` and `NativeScreenBase` use a 128dp bottom `FrameLayout` dock and a fully contained 108dp center CTA. Eliminated `translationY(-38dp)` responsible for clipping. The button has its own contrasting circular background, bike icon, visible caption, native press feedback and a functional click target.
2. **Optional native Neshan SDK integration.** `PedalNeshanMapSurface` hosts `org.neshan.mapsdk.MapView` when a **licensed Android SDK build** is explicitly enabled. It preserves the existing PedalPro GPS path/marker interface and adds map style (day/night), traffic, zoom and auto-frame controls. This is **not WebView**.
3. **Android GPS visual overlay.** `NativeRouteView` can operate as a transparent route/marker layer while the SDK supplies the map background; its camera is synchronized with the SDK via the supported Neshan camera API. If the SDK is unavailable the surface falls back to the existing native XYZ renderer, retaining track functionality.
4. **Coverage.** Main ride, routes, route detail, group/live navigation, event map, Replay/Flyover and Advanced route surfaces now construct the same reusable map host.
5. **Build safety.** The native Neshan SDK is **opt-in**, so public CI and Bazaar/Myket builds do not ship with a missing/invalid license. The `neshan.licence` and Android signing files are gitignored. No GPS filter/recording/distance/speed/backend-user-data changes are part of this integration.

## How to enable genuine Neshan on Android

**Required external credentials:**
- Android Neshan SDK `neshan.licence` issued for package `ir.pedalpro.app` and the **actual release APK signing SHA-1**. Obtain this from <https://platform.neshan.org/>; place locally in `app/src/main/res/raw/neshan.licence` (do not commit).
- Release signing **SHA-1 verified from successful GitHub Actions keytool log**: `B7:56:19:B4:7B:D3:18:96:66:58:4A:3A:0A:9A:F1:2F:2C:AA:11:4B` (both Bazaar/Myket currently share this signing material). Do not confuse the SHA-256 of the APK artifact ZIP with this signing SHA-1.
- If the website uses Neshan service-based road directions/matching, a **separately authorized Neshan service key** is required; previous PedalPro Web integration used `neshan_service_key` in runtime config. A `web.*` display credential/browser-referrer key is **not** an Android SDK licence or automatically a routing key.
- Signed Bazaar and Myket builds presently share the configured GitHub release signing key. Check its SHA-1 fingerprint, then issue the Neshan Android licence for that fingerprint. Keep the licence as private build material.

Command for a configured machine/CI runner:

```bash
gradle :app:assembleBazaarRelease :app:assembleMyketRelease -PpedalproNeshanAndroidSdk=true
```

Default store CI (without `-PpedalproNeshanAndroidSdk=true`) builds the native XYZ fallback. It **does not imply** the Neshan SDK is active. The opt-in signed Neshan build **has not been validated** without the licence.

## Direction services activation — RC3 (2026-10-14)

The owner enabled Neshan service API permissions **no-traffic routing**, **traffic-aware routing**, and **point-to-road map matching**. This alone does not transfer a secret key into the Android app.

### Implemented RC3

- `NeshanDirectionsClient`: Neshan `GET https://api.neshan.org/v2/direction` road route using the `Api-Key` request header, bounded response/timeouts, decoded encoded `overview_polyline.points`, route length and first available instruction. API syntax is supported by the public V2 examples, but **the newly issued key has not yet been tested live**.
- `NativeRouteView.setNavigationRoute`: a separate **gold road route** distinct from the original raw GPS ride track and yellow straight-line target. Recorded GPS, TrackingService, distance and speed remain unmodified.
- `NativeLiveNavigationActivity`: optional explicit road-route mode, 45-second minimum request interval, re-request after user/group target moves over 80 metres, safe fallback to straight distance on invalid key/network error. No uncontrolled per-3-second Neshan calls.
- `NeshanServiceCredentials`: service API key entered **locally in the app**, encrypted with Android Keystore (AES/GCM), never checked into git or injected into signed BuildConfig. Navigation offers save/clear. Users can continue to navigate in direct-line mode with no configured key.
- **Caution:** Neshan V2 street directions may represent **motor-vehicle roads**, not cycling-legal routes. The app explicitly describes them as a suggestion for cars, does **not** display vehicle ETA as cycling ETA, and never silently promises bicycle safety.
- **Security:** an on-device service key is a stopgap for controlled testing only. A shared production service key should live on the PedalPro **server-side proxy**, using an explicitly authorized backend endpoint, server-side quota and origin auth. No backend endpoint was invented or changed in RC3.
- **Provider distinction:** Web map key, Mobile map key, native SDK licence, Neshan `service` key and Android release signing certificate are different credentials. The previously requested `neshan.licence` is still not available, so **default RC3 does not display actual Neshan SDK map**.

### QA not yet demonstrated

1. Android SDK enabled build with real signed `neshan.licence`; default build succeeds without it.
2. On-device live API response using the **newly created** Neshan Services key, including any IP/domain allowlist restrictions (a mobile device's public IP changes).
3. Road route/screen equivalence with Web 4.7.46, especially bicycle access, rerouting, route snapping, map-matching and dynamic moving-group ETA.
4. Visual pixel comparison against the original Web reference and actual device tap/pinch map functionality.

## Remaining website differences — code/screenshot findings

| Area | Native difference against Web 4.7.46 requirements | Status |
|---|---|---|
| **Neshan map imagery** | Default APK still uses XYZ fallback, not Neshan styles/POI/traffic | SDK wired, **licence and device verification pending** |
| **Street navigation to rider/group** | `NativeLiveNavigationActivity` now supports optional Neshan V2 road suggestion and keeps direct geodesic fallback; cycling-safe engine/true turn-by-turn and Web parity remain unverified | **Partially implemented; live key and device QA needed** |
| **Moving group route** | Target refresh every 3 sec remains native; optional road recalculation every 45s / 80m is in RC3; cycling ETA and road snapping parity unverified | **Partially implemented** |
| **Map touch/zoom** | Native gestures and CTA controls are implemented but may differ from SDK/Web pinch inertia, map layers and auto-follow behavior | **Device acceptance pending** |
| **Ride CTA** | Circular dock was clipped by negative translation in main and secondary shells | **Code fixed**; visual device acceptance pending |
| **Ride forms** | Android `Spinner`, checkbox rows and camera/GPS preflight layouts still differ from Web native-styled controls and compact sizing | **Outstanding UI parity** |
| **Activity detail maps** | Same map host wired, but precise basemap style, route markers, photo interactions and popup behavior need screenshot comparison | **Pending device/Web reference** |
| **Home cards / weather / leaderboard** | Native cards approximate the Web layout; spacing, translucent surfaces, icon geometry, card sizing and carousel scrolling not pixel-verified | **Pending screenshot audit** |
| **Drawer and bottom nav** | Main bottom glyphs were replaced with vectors; badge positions, nested menu spacing, transitions and selected-state parity are not yet fully verified | **Partially fixed** |
| **Segments / Heatmap / Offline / Flyover** | These use separate native implementations, not the Web's exact map tiles, visualization layers, caching and animation contracts | **Outstanding** |
| **Chat / AI / Safety / BLE** | Native screens provide operations but exact states, typography, accessibility and device interaction remain unverified | **Pending** |
| **Admin, SMS, APK release and role screens** | Separate native editors; some bulk/pattern SMS and release rollback action contracts remain unverified | **Pending** |

**Evidence boundary:** the provided screenshots and native source substantiate clipping and map renderer deficiencies. The exact server-side Web 4.7.46 snapshot and its logged-in screen states were not accessible during this run, so the table is an engineering gap audit, **not a certified pixel-by-pixel visual diff**.

## Acceptance gates

- Confirm actual Neshan Android licence with signed Bazaar/Myket APKs; verify style, traffic, zoom/pan, street labels, overlays, provider attribution, map error states and offline transitions on a physical Android device.
- Confirm service API supports bicycle-appropriate road directions and compare the precise Web route response; do not substitute pedestrian/car directions silently.
- Verify map overlay and Neshan SDK projection pixel alignment at multiple zoom levels before claiming route parity.
- Check complete raised ride CTA across main, ride, social/advanced and secondary screens with gesture navigation and three-button system navigation.
- Compare all screens to exact Web 4.7.46 evidence, not screenshots from this interim APK.
- Require passing Native RC QA plus signed-release workflows on same final HEAD; **do not merge `main` or auto-publish** without explicit owner approval.

## Source references

Neshan official Android sample: <https://github.com/NeshanMaps/android-neshan-maps-starter-master-new>. Neshan SDK license is bound to Android package/signing fingerprints.
