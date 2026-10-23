# PedalPro 2.0.0-native-rc4 — Settings, Neshan and Full-Screen Ride

Branch: `native/web-parity` · versionCode **48** · no merge into `main`.

## Screenshots and root cause

The user-provided Web and Android screenshots showed the Web ride map occupying almost the entire viewport, with compact floating ride state banners, map gestures, a side toolbar and a fixed bottom HUD. The prior Android native ride screen was a long scrolling form with a **280dp map card** and stacked full-width buttons below the map. The same map provider alone could not correct this fundamental layout difference.

## RC4 ride UI changes

- NativeMainActivity retains the same TrackingService / GPS recorder, start/stop, manual pause, live share, details and OLED actions.
- When an active ride exists, `renderImmersiveRide` moves the existing map view into a full-screen FrameLayout and displays compact dark floating status bars, an accessible stop button, pause/resume and back actions, group navigation, an expandable controls panel and fixed bottom time/speed/distance/GPS metrics.
- `PedalNeshanMapSurface.setFullRideMode(true)` moves zoom/recenter controls to the side of the map. The map uses the same native provider/fallback as before; it has **not** magically gained Neshan tiles.
- On leaving the ride page, `hideImmersiveRideOverlay` removes the full-screen layer. All actions use the original handlers; recorded GPS and backend data remain unchanged.
- Actual on-device pixel parity, system bar sizing, map gestures and comparison to the canonical Web 4.7.46 screens **remain to be tested**.

## RC4 app settings

New `NativeAppSettingsActivity`, registered as `android:exported="false"`, accessible from profile, user drawer and the phone settings section.

| Setting | Availability | Implementation |
|---|---|---|
| Neshan MOBILE key | All local app users | Encrypted in Android Keystore; save, status and delete |
| Neshan Services / Routing API key | All local app users | Same existing AES-GCM Android Keystore store, separate encrypted field; save, status and delete |
| Default opt-in street-route guidance | All local app users | Local toggle, read by `NativeLiveNavigationActivity`; not bicycle-legal route guidance |
| Device / Android permissions | All local app users | Opens the app's own Android settings, no modification of GPS recorder |
| Site General | Admin after verified bootstrap only | Existing native site settings panel |
| SEO / Robots | Admin only | Native SEO title, description, keywords, robots, HEAD; uses existing `site_settings_save` action; preserves general fields |
| Site + app appearance | Admin only | Existing `ui_settings_save` panel |
| Medals, users/roles, SMS/OTP, releases, FCM, system | Admin only | Deep-linked existing native admin tabs |

**Important naming distinction:** the `seo_robots` field controls search engine crawler indexing. It is not proof of settings for separate chatbots, Telegram bots or custom automation robots. No unknown backend endpoint or bot credential is invented.

## Neshan credentials and provider limits

`MOBILE` and `Services` access tokens are independent of the Neshan Android SDK licence. Entering a key in Settings saves it; it **does not** replace the default XYZ tiles with licensed Neshan MapView. Official native Neshan requires its authorized build-time licence/SDK, or a documented MapLibre mobile tile integration. The production shared Services key should remain on a PedalPro server, rather than distribute unrestricted API access through clients. Live road-routing uses the existing key store when the user explicitly enables it.

## Still missing versus canonical Web

1. Provider-licensed Neshan MapView with identical style/POI/traffic imagery in the distributed APK, not the current default XYZ fallback.
2. True bicycle turn-by-turn route matching and verified map matching / ETA (Neshan car-road directions are only a labelled suggestion).
3. Exact full-size layout geometry, floating panel position, zoom control and navigation animation after device screenshot comparison.
4. Any **additional** site/bot management fields not exposed by existing PedalPro bootstrap/admin endpoints: backend contract must be identified, not guessed.
5. On-device QA, role testing, store update acceptance and explicit owner sign-off.

## Mandatory build acceptance

Run Native RC QA on the same final head (static source invariants, Android unit tasks, Lint, Bazaar/Myket smoke builds). Run Android Store Release Build (proper signing and signature verification). Never merge to main or publish automatically until QA is accepted.
