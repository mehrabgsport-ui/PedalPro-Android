# PedalPro 4.7.46 Web UI Inventory

Source: `PedalPro_RestorePoint_2026-10-13_Pre_Native_2.0.0.ppup/index.php`.
Purpose: implementation checklist for native/web-parity. This file records source-observed UI only; it is not a redesign specification.

## Global shell
- RTL document, Persian UI.
- Website background image + dark overlay.
- Vazirmatn is the intended current UI family where reference-skin selectors inherit the page font.
- Sticky mobile glass header.
- Right-side mobile drawer and dim overlay.
- Main scroll area.
- Fixed bottom navigation with center ride control.
- Global toast/message system.
- Connection/server-status pill in sidebar.
- Auth modal and admin/editor modals.
- Safe-area handling on top/bottom mobile chrome.
- Light-theme code exists on web and must be mirrored only where the current website exposes it.

## Page registry observed in current source
Included page files:
- `dashboard` — `pages/dashboard.php`
- `activities` — `pages/activities.php`
- `routes` — `pages/routes.php`
- `analysis` — `pages/analysis.php`
- `challenges` — `pages/challenges.php`
- `about` — `pages/about.php`
- `gallery` — `pages/gallery.php`
- `members` — `pages/members.php`
- `profile` — `pages/profile.php`
- `bikes` — `pages/bikes.php`
- `fitness` — `pages/fitness.php`
- `ai` — `pages/ai.php`
- `chat` — `pages/chat.php`
- `app` — `pages/app.php`
- `sms` — `pages/sms.php`

Hard-coded/current source pages:
- `phone-settings`
- `install` (website/PWA only; removed in Native runtime by 4.7.46 policy)
- `guide`
- `pro-features` internal destination hub
- `sos-public`
- `beacon-public`
- admin `settings` shell and dynamically injected settings panes

Feature tabs/routes exposed through the Pro hub/current information architecture:
- `social`
- `recommend`
- `globalheat`
- `flyover`
- `discover`
- `safety`
- `training`
- `privacy`
- `replay`
- `clubpro`
- `analysis`
- `segments`
- `heatmap`
- `groupnav`
- `beacon`
- `offline`
- `maintenance`
- `sensors`

## Mobile header
Elements:
1. PedalPro logo.
2. Login/account control.
3. Admin site-settings control, admin only.
4. Hamburger toggle.
5. Dynamic top gap from `DATA.ui.mobile_header_top_gap_px`.

States:
- guest: login control.
- signed in: account/user control.
- admin: site settings visible.
- drawer open: page dimmed/locked.
- responsive mobile glass styling.

## Mobile drawer
Primary items:
- صفحه اصلی
- فعالیت‌ها و برنامه‌ها
- مسیرهای GPS

Expandable groups:
- رکاب‌زنی (open initially)
- مسیرها و اکتشاف
- باشگاه
- ارتباطات
- عملکرد من
- ایمنی و حریم خصوصی
- حساب من
- مدیریت (admin only)

Visibility:
- account/member-only destinations follow current web auth rules.
- management group only when `DATA.is_admin`.
- legacy flat `pro-features` button is hidden.
- Native removes install item.
- side-bottom decorative block is hidden on mobile.

Interaction:
- details/summary expand-collapse.
- active destination is cyan.
- drawer closes when destination is selected.
- page history/hash is replaced by web router behavior.
- backdrop closes drawer.

## Bottom navigation
Slots:
1. خانه
2. فعالیت‌ها
3. شروع رکاب / dynamic `DATA.ui.ride_button_text`
4. مسیرها
5. من

States:
- normal
- active cyan
- center ride elevated with dedicated notch/ring
- text remains RTL inside LTR five-column dock
- fixed to bottom and safe-area aware

## Dashboard / home
Source-observed components/classes:
- hero `.hero.home-hero`
- glass copy panel `.home-hero-copy`
- `.home-eyebrow`
- brand word block
- `.home-tagline`
- CTA stack
- primary CTA
- secondary routes CTA
- quick-card grid
- upcoming-program card
- report block
- live ride affordance when data exists

Dynamic text:
- `home_eyebrow`
- `home_tagline`
- `home_primary_button_text`
- `home_secondary_button_text`

Mobile 4.7.38 fix:
- hero height becomes content-driven
- outer hero padding `10px 8px 12px`
- copy width 100%
- copy padding `17px 15px 15px`
- no transform/forced vertical offset

Home quick card final style:
- transparent glass gradient
- border `rgba(255,255,255,.21)`
- mobile min-height `150px`
- mobile radius `26px`
- cyan vector icons

Home report:
- 3 columns desktop
- 1 column mobile
- separators become horizontal on mobile
- glass card styling

## Activities / programs
Observed/current functional areas:
- activities and programs listing
- event/program cards
- registration state
- admin event creation/editing
- event cover/photo flow
- event SMS automation/reminders
- activity feed
- completed rides/activity history
- event detail/action controls
- registration/unregistration behavior
- empty state
- loading/refresh state
- error/toast behavior

Admin/event modal:
- `eventModal`
- cover/photo preview
- date/time fields and Jalali normalization
- location fields/map helper where present

## Routes
Observed/current functional areas:
- routes listing
- route/ride cards
- route maps
- ride details
- live rides
- group rider navigation
- route recommendation
- segments
- personal/club/global heatmaps
- offline routes
- flyover
- route/privacy controls

Map/GPS restriction:
- Native parity work must not replace or modify tracking/map algorithms without explicit user instruction.
- Presentation and navigation can be matched around the existing GPS/map implementation.

## Ride / active tracking
States:
- no active ride / GPS preflight gate
- location permission pending/denied
- GPS searching
- GPS ready with accuracy
- ride active
- paused
- resumed
- finish confirmation
- server/API failure
- offline/network failure
- low-power mode
- background tracking status

Observed native bridge/web behaviors retained as parity reference:
- Native location/status callbacks update active ride and GPS indicator.
- Ride mode receives latitude/longitude/altitude/accuracy/speed/heading.
- active ride is merged from API response.
- Android tracking status/error is surfaced in UI.

Known ride-related overlays/modals in source:
- GPS help modal `gpsHelpModal459`
- navigation chooser `ppNavChooser461`
- ride privacy modal `ridePrivacyModal464`
- privacy overlay `ppRidePrivacy4630`
- target navigation HUD `ppTargetNavHud4630`
- group navigation active state
- related map/HUD overlays

## Low-power ride
Must remain a dedicated Native screen while matching the web/app reference behavior:
- black OLED surface
- time
- speed km/h
- distance km
- pause/resume state
- back control
- no extra location icon/lines
- exact typography/layout from current accepted reference
- tracking source remains existing native tracking engine

## Challenges
Observed source states/features:
- challenge cards
- selected challenge state
- join/leave state
- official marker where data exposes it
- participants/member chips
- current-user member chip state
- extra metadata badges
- my-challenges section
- medals/catalog UI added in current source
- empty state
- API failure/toast state

## Club / members / gallery
- members grid/list
- weekly ranking
- discovery/programs destination
- gallery grid
- gallery image cards and labels
- club professional summary
- current membership/account visibility rules

## Social / communication
- chat room
- group chat history
- unread/presence polling
- social feed
- Follow
- Kudos
- AI advisor
- loading, empty and API-error states

## Performance / health / bike
- statistics and analysis
- Training Load / Fitness
- activity replay
- elevation/weather
- body/fitness status
- bikes
- service/maintenance
- maintenance create/update/complete states
- performance reports
- current cycling-sensor views

## Safety / privacy
- SOS and emergency contacts
- Beacon
- Privacy Zones
- public SOS/Beacon pages where web permits public access
- permission/network/error states

## Phone settings page
Exact visible section:
- title: `تنظیمات تلفن همراه`
- explanatory copy
- detected-device box
- `باز کردن تنظیمات باتری`
- `تنظیمات اجرای خودکار`
- `تنظیمات PedalPro`
- notice regarding Sleeping / Deep Sleeping / Battery Saver / Background Restriction / Precise Location

## Guide page
- title: `راهنمای کامل استفاده از PedalPro`
- subtitle: `راهنمای وب و اپلیکیشن Android، از شروع رکاب تا تحلیل عملکرد و ایمنی`
- `دانلود راهنمای PDF`
- guide cards:
  - شروع کار
  - رکاب‌زنی
  - مسیرها
  - عملکرد
  - باشگاه و ارتباطات
  - ایمنی
  - تنظیمات گوشی
  - رفع اشکال

## App/version page
- current app/release information
- store-channel behavior
- Bazaar flavor follows Bazaar update path
- Myket flavor follows Myket update path
- no direct website APK install UI in store flavors where 4.7.46 policy separates store delivery

## About
- exact page content from current web include must be mirrored without added Native marketing text.

## Profile
- profile data
- avatar
- verified phone status
- profile editor
- phone/verification workflows
- user account actions
- current web sub-tabs where present
- loading/error/validation states

## Bikes
- bike list/cards
- photo/thumbnail
- add/edit fields
- delete/maintenance actions where web exposes them
- empty state

## Fitness
- summary cards
- charts
- history
- body metrics
- current responsive single-column mobile structure

## AI advisor
- chat transcript
- input composer
- send/loading/failure states
- keyboard behavior
- scroll-to-latest behavior
- exact web labels/messages

## Chat room
- transcript
- sender/current-user variants
- presence/unread
- composer
- loading, empty and retry states
- polling behavior

## Admin settings
Baseline settings shell:
- horizontally scrollable settings tabs on mobile
- one active settings pane at a time
- cards and forms use final reference skin

Observed API-backed areas:
- general site settings: `site_settings_save`
- appearance/native shared UI: `ui_settings_save`
- medals: `medals_recalculate`, `medal_save`, `medal_delete`
- users: `admin_user_save`
- roles: `admin_role_save`
- SMS/FarazSMS:
  - `sms_status`
  - `sms_otp_patterns`
  - `sms_test_connection`
  - `sms_save_sender`
  - `sms_fetch_lines`
  - `sms_save_otp_patterns`
  - `sms_send_custom`
  - `sms_send_pattern`
  - `sms_send_bulk`
  - `sms_logs`
- application release manager:
  - `app_release_status`
  - `app_release_upload`
  - `app_release_rollback`
- push/FCM:
  - `fcm_status`
  - `fcm_config_save`
  - `fcm_test`
  - `app_notification_send`

Dynamically injected appearance pane:
- title `ظاهر سایت و اپ`
- top mobile gap
- home eyebrow
- home subtitle
- main button label
- routes button label
- ride button label
- save button `ذخیره ظاهر سایت و اپ`

## SMS admin
Must mirror:
- status
- connection test
- sender/line selection
- OTP/event pattern IDs
- custom message send
- pattern send
- bulk send
- logs/history/status
- event registration/reminder pattern validation where current source exposes it

## App release admin
- current release status
- upload
- rollback
- store policy/channel fields exactly as web exposes
- errors/progress/results

## Push admin
- FCM status/config
- test push
- application notification send
- validation and success/error states

## Modal inventory observed by ID/source
- `authModal`
- `adminUserModal`
- `eventModal`
- `challengeModal`
- `groupEventModal439`
- `gpsHelpModal459`
- `ppNavChooser461`
- `ridePrivacyModal464`
- `ppRidePrivacy4630`
- other dynamically-created feature modals must be inventoried from their source block before implementing that screen.

## Shared interaction/state inventory
For every page/control:
- default
- active
- pressed/tap
- disabled
- loading
- empty
- API error
- offline/network error
- permission denied where applicable
- validation error
- success/toast
- modal open/close
- expand/collapse
- refresh
- scroll
- keyboard
- Android Back
- page/history state
- admin/member/guest visibility

## Visual acceptance checklist
A screen is not complete until:
- no missing web element
- no Native-only extra element
- exact labels/order
- source-derived dimensions/spacing where available
- source-derived typography/colors/radii/borders/gradients
- source-derived responsive behavior
- source-derived fixed/sticky/scroll behavior
- source-derived interactive states
- matching navigation/back/modal behavior
- matching API semantics
- screenshot comparison at target mobile widths

## Explicit non-parity items to remove from previous Native RC presentation
- green primary styling where current web uses cyan reference primary
- Native-only glossy/rounded shells not present in web
- selected bottom-nav scale animation
- forced login before public home/activities/routes
- visible `نصب PedalPro` in Native menu
- mobile drawer footer text hidden by current web
- guessed/renamed menu labels
