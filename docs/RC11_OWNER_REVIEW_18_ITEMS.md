# PedalPro Android Native — RC11 handoff and release gates

Branch `native/web-parity`; backup `backup/pre-18-items-20261014`. Version `2.0.0-native-rc11`, versionCode `55`. PR #29 remains Draft, main untouched.

## Completed source changes
- Native splash bound to real API bootstrap, existing PedalPro original logo asset (no new logo).
- Readable opaque header/footer/cards/drawer; vector menu/social icons; 3-column main stats; RTL/justified upcoming event text; correct separate membership/registration buttons.
- Member social profiles colored/grey depending on account presence; medal list earned vs unearned with real greyscale; admin can edit existing image_path references only.
- Account page and server-issued Bale invite link fallback; dedicated NativeChatActivity with both private/group, scroll viewport, bounded bubbles, null normalization, native FCM launch.
- History-aware back, no GPS stop and dashboard exit confirmation.
- Neshan recorded polyline camera fitting, vector bicycle location marker, two-stage choose route start/end, external official Neshan destination handoff and in-app GPS following.
- Group follow target ten-second update, arrival prompt, cancellation back to normal ride; group GPS can begin with existing start_ride/TrackingService actions; admin-only start program with event id and finish confirmation.

## Explicit limits / blocked website API work
1. Bale admin bot templates/token/webhook/connected users/send one/all: private backend action schema and authorization unavailable, **not implemented**. Device-local Keystore secrets are not server bot configuration.
2. Medal image upload: only existing image_path preview/edit; server upload API not present in repo. Creating upload action would be speculative.
3. Group credit double-counting and late attendance: requires confirmed PHP/backend event_distance_credits and recording-overlap rules, **not guaranteed by Android UI**.
4. Native Neshan MapLibre only supports an external destination PIN handoff; its standalone navigator cannot be assumed to accept arbitrary recorded cycling path, and its car directions are not a certified bicycle route.
5. Online GPS, battery/tiles, exact Web 4.7.46 pixel visual parity and runtime state require real phone test.

## Delivery
Build via `Android Store Release Build` GitHub Actions using the already-connected `NESHAN_MOBILE_API_KEY` secret; verify both manifest metadata and APK v2/v3 signatures. Only use artifacts from SUCCESS on same RC11 HEAD. Never merge or upload automatically.

## RC12 follow-up — GPS-before-Neshan preflight
- The recorded-route handoff now offers a real authenticated GPS start before launching Neshan and starts the unchanged TrackingService. A user may explicitly navigate without recording; no misleading "always recording" claim.
- If a server-side active ride already exists, it is reused; the client does not create another ride. If location is stale (>12s or accuracy >35m), no ride is started.
- Group live navigation removes an expired location target and proposed directions when the server reports no group location. This never stops or modifies background GPS.
- RC12 2.0.0-native-rc12 / versionCode 56; signed APK and device QA require separate verification.
