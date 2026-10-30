# RC18 — Bicycle-only routing / GPS identity / 2.5D marker

## Android (native/web-parity)
- Render a perspective-shaded two-wheel bicycle billboard sprite instead of a flat circular pictogram, preserving Neshan MapLibre and TrackingService map data. This is not an actual rotating 3-D mesh.
- New CyclingDirectionsClient makes authenticated GET pp_cycling_directions, demands profile cycling-regular, validates geometry and refuses car fallback.
- NativeSavedRouteFollowActivity draws proposed cycling route from current fresh GPS fix to the user-selected recorded start/end, then requires two distinct accurate fixes at least 6s apart before arrival. On reaching the beginning it transitions to follow the original saved polyline.
- Moving group routes use bicycle-only server API; staleness from location_age_seconds or legacy Asia/Tehran recorded_at prevents navigation/arrival using old leader coordinates. Transport errors clear stale targets.
- GPS accepted point gets stable client_point_id; pending queue points are bound to the ride at creation. Early starter point also gets a stable ID. No GPS distance/speed threshold or map projection changed.
- Version 2.0.0-native-rc18, code 62.

## Staging backend patch delivered separately
- PHP source snapshot 4.7.46 patch and manual SQL migration for ride_points client_point_id unique per ride, received_at and millisecond recorded time; support replay up to 7 days tied to activity start, diagnostic auth/bounds and group freshness.
- Server bicycle routing adapter uses ORS cycling-regular only, via server-only PEDALPRO_ORS_CYCLING_KEY. If not deployed/configured, native deliberately shows routing unavailable instead of car routes.
- Staging deployment must precede declaring routing and GPS end-to-end complete. No production deployment or live migrations were executed.

## Unverified
- Android emulator/device, real ORS credential, server connectivity/coverage, correct isometric wheel-to-road anchor on a phone, accuracy of historical/late group credits, screenshots vs web4.7.46.
- Backend late attendance and overlap credit policy are unresolved; must not silently backfill live distance.
- PR29 stays Draft. No merge into main or store publication without approval.
