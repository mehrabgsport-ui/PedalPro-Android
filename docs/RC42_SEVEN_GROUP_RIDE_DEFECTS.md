# PedalPro RC42 — seven owner-reported defects (development branch only)

Scope: reports from a real group ride on 2026-10-22. Source changes reside on `native/web-parity`, PR #29 Draft. Android `2.0.0-native-rc42`, code 85, not released. Never publish, alter live PHP, touch Production MySQL or merge main without owner authorization.

## Native fixes committed
1. Current ride map fetches `pp_live_riders` and screens out self, expired/invalid rider rows. For up to six server-authorized riders, calls `ride_live_points` with per-ride cursor, bounded history and separate amber track + cyclist-with-amber-ring symbol. Other users' routes must not be inferred from raw third-party data. A clear status labels empty lists and request errors. Stale peer markers expire after 45 seconds during network failure.
2. Public Beacon/SOS App Links are no longer silently consumed into the Android dashboard. The native launcher detects live links and opens an external web browser explicitly. **Server routing remains unverified**: the returned URL must resolve to a live map page on the website, not a generic homepage. The Android client cannot repair a server redirect.
3. Live watcher preserves the target ride's received track separately (amber) while current rider's location continues to be shown. Selection respects the exact `ride_id`; refreshes do not erase the watched track. Old histories are memory-bounded.
4. Arriving at the group enables `pp_cycling_directions` by default and requests updates for moving target. Map follows the user; no automotive routing is falsely presented as cycling directions. A real street route requires the server's validated cycling profile.
5. Header unread indicator polls the existing `chat_bootstrap` (20s, foreground only), hides when empty, and opens `NativeChatActivity`. FCM `type=chat|group_message|private_message` routes into chat even with no action_url. Real background pushes require server dispatch, token registration and end-to-end tests.
6. Start-ride preflight displays battery optimization explanation and direct application settings button.
7. Cherry-picked the narrowly scoped PR #30 Bale user/admin reconnect UI into the development branch. Admin can request fresh personal invitation/fallback to public bot and then explicitly test webhook/message queue. Backend event production/delivery still not verified.

## Backend contracts required before these seven issues can be called closed

### A. Live public/authorized rides
- GET `pp_live_riders` must return `{ok:true,riders:[{ride_id,user_id,display_name,lat,lng,age_seconds,speed_kmh}]}` including **only** rides visible to this signed-in viewer by server-side ACL. Exclude self where feasible; coordinates bounded and fresh (<=45s). Public rides visible to other members and opted-in private rides visible only to explicitly authorized viewers.
- GET `ride_live_points?ride_id=R&after_id=P&limit=300` must verify the same viewer ACL on EVERY call; return exact ride (id/status/owner_name/last_lat/last_lng), chronologically increasing points with stable unique IDs, last_id and no silent fallback to current user's ride. No caching of unauthorized histories.
- Revocation, stop ride, privacy settings and removal from viewer ACL must immediately stop disclosures. Test users A, B, C: public A visible to B/C; private A visible only to B if invited; C rejected on both endpoints; no cross-user IDOR.
- Verify point timestamps and live rider updates when GPS continues in foreground, screen locked and cellular data changes.

### B. Live share URL
- `pp_beacon_create` must return a short-lived HTTPS link that opens a dedicated **public live map viewer** on first load (including without logged-in cookies, on browsers without the Android app).
- Viewer must use an unguessable, scoped, expiring token, no auth/session bypass and a constrained read-only feed. Test incognito browser, active ride, moved coordinate, stopped/expired link, invalid token and redirect routing. Do not log or bundle viewer tokens.
- Fix the PHP/router/rewrite path. Android App Link handling is corrected, but an independent website redirect to /home is a remaining blocker.

### C. Reach-moving-group navigation
- GET `group_nav_state` returns timely leader/group lat/lng and `location_age_seconds` (max 30). GET `pp_cycling_directions` must return an actual **cycling-regular** geometry with >=2 valid points, distance_m, duration_s and instruction steps, using a cycling-capable provider configured with server-side credentials.
- No invented car geometry; test deviation reroute and moving leader roughly every 10 seconds. Missing provider is a failed dependency, not a completed navigation fix.

### D. Chat + notifications
- `chat_bootstrap` reports accurate `unread_count` (thread/room and optionally top-level) and updates read receipts when opened.
- For each committed private/group message, dispatch FCM once to authorized recipients, with `type=group_message` or `type=private_message` and room/action metadata; no alert to sender. Dedup with message id/nonce. Verify POST_NOTIFICATIONS permission and Android 13+ user consent.
- Test sender/recipient with app open, background, force-closed and network loss. Foreground badge does not substitute for background FCM delivery.

### E. Bale robot events and reminders
- Check the real `bale_status` response, token_configured, webhook_configured, enabled, connected_count, administrator chat id and notification template switches.
- Verify durable, idempotent Bale queue entries for completed user signup (`auth_signup_finish`), event signup (`register_event`), withdrawal (`unregister_event`), new app account and configured program reminders. Use authenticated server-side event hooks for web AND Android, not client-only sends.
- Confirm cron/worker schedule, locked/queued/sent/failed rows, retries with backoff, send response, handling of Bale API HTTP failures, stale worker recovery and opt-out for member-directed reminders.
- Send one approved sandbox test to the administrator, confirm receipt end to end, log only event IDs and redacted failure reasons. Never log full tokens, chat ids or private phone data. Do not send unsolicited test alerts from Production.

## Verification status
- Native GitHub Action QA and store-signed build artifacts are CI checks, **not** in-field/staging proof.
- RC40 Staging bundle exists in project Library but raw bytes were not materializable in this runtime. GitHub Android repository deliberately does not hold the private PHP/DB implementation.
- Cannot claim to have fixed server routing, public live ACL, cycling provider, actual FCM broadcast, or Bale reminder/notification automation without authenticated staging PHP/DB access and tests.
- Correct release gate: unit/source QA, real Neshan build with MOBILE key, two real accounts and two phones, tokenless public viewer in incognito, actual backend delivery, signing identity and explicit owner's publication approval.
