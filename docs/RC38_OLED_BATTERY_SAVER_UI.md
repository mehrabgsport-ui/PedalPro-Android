# PedalPro Android Native RC38 — OLED route display

**Branch:** `native/web-parity`, PR #29 Draft.  
**Version:** `2.0.0-native-rc38` / versionCode `82` / `ir.pedalpro.app`.

## Design contract implemented

- AMOLED toggle in `PedalNeshanMapSurface` continues using persisted `pedalpro_map_display/amoled_enabled`. Off leaves the legacy Web 4.7.46-inspired colors intact.
- **Pure-black (#000000)** map control panels and buttons when ON, with subdued gray borders; in-ride top/status/tools/metrics panels, native app header/bottom dock, and shared `NativeScreenBase` cards/inputs/actions inherit the same setting. The wallpaper is hidden in AMOLED.
- The older Neshan map road treatment is intentionally **unchanged**. No new recoloring of road layers and no edits to GPS/MapLibre camera/basemap.
- **Neon cyan recorded trail** appears only with AMOLED ON: outer low-opacity wide stroke (22), mid-opacity halo (13) and narrow bright core (7.5). These are presentation-only overlays for the exact same route coordinates.
- Native diagnostic/fallback Canvas map uses corresponding low-cost three-stroke recorded path rather than expensive blur/shaders.
- Enabling/disabling AMOLED on the active ride map immediately re-skins navigation panels while preserving the instantiated Neshan map and the tracking service.

## Guards and limits

- No changes to `TrackingService`, backend `api.php`, GPS filtering, stored ride points, sessions, user data, privacy checks, or release signing keys.
- No WebView.
- True-black backgrounds reduce OLED emitted light, but an illuminated neon trail consumes some display power; **no measured battery-saving percentage is claimed**.
- Visual acceptance on a real AMOLED screen, correct map rendering, persisted preference, and same signer as current Bazaar/Myket production must still be verified before public release.
- CI: source contract, Gradle Android unit tests/lint, APK build and signed Bazaar/Myket workflow; do not merge PR #29 automatically.
