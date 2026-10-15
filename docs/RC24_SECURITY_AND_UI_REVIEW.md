# PedalPro RC24 — verified Android changes and deployment limitations

Source branch native/web-parity. Based on RC23 commit 7f0c54176b898f626d97216e9bf21de0786ffb78.

Implemented in this change:
- Ride-detail graph is adjacent to the map, with navigation action immediately below; existing synchronized scrub map preserved.
- Screen stays awake while the active ride view, live navigation or saved-route follow is visible; returns to platform sleep policy on exit.
- Safer HTTPS-only image retrieval. Cookies accompany requests only to the exact pedalpro.ir origin, automatic redirects are disabled, response MIME/size and decoded bitmap dimensions are bounded.
- Prevent the public Firebase config endpoint from persisting a Neshan routing service secret. Push device registration uses the authenticated native PHP session/CSRF rather than legacy WebView cookies.
- Existing server-backed medal metric types rendered as selector, retaining four known types and unknown historic types; no unsupported metric invented.
- Admin user listing displays server-returned phone only inside admin-only settings; backend permissions must be audited.
- Compact weather forecast displays dominant wind direction, wind speed, rain probability (the values already existed in Open-Meteo data). No extra weather requests.
- Presentation-only null normalization for standard text components. Does not rewrite PHP/MySQL stored fields.

NOT ACCEPTED as complete:
- Full 20-item program requires actual PHP/MySQL and cPanel source, staging migrations, API authorization tests (esp. user-created groups, invitations, group chat, points/medals, follow lists, late joins, truthful moving-time accounting, Bale linking, privacy and live riders), and on-device GPS/Neshan validation. Do not deploy fabricated endpoints.
- 500 KB image upload compression across every screen, global Jalali/time picker, PNG logo sourced from approved artwork, map battery style/turn-by-turn and full RTL of legacy website require further implementation.
- Security follow-up: encrypt SessionStore and raw GPS persistence with backward-compatible migration, audit signed update upload, cookie flags and group-level authorization on server; conduct OWASP MASVS/ASVS reviews.
- No main merge, cPanel deploy, or store publication.
