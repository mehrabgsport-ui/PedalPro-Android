# RC14 — Fixed native chat composer and keyboard-safe viewport

Scope: issue 8, group/private chat only. No WebView, backend schema change, TrackingService change or user-data migration.

- NativeScreenBase keeps legacy scrolling pages unchanged; NativeChatActivity opts into a flex-height viewport.
- Tabs and composer remain outside the messages ScrollView. Only the bubble list scrolls; footer remains visible.
- The Android IME inset is applied to the fixed viewport and the bottom ride dock hides while typing.
- Reply/edit cancel is now shown and can reset the composition without sending. Private-thread unread counts appear only when supplied by the existing chat_bootstrap response.
- Existing room-generation guards, 240-message in-memory cap, receipt change detection and offscreen polling remain intact.

Validation: structural regression guards, Gradle unit/lint, Bazaar/Myket signing CI required on RC14 SHA; real-device IME height, rotation, RTL and private/group messaging still need owner QA. No site/backend changes. PR #29 stays Draft and main unchanged.
