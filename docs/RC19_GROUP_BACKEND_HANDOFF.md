# RC19 group distance / backend handoff

Android release: 2.0.0-native-rc19 (code 63), branch native/web-parity.
- Admin-only late group join uses POST event_late_add with current server attendance time.
- For attendance marked after program completed, ask the administrator for actual Gregorian join timestamp; never silently attribute full group distance.
- Server GPS accuracy configurable from validated bootstrap ride_config within V5 25..35m.
- Prior personal-ride/GPS and group-segment safeguards preserved.

Separate RC19 staging PHP patch:
- Migration RC18 then RC19: stable point IDs and attended_at, event_member_ride_links.
- Leader GPS polyline-derived group credit fraction from check-in time (interpolated); no retroactive rewrite for legacy null join timestamps.
- Explicit personal ride link prevents double counting; prior heuristic remains legacy fallback.
- Enforced GET read allowlist, bounded authenticated diagnostic posts, fail-closed user access, protected archives, private group policy.
- Bale write-only token can be encrypted with server-only SODIUM secret key; old plaintext migrates on settings save; opt-out honored at send and enqueue, stale worker recovery.
- Cycling coordinates accept valid global lat/lng, never falls back to car routes.

Manual staging migrations, live hash comparison, API integration tests, real ORS key, device route tests and web screenshot QA are NOT completed. No production database or files changed. PR#29 remains Draft.
