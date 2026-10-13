# PedalPro Web Parity Contract

Canonical web reference: **PedalPro 4.7.46** (`PedalPro_RestorePoint_2026-10-13_Pre_Native_2.0.0.ppup`).
Native parity branch: `native/web-parity`.
Rollback branch: `backup/pre-native-web-parity`.

## Non-negotiable rules
- The web source is the sole source of truth for UI, layout, labels, ordering, navigation and behavior.
- Native Android only. No WebView/PWA/iframe.
- Do not redesign, simplify, rename, regroup, add, remove or reorder UI unless the web source itself does so.
- Preserve API contracts, user data, backend behavior, GPS/tracking, map behavior and activity/group/challenge logic.
- GPS/map logic is out of scope unless explicitly requested.
- `main` remains untouched until final user acceptance.
- Large changes require a rollback point.

## Framework decision
The existing application is already a native Android codebase with direct `TrackingService`, BLE sensor managers, Firebase messaging, Android permission flows, Bazaar/Myket product flavors, signing and CI. Replacing that runtime with React Native would change the technical layer that owns tracking and lifecycle behavior. Under the requested framework exception, parity work therefore remains on the existing Android Native/Java stack and changes only the presentation/navigation layer.

## Canonical tokens
Base:
- bg: `#06111e`
- panel: `#0d1c2c`
- panel2: `#102338`
- line: `#21364d`
- text: `#f5f7fb`
- muted: `#9fb2c8`
- green: `#2ee6a6`
- blue: `#2b92ff`
- pink: `#ff557e`
- orange: `#ff7c46`
- purple: `#8d63ff`
- yellow: `#ffd94a`
- red: `#ff5c6c`

Final reference skin:
- cyan: `#16e4ef`
- cyan2: `#22c9f5`
- ref bg: `#05111d`
- ref text: `#f7fcff`
- ref muted: `#d0e2ef`
- ref border: `rgba(255,255,255,.24)`
- generic primary button: `linear-gradient(135deg,#13dce8,#25c8f4)`
- generic button radius: `17px`
- generic page card radius: `26px`
- generic input radius: `15px`
- map radius: `24px`

## Mobile header
Source class: `.mobile-head.glass-topbar`.
- sticky at top
- current mobile radius: `0 0 26px 26px`
- bottom padding: `14px`
- top padding: dynamic `DATA.ui.mobile_header_top_gap_px` + safe-area inset; default `20px`, clamped `6..50`
- current mobile glass: `linear-gradient(180deg,rgba(24,59,82,.34),rgba(8,31,49,.42))`
- blur: `30px`, saturation `165%`
- order: logo, account/login, admin site-settings action when admin, hamburger

## Dynamic UI values from bootstrap
Read from `DATA.ui` and mirror in Native:
- `mobile_header_top_gap_px`
- `home_eyebrow`
- `home_tagline`
- `home_primary_button_text`
- `home_secondary_button_text`
- `ride_button_text`

## Mobile drawer
At `max-width:760px`:
- fixed right-side shell
- width: `min(86vw,350px)`
- height: `100dvh`
- padding: `18px 12px calc(12px + safe-area-bottom)`
- only menu list scrolls
- side-bottom is hidden
- grouped navigation style:
  - nav padding `8px` (mobile scrolling shell later uses `6px 6px 18px`)
  - primary radius `14px`, padding `11px 12px`
  - group radius `15px`, border `rgba(255,255,255,.07)`, background `rgba(255,255,255,.025)`
  - group summary mobile padding `13px 12px`
  - group body gap `3px`, padding `0 7px 8px`
  - subitem min-height `42px`, radius `11px`, font `12px`
  - active subitem bg `rgba(32,228,241,.09)`, text `#55eff7`

## Navigation inventory
Primary:
- صفحه اصلی
- فعالیت‌ها و برنامه‌ها
- مسیرهای GPS

رکاب‌زنی:
- شروع / ادامه رکاب‌زنی
- سنسورهای Cadence / Power
- رسیدن به گروه در حال حرکت
- اشتراک زنده Beacon

مسیرها و اکتشاف:
- مسیرهای من
- مسیرهای پیشنهادی
- Segments
- Heatmap شخصی / باشگاه
- Heatmap عمومی PedalPro
- مسیرهای آفلاین
- Flyover

باشگاه:
- اعضا
- چالش‌ها
- رتبه‌بندی هفتگی
- کشف برنامه‌ها
- گالری

ارتباطات:
- چت‌روم
- فید اجتماعی / Follow / Kudos
- مشاور هوشمند

عملکرد من:
- آمار و تحلیل
- Training Load / Fitness
- Replay فعالیت
- ارتفاع و آب‌وهوا
- وضعیت جسمانی
- دوچرخه‌های من
- سرویس و نگهداری

ایمنی و حریم خصوصی:
- SOS و مخاطبین اضطراری
- Beacon
- Privacy Zones

حساب من:
- پروفایل من
- تنظیمات تلفن همراه
- نسخه اپلیکیشن
- درباره ما
- راهنمای استفاده

مدیریت (admin only):
- تنظیمات سایت
- پیامک

### Native install policy
The normal website HTML contains `نصب PedalPro`, but 4.7.46 detects the native bridge and removes the install menu/prompt/page from the Native runtime. Therefore the independent Native app **must not expose `نصب PedalPro`**. A deep link to `#install` is redirected to the app page.

## Bottom navigation
Final 4.7.46 order:
- خانه
- فعالیت‌ها
- شروع رکاب
- مسیرها
- من

Mobile geometry:
- fixed full-width at bottom
- height `116px`
- padding `16px 10px 10px`
- radius `36px 36px 0 0`
- glass `linear-gradient(180deg,rgba(24,59,82,.40),rgba(8,31,49,.48))`
- center notch: top `-23px`, width `146px`, height `62px`
- normal icon `30px`
- normal label `11.5px`
- home tile width `76px`, radius `24px`, padding `8px 0 7px`
- center ride `86px × 86px`, margin-top `-38px`
- ride gradient `linear-gradient(145deg,#1deff4,#15d1ee 55%,#2bb2ff)`
- selected normal item uses cyan, not scale animation

## Public/member visibility
The three primary pages are not marked member-only in the canonical menu. Native must not force the login modal merely to render the public home/activities/routes pages. Account-required actions still invoke authentication exactly where the web does.

This contract changes only when the canonical web source changes.
