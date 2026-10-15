# PedalPro RC22 — 6-item ride QA and staging handoff

Android branch `native/web-parity`, version `2.0.0-native-rc22` (66).

1. Group program detail: adds a user-invoked raster tile fallback inside program modal and an external `geo:` map action. A grey Neshan vector style can still indicate a wrong MOBILE key, blocked style URL, or network; remote key/network acceptance must be tested on device.
2. Resume ghost cyclist: removes previous rider, goal and polyline annotations prior to reinitializing MapLibre style, then rebuilds one rider. GPS track retained.
3. Live speed flicker: confidence-aware, display-only EMA/temporary hold in full-screen and OLED screens; pauses and prolonged losses zero speed. Does not falsify underlying GPS or distance.
4. Stored speed chart: suppresses isolated zero samples and high spikes more consistently without rewriting stored route or real pauses. Historical recording errors require raw server diagnostics before retrospective corrections.
5. Native ride details include interactive profile with Elevation/Speed/Time switch, selected GPS location, speed/time/elevation/distance inspector and synchronized map cursor. Supports both active and saved details. Poll doesn't rebuild during 6s after a touch.
6. Live riders are fetched every ~10s in active ride and group navigation. Markers display names and speed; old locations removed after 45s. **Requires RC22 server-side GET pp_live_riders deployed to staging then production**. A private user's ride is not exposed by default; private users may see authorized/public riders. Backend SQL enforces permission and returns only newest points, never other riders' full tracks.

The separate backend ZIP is **not added to the public Android repository**. Apply it only to a staging copy after DB backup and version/hash comparison. Neither a cPanel session nor SQL access was available here; no server was modified and none of these features has been verified end-to-end on real Android devices.

QA includes pure Java speed regression and Android source-architecture checks. Do not merge Draft PR #29 or publish to Bazaar/Myket without staging and rider acceptance.
