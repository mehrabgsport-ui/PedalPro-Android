# PedalPro Native Map — diagnosis, provider setup and acceptance

## Root cause found in the reported screenshots (2026-10-14)

The older `NativeRouteView.java` drew **only a six-by-six dark grid and GPS polylines**. No SDK, raster tile HTTP calls or base-map drawable existed in that class. The empty GPS map is therefore **not evidence of a missing GPS location permission or a broken website API key**. GPS coordinates can arrive even while the map background is blank.

## Native implementation

- `NativeRouteView.java` retains the app's existing `setPoints`, `appendPoints`, `setCurrent`, `setTarget` route and marker interfaces. Camera/projection is now Web Mercator to align track, marker and tile pixels.
- `PedalMapTiles.java` fetches native HTTPS XYZ raster images (no WebView), with a bounded two-thread downloader, 72-image memory cache, seven-day local file cache, application-specific User-Agent and visible attribution.
- Error and HTTP 401/403/429 backoff is included; the last valid GPS/route overlay is still drawn if the tile server fails. This does **not** affect the TrackingService GPS filter, speed, distance, persistence, location permission handling or backend endpoints.
- The validated starting GPS fix seeds the **visible map camera** immediately on entering a new ride; subsequent GPS/UI updates remain unchanged.
- OpenStreetMap public raster tiles are used **only as the test/development fallback**. Their public servers are not an assured production service; bulk/offline prefetch from OSM public tiles is forbidden. See <https://operations.osmfoundation.org/policies/tiles/>.

## Configure a production map provider

Supply a **licensed HTTPS XYZ raster tile URL template** with `{z}`, `{x}`, `{y}` placeholders, the required key/token, the provider's attribution text, and confirm Android/mobile usage in the provider account. Example (placeholder only; do not check a real token into Git):

```sh
gradle :app:assembleBazaarRelease :app:assembleMyketRelease \
  -PpedalproMapTileTemplate='https://maps.example.org/styles/cycling/{z}/{x}/{y}.png?key=RESTRICTED_ANDROID_KEY' \
  -PpedalproMapAttribution='© Licensed map data provider'
```

Store provider configuration in private build secrets (or a restricted runtime configuration). Restrict a client-exposed map key by provider-supported package name, signing certificate, scopes and quota. Never assume a **JavaScript/browser key** restricted to website HTTP referrers works as an Android SDK or direct raster-tiles key. If the existing web provider only offers native SDKs or vector PBF tiles, use that **Android SDK/MapLibre integration** rather than passing a JS/tiles address to the raster loader.

If a provider specifies an attribution requirement, set `pedalproMapAttribution` accordingly. Tile imagery is not equivalent to turn-by-turn routing; PedalPro's existing navigation/TrackingService logic has **not** been replaced.

## Acceptance on device

1. After starting a ride, the map should show city streets and roads instead of a solid dark grid.
2. Before any points arrive, show the waiting-for-GPS state; after the first valid fix, center the map on the actual fix, not a hard-coded city.
3. When there are route points, the cyan polyline, live current position and yellow target must align with street geometry.
4. Disconnect the internet: cached tiles can display; GPS/route overlay must still be visible. Do not prefetch public OSM tiles for offline routes.
5. With an invalid tile API key, an intelligible map-access error should appear; the GPS metrics and recorder must continue functioning.
6. Compare with the exact PedalPro Web 4.7.46 map screenshots and test Android touch interaction/performance. **Pixel parity, map pan/pinch and device behavior are still unverified**.

## Other screenshot differences corrected

- Duplicate title/blank hero card on Start GPS removed.
- Upcoming program's missing `finish_location` no longer displays literal `null`.
- Main bottom-dock navigation and drawer iconography converted from emoji to native vectors.
- RTL input selection text enlarged to 14sp.
- Added scroll clearance so fields and last actions are not trapped underneath the fixed ride dock.
