# RC20 handoff

Android native/web-parity version 2.0.0-native-rc20, versionCode 64.
- Align admin medal management UI with authenticated POST medal_image_clear contract (medal_id) shipped in separate **staging** backend patch; prompt confirmation before deleting images and medal records.
- Fix medal definition metric display to use metric_type (not obsolete metric).
- Existing RC19 late attendance, RC18 bicycle-only navigation and GPS tracking flows preserved.
- Backend RC20 is deliberately NOT committed to this PUBLIC Android repository. The backend code ZIP is delivered separately to the owner, for controlled staging deployment.
- Build/CI are not substitutes for MySQL/Cron/ORS provider/mobile GPS/device screenshot testing.
- No live cPanel credentials, staging server, and external ORS credential were available to this GitHub-only execution.
- No production migration, PHP server file edit, actual Bale message, store publication or main merge was carried out.
- PR #29 remains Draft pending environment acceptance.

- RC20 chat private-room creation moved from GET to POST + CSRF in Android and backend; staging web inline chat script must also be updated with bundled one-change patch tool, otherwise server rejects legacy GET with HTTP 405.
