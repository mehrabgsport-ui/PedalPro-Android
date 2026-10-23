# PedalPro RC6 — Native admin and navigation delta
Date: 2026-10-14
Branch: `native/web-parity`
Backup: `backup/pre-native-rc6-20261014`
Base RC5: `16f227f150e587b79a672faadb4364c87645de8a`

## Implemented source changes
- Administrator's existing single settings entry beside Account retained; Bale tab masks unknown values, keeps connection management read-only until verified Web endpoint contracts exist.
- Active-riding XYZ screen displays the rider below center with a bearing-responsive camera target in Mercator coordinates. North-Up vs Heading-Up is independent of follow/recenter. Zoom buttons retain follow.
- Manual panning compensates for rotation/pitch; suggested roads and recorded GPS remain separate lists.
- Only tiles covering the inverse-rotated viewport are requested; existing in-memory/on-disk caching remains, scoped by map provider. Map credits are upright.
- Live/group navigation enables follow and consumes valid Fused Location bearing; its duplicate admin-credential shortcut was removed.
- No WebView, GPS/tracking algorithm, user data, backend or API mutation.
- Android application version: `2.0.0-native-rc6` / code `50`.

## Not yet proven
- Exact Bale 4.7.46 website controls, their API save/test/toggle contracts and permission enforcement.
- Neshan Android `neshan.licence` for `ir.pedalpro.app` and signing-key SHA1; actual licensed SDK map, pitch and heading camera on phone.
- Signed release job on final SHA, APK certificate match with marketplace uploads, real device runtime/RTL/low-light/offline/GPS battery measurements, source screenshot pixel diff.
- Car-road Neshan directions are not automatically safe cycling routes.

## Provider choice
Use officially licensed **Neshan Android SDK** if genuine Neshan cartography and camera APIs can be compiled and physically accepted. Keep the existing lightweight XYZ renderer as an honest, labelled fallback while licence/authenticated source is absent. Do not ship a MapLibre engine without an authorized vector style/tile endpoint; MapLibre is a renderer, not a Neshan map-data licence.

Sources: https://github.com/NeshanMaps/android-neshan-maps-starter-master-new ; https://github.com/maplibre/maplibre-native

## Deployment
PR #29 must remain Draft; no main merge, no publication. Signed Bazaar/Myket artifacts must be retrieved only from a successful `Android Store Release Build` on RC6 HEAD. Run `Native RC QA` on the same SHA. Nothing in these source-level changes constitutes GPS or visual device QA.
