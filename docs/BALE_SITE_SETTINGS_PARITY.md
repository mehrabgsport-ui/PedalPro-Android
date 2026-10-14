# PedalPro Native — Admin Site Settings and Bale Bot Parity

## Source/evidence boundary — 2026-10-14

Website target: `https://pedalpro.ir`, historical reference Web `4.7.46`.
The website's logged-in **administrator settings** view and its source bundle are **not available in the accessible GitHub repository, Library/Project files or connected Drive**; public site retrieval was unsuccessful. The only connected GitHub repository is `mehrabgsport-ui/PedalPro-Android`. Its native `api.bootstrap()` and existing administration actions are the reliable contracts accessible here.

**Do not confuse** Web SEO `seo_robots` / robots.txt with **ربات بله (Bale messenger bot)**. The Bale integration is a separate operational backend feature. No guessed Bale endpoint (`bale_save`, `bot_webhook`, etc.) has been added.

## The single administration entry point

- **Only** the admin settings gear beside the account in the native header opens `NativeSiteSettingsActivity`, guarded by `is_admin` from `api.bootstrap()`.
- The duplicated admin drawer section, profile Neshan-settings shortcut and ride tools settings shortcut were removed. Existing per-device Android battery and GPS permissions remain in the member's phone-settings page.
- Existing legacy `NativeAppSettingsActivity` redirects authorized admins to the unified settings screen (Neshan section) and exposes **no keys to non-admins**.

## Unified settings tabs / verified native integration

| Section | Data/behavior | Save contract |
|---|---|---|
| General | `site` (club name, subtitle, about page, SEO) | Existing `site_settings_save` |
| SEO / Robots | Search index/meta/robots/HEAD | Existing `site_settings_save`; **not Bale bot** |
| Neshan map | Separate encrypted MOBILE key and Routing API key; explicit street-navigation preference | Android Keystore local save; no website schema is invented |
| Bale bot | Read-only discovery of real `bale_*` objects returned by authorized `bootstrap`; sensitive key/token/auth fields omitted | **Not enabled** until the real Bale server API contract is verified |
| Appearance | `ui_settings` and native visual options | Existing `ui_settings_save` |
| Medals | Edit and recalculate | Existing admin medal endpoints |
| Users and roles | User editor + roles | Existing `admin_user_save` / `admin_role_save` |
| SMS | FarazSMS sender/OTP/manual send/log | Existing `sms_*` endpoints |
| Application releases/notifications | Android release manager and notification history | Existing documented native controller |
| Push / FCM | Native Firebase admin controller | Existing `fcm_*` |
| System | Existing site version and totals | Read-only |
| Other site fields | Additional non-secret primitives returned in `site` or `ui_settings` | Read-only audit, preserving unknown backend fields |

**Bale status disclosure:** if the backend does not include Bale metadata in the bootstrap response, the tab explicitly reports this gap. It does **not** fabricate a bot token, webhook status, message delivery, or a successful save.

## Remaining work before claiming exact Web parity

1. Obtain the actual **authenticated Web 4.7.46 settings form / HTML/JS** or a safe API schema/export of its settings tabs and the Bale bot read/save/test actions. **Remove secrets and all user data from any export**.
2. Map each Bale field to a verified backend action and implement editable controls only after input validation and role enforcement can be demonstrated. Mask all tokens and access keys; never reveal a stored server-side bot token.
3. Compare site settings field-by-field, including default values, save validation, errors, API responses, permissions and RTL visual dimensions. This is **not complete** based on the presently accessible evidence.
4. Test the app admin vs member roles on Android; ensure that the admin gear and private keys never appear for ordinary accounts.
5. Pass Android RC QA, signed Bazaar/Myket workflow and on-device visual state QA for the **same Git SHA** before final sign-off. No auto-merge into `main`.

## Map camera improvement, separate from administration

The Native XYZ fallback now offers a rider-follow, bearing-up camera with a lightweight 2.5D visual pitch, sourced from TrackingService's existing `bearing` update **only when moving**; the rider marker remains upright. The authentic Neshan SDK's 3D camera is not available in default builds without `neshan.licence`. Do not claim true 3D SDK tilt or street-safe cycling routing from the fallback.
