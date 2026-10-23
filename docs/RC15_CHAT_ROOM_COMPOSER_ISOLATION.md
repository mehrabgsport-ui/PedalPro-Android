# RC15 — Native chat room composer isolation

- Branch: `native/web-parity`; PR #29 remains Draft; no merge or store publishing.
- Fixed: switching between group and private conversations clears stale `replyTo`, `editId`, selected reply/edit label and typed text, preventing cross-room message-context reuse.
- Fix is applied only once a private room actually opens; a failed attempt does not erase the current draft.
- Cancel reply/edit uses the same reset method.
- Server chat actions, APIs, GPS recording, TrackingService, map and backend remain untouched.
- Added CI structural assertions checking both room transitions and the reset implementation.
- Release metadata: `2.0.0-native-rc15` / versionCode `59`.
- Validation gates: Native RC QA and signed Bazaar/Myket Android Store Release Build must both succeed at this same SHA. Manual Android testing of group/private switching, keyboard, pending send and draft reset is still required.
