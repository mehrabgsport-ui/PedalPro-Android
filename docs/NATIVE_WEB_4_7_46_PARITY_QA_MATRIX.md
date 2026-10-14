# PedalPro Android Native — Web 4.7.46 Parity QA Matrix

- Reference: PedalPro Web 4.7.46, restore archive `PedalPro_RestorePoint_2026-10-13_Pre_Native_2.0.0.ppup`.
- Target: branch `native/web-parity`; **never merge to `main` without explicit approval**.
- Constraints: **no WebView**, no changes to GPS/tracking/map algorithms, backend contracts, or user data.
- Evidence levels: **CODE PRESENT** means UI/action was found in native source, not that on-device behavior or pixel alignment passed. **NOT VERIFIED** means build/device/reference evidence is missing.
- Reference availability: the named PPUP was not available through accessible project/library or connected Drive during this run. Exact text/color/spacing/pixel comparison is **NOT VERIFIED**.

## Phase 5: ride, navigation, safety

| Feature | Native implementation | Status / outstanding acceptance |
|---|---|---|
| Ride start, GPS preflight, finish | `NativeMainActivity.showRide`, `createRideAndStartTracking`, `confirmFinishRide` | CODE PRESENT; live device state transitions NOT VERIFIED |
| Pause/resume and OLED clock | `NativeMainActivity`, `LowPowerRideActivity` | UI state synchronization patched; background / process recreation NOT VERIFIED |
| OLED iconography, dark screen, time/distance/speed | `LowPowerRideActivity`, `PedalIconView` | CODE PRESENT; reference screenshot geometry NOT VERIFIED |
| Rider detail, comments, photos, publication/privacy | `NativeRideDetailActivity`, `NativeRidePhotoController`, `NativeRidePrivacyDialog` | CODE PRESENT; guest/owner/admin device matrix NOT VERIFIED |
| Live and group navigation | `NativeLiveNavigationActivity` | CODE PRESENT; native heading icon; dynamic target/map behavior NOT VERIFIED |
| Beacon, SOS and Privacy Zones | `NativeSafetyActivity` | CODE PRESENT; service/API and external-link expiry NOT VERIFIED |
| Cadence, Power and heart rate | `NativeMainActivity`, `NativeSensorsActivity` | CODE PRESENT; pairing with physical BLE devices NOT VERIFIED |

## Phase 6: Pro and community

| Feature | Native implementation | Status / outstanding acceptance |
|---|---|---|
| Segments and leaderboard | `NativeAdvancedActivity`, `NativeSegmentLeaderboardActivity` | CODE PRESENT; invalid submissions gated; leaderboard QA NOT VERIFIED |
| Personal/club/global heatmap | `NativeHeatmapActivity` | CODE PRESENT; overlay and privacy QA NOT VERIFIED |
| Training Load and fitness | `NativeAdvancedActivity` | CODE PRESENT; score/value parity NOT VERIFIED |
| Replay and Flyover | `NativeAdvancedActivity` | Playback cursor and stale-response lifecycle patched; 3D visual parity/device validation NOT VERIFIED |
| Weather and elevation | `NativeAdvancedActivity` | CODE PRESENT; numerical parity NOT VERIFIED |
| Offline Routes | `NativeAdvancedActivity` | CODE PRESENT; destructive cache clear now confirmed; offline tile/device QA NOT VERIFIED |
| Maintenance | `NativeAdvancedActivity` | CODE PRESENT; task lifecycle QA NOT VERIFIED |
| Social / Follow / Kudos / recommendations | `NativeSocialActivity` | CODE PRESENT; vector Kudos; feed/privacy QA NOT VERIFIED |
| AI Advisor | `NativeAiActivity`, `NativeMainActivity` | Duplicate-send / late-history overwrite fixes committed in both entrypoints; device QA NOT VERIFIED |
| Group Chat | `NativeChatActivity`, `NativeMainActivity` | Private/group response isolation and background poll lifecycle fixed in dedicated screen; full device/privacy QA NOT VERIFIED |
| Challenges | `NativeMainActivity` | CODE PRESENT; full guest/member/admin QA NOT VERIFIED |

## Phase 7: administration

| Feature | Native implementation | Status / outstanding acceptance |
|---|---|---|
| Site general / appearance | `NativeSiteSettingsActivity` | CODE PRESENT; web-setting persistence NOT VERIFIED |
| Medals | `NativeSiteSettingsActivity` | CODE PRESENT; edit/recalculate QA NOT VERIFIED |
| Users / roles | `NativeSiteSettingsActivity` | CODE PRESENT; one-tap editor and safe partial updates; role/security QA NOT VERIFIED |
| FarazSMS status, sender, OTP patterns, custom send, logs | `NativeSiteSettingsActivity` | CODE PRESENT; direct SMS panel access patched; real provider QA NOT VERIFIED |
| Pattern send / bulk SMS | Source action contract not verified against Web 4.7.46 | **OUTSTANDING; do not invent backend actions** |
| Release manager / chunked APK upload | `NativeAppReleaseAdminController` | CODE PRESENT; worker/thread handling patched; remote upload/signing QA NOT VERIFIED |
| Release rollback | No verified native+backend contract in inspected files | **OUTSTANDING** |
| Notifications / history | `NativeAppReleaseAdminController` | CODE PRESENT; delivery/target QA NOT VERIFIED |
| FCM configuration and test | `NativeFcmAdminController` | Credentials/form threading corrected; token/credentials and device QA NOT VERIFIED |
| System status | `NativeSiteSettingsActivity` | CODE PRESENT; backend data QA NOT VERIFIED |

## Phase 8: mandatory verification gates

1. **Web source**: obtain the exact 4.7.46 restore snapshot/screens (guest, member, admin, offline, active ride), and compare UI route by route; do not compare to an unverified newer public website.
2. **Architecture**: `bash ci/native_web_parity_checks.sh` passes, and no native WebView is instantiated.
3. **Build**: Native RC QA `success` for the final HEAD; compile, tests, lint, manifest inspections pass.
4. **Store**: Android Store Release Build `success` for the **same HEAD**; two APK artifacts exist, signed with the correct established Bazaar/Myket upload keys; verify signatures/certificates and package/version codes. Do not invent artifacts.
5. **Device**: test RTL, typography, card radius/spacing, Android back/modal/drawer, guest/member/admin permissions, GPS/Tracking service continuity, BLE and navigation, both app flavors, privacy and deep links.
6. **Acceptance**: no unverified scope/placeholder/raw JSON UI, no user-data loss, no merge to main prior to explicit sign-off.

### Current automated coverage

The push-triggered `Native RC QA` workflow includes source checks, UI contract assertions, Android unit tests, lint, and Bazaar/Myket release smoke builds. A separate `ci/native_web_parity_checks.sh` now covers 34 source predicates, including Replay, chat-room isolation, AI histories, and FCM form threading. These do **not** replace a signed store workflow or on-device Web-reference comparison.

### Build commands

```bash
bash ci/native_web_parity_checks.sh
gradle :app:testBazaarReleaseUnitTest :app:testMyketReleaseUnitTest --stacktrace
gradle :app:lintBazaarRelease :app:lintMyketRelease --stacktrace
gradle :app:assembleBazaarRelease :app:assembleMyketRelease --stacktrace
```

**Overall status: IN PROGRESS / NOT RELEASE-READY** until the independent CI, signature, device, and Web 4.7.46 acceptance gates pass.

## RC6 delta / unverified gates (2026-10-14)

| Acceptance test | Code-level result | Verification still required |
|---|---|---|
| Bale bot | Read-only operational values redacted unless a safe primitive/identifier is explicitly permitted; no guessed bot action | Authenticated Web 4.7.46 fields, save/test/toggle endpoints and server-side RBAC unavailable; **not implemented** |
| Active ride navigation | Mercator look-ahead positions rider below center; follow, independent heading/north orientation and follow-preserving zoom | Compare against Web reference and physical GPS device, screen orientation, offline/pause/resume; **not verified** |
| Group/live route camera | Follow enabled, mobile bearing when moving, admin credentials kept in manager only | Device moving/stationary heading QA and mixed-role check; **not verified** |
| Raster tile efficiency | Rotated tile bounds, cache scoped by provider, upright attribution, in-flight cap | Actual memory/network/battery telemetry, licence provider terms and rate limits; **not verified** |
| Neshan SDK native camera | Conditional licensed SDK pathway unchanged; default signed build uses XYZ | Official signed licence and genuine SDK pitch/bearing integration still absent; **not verified** |
| APK/CI RC6 | `versionCode 50`, Bazaar/Myket workflows updated for RC6 | Require both workflows success on final RC6 SHA plus certificate verification; **not yet verified** |

## RC7 — reported screen-recording regression (2026-10-14)

| User-visible issue | Code fix | Acceptance |
|---|---|---|
| Raster streets disappear after 5-8 seconds | Persist `PedalNeshanMapSurface` across ride-screen recreation; refresh ride geometry at most once/20s | Live device test PENDING |
| Heavy city-wide tile bursts | 512 logical device pixels/tile, 18 max in-flight, queue rider-centered first; trigger redraw for every visible map | Actual CPU/battery/network telemetry PENDING |
| Huge blank areas during zoom | Render cached lower-zoom parent tile as placeholder; keep ongoing map view | Network loss/zoom test PENDING |
| Navigation appears flat | 4-point projective pitch matrix, 68% rider anchor reprojected through inverse, heading rotate only if valid | Real phone/device reference test PENDING |
| Actual Neshan SDK absent | When Android licence exists, invoke documented Neshan MapView.setBearing / setTilt(55f) | SDK-bound signing licence & QA PENDING |
| GPS data change | No changes to TrackingService, filters, ride-point writes, speed/distance/calories | Compare route logs on device PENDING |

VIDEO evidence: `1000152337.mp4` displayed an OSM raster map flickering into navy-black regions and no visible pitch during a stopped GPS state. The reference Web 4.7.46 and Neshan `neshan.licence` remain unavailable; no Neshan cartography/true bicycle routing claim.

### RC8 native map acceptance (2026-10-14)
| Requirement | Implemented | Evidence still needed |
|---|---|---|
| Native Neshan MapView | `LicensedNeshanMapBridge` uses `org.neshan.mapsdk.MapView` directly | CI APK must contain licensed SDK; actual licence secret may be missing |
| Native pitch & heading | `setTilt(55)`, `setBearing` | Device test with moving bicycle |
| Native recorded vs suggested geometry | Two independent Neshan SDK `Polyline` layers | Device projection/zoom validation |
| Map gestures | Overlay hidden in SDK mode; native gestures exposed | Pan/zoom/recenter device QA |
| License/security | Build gated by non-committed `neshan.licence`; service API token not hardcoded | Confirm SHA1 and official SDK permissions |
| Fallback | Explicitly labelled XYZ, not shown as Neshan | Only for diagnostic / unlicensed testing |

### RC9 — current Neshan MapLibre acceptance
| Feature | Source status | Gate |
|---|---|---|
| New official SDK | `org.neshan.maplibre:android-sdk-opengl:13.4.1` conditional dependency | Compile with test MOBILE key |
| Authorization | Manifest meta-data `org.maplibre.android.API_KEY`; no legacy `.license` | Real MOBILE secret with provider restrictions |
| Cartography | `https://static.neshan.org/sdk/maplibre/styles/dark.json` and `light.json` | Neshan vector tiles visibly loaded on phone |
| Camera navigation | Native `CameraPosition` zoom/bearing/tilt/follow/recenter | Stable heading and pitch while cycling |
| Route drawing | Two distinct native polylines; current rider marker | Position projection and performance on device |
| Tracking integrity | No TrackingService, GPS filter, distance/calorie, or user data changes | Paused/offline/on-device verification |
| Complete route guidance | Current Neshan Map SDK is a renderer, not a built-in bicycle navigation engine | True turn-by-turn bicycle rules and Web UX remain not verified |

## RC11 — user review checklist 1–18 (2026-10-14)

| # | Requirement | Source status | Mandatory remaining acceptance |
|---|---|---|---|
| 1 | Member profiles opacity, social icons, earned medals | Implemented presentation | Real member/bootstrap and UI test |
| 2 | Medal image upload and locked grayscale | Partial: render images, grayscale, edit image_path | No verified medal image upload API or server image storage |
| 3 | Web-style initial splash | Implemented using existing official app art and bootstrap progress | Pixel parity screenshot and launch performance |
| 4 | Home stats three columns | Implemented | Small-screen typography test |
| 5 | Bottom tab bar original vector and opacity | Updated | Visual screenshot/device test |
| 6 | Original PedalPro brand logo | Reuses original `pedalpro_app_icon.webp` instead of custom `pedalpro_logo.xml` | Check clipping across densities |
| 7 | Account page and Bale self-link | Partial: RTL account controls, server-issued self-invite when available | Web account API/auth response/role and complete layout parity |
| 8 | Group+private chat, scroll and notifications | NativeChatActivity now opens from main; bounded list, FCM native entry | Real FCM event delivery to all group/private recipients requires backend |
| 9 | Drawer vector icons/font/opacity | Code updated | Screenshot and smoothness tests |
| 10 | Admin group start/finish/attendance credit | Partial: admin event selector start_ride + confirmation | Server attendance late credit + overlapping personal ride de-dup NOT certified |
| 11 | Rider bicycle marker | Native MapLibre icon uses existing PedalIconView bike vector | Real map tilt/heading alignment |
| 12 | Join CTA and upcoming event register button | Implemented | Status/registration_open and auth testing |
| 13 | Android Back history | Implemented history with scroll and exit dialog | Gesture Back/predictive test across screens |
| 14 | Justified event description & padding | Implemented, lint-guarded | Visual RTL screenshot |
| 15 | Header opacity and manager-only gear | Implemented native role visibility; existing permission check | Server admin RBAC to be audited |
| 16 | Recorded route and Neshan start/end navigation | Partial: polylines, external destination, 2-stage in-app follower | Official Neshan cannot be presumed to import arbitrary cycling GPX / auto-turn |
| 17 | Group live reroute, arrival/cancel and ride | Partial: 10-s polling, 10-s route cache, arrival alert, start-ride controls | Real location/accuracy/pause background + personal/group credit overlap |
| 18 | Full Bale bot management/templates/connected users/mass send | Blocked: no authenticated web bot controller or HTTP request/response schema | Must get actual site's PHP/API source and server-side admin RBAC; no invented endpoints |

No existing `TrackingService` GPS filter or persisted user data was modified in RC11. Neither main nor market listings were updated.
