# PedalPro Android Native — RC13 remaining-items handoff

**Branch:** `native/web-parity` (PR #29 stays Draft). **Version:** `2.0.0-native-rc13` / code 57. **No website data or GPS persistence logic changed.**

## Verified source-level changes since RC12

- **Saved GPS routes (#16):** after arriving at the selected starting point with an accurate fix, the native screen automatically swaps from the destination navigation stage into following the original recorded GPS polyline. If no recording is active, it requests explicit consent to start the existing server-backed ride and TrackingService; passive viewing is identified as unrecorded. Neshan external app is launched only for a navigable destination pin, not assumed to accept an arbitrary recorded path.
- **Group arrival (#17):** proximity requires two distinct location updates at least six seconds apart, each gated by accuracy and fresh GPS timestamp; stale cached fixes do not announce arrival. The existing 10-second server polling and user-initiated cancellation remain; cancellation does not stop the TrackingService.
- **Chat (#8):** unique message IDs, bounded in-memory message list and bubble count; receipt updates re-render only on change; idle 3-second polls no longer rebuild the entire view tree. Group and private endpoints remain unchanged.
- **Account (#7):** age/height/weight summary and working native links to AI advisor and personal performance; Bale connection state is displayed only when confirmed by existing server bootstrap data. Server-issued connection link remains the only permitted invite.
- **Attendance (#10):** disallow overlapping checkbox writes, reload authenticated bootstrap after successful `attendance` response, and roll back optimistic UI state on failure. Server remains sole owner of group-distance credit and duplication prevention.
- Regression assertions were added to `ci/native_web_parity_checks.sh`; store and QA workflows updated to release code 57.

## Gaps that Android-only source cannot safely resolve

1. **Medal binary upload (#2):** server `medal_save` with `image_path` exists, but no authenticated medal multipart-upload contract is present. No fake upload is claimed or added.
2. **Bale bot administration (#18):** current authenticated `bootstrap` does not establish bot-name/token/webhook/template/recipient-list/send-one/send-all write actions and request formats. The only connected repository is this Android client and no matching server source was discovered. Device-local token storage is not server administration; do not install fake endpoint handlers or ship a user's bot secret in the APK.
3. **Group event credits (#10/#17):** although `attendance` and `start_ride` are called, server-owned `event_distance_credits`, delayed attendance and overlapping individual GPS accounting require PHP/schema access; a client cannot correctly award or de-duplicate authoritative server balances.
4. **Native/Web UI pixel parity and runtime routing/FCM (#7/#8/#16/#17):** requires real owner/member/admin sessions, original website reference, and on-device GPS and notification tests. GitHub Actions source checks, lint and compile are not that evidence.

## Mandatory release gates

- Both Native RC QA and signed Bazaar/Myket workflows must report success for **the same final HEAD**.
- Confirm on a physical Android device: starting/stopping an actual ride, background GPS while Neshan is foreground, start-point transition after return, two-fix group arrival, private-chat receipts and account navigation, and authentic Neshan map tiles.
- Never merge to `main` or publish to Bazaar/Myket without explicit approval.
