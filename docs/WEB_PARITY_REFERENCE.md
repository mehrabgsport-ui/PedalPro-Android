# PedalPro Web Parity Contract

Canonical web reference: PedalPro website restore point **4.7.46** (`PedalPro_RestorePoint_2026-10-13_Pre_Native_2.0.0.ppup`).
Android target branch: `native/android-2.0.0`.

## Hard rules
- Native Android only. No WebView.
- Website UI/UX, labels, grouping, ordering, content hierarchy, admin visibility and button behavior are the source of truth.
- Do not redesign, rename, regroup or invent navigation.
- Do not change GPS/map logic as part of UI parity work.
- `main` stays untouched until final approval.

## Web theme
- bg: #06111e
- panel: #0d1c2c
- panel2: #102338
- line: #21364d
- text: #f5f7fb
- muted: #9fb2c8
- green: #2ee6a6
- blue: #2b92ff
- pink: #ff557e
- orange: #ff7c46
- purple: #8d63ff
- yellow: #ffd94a
- red: #ff5c6c

## Mobile header
1. PedalPro logo
2. account/login action
3. admin site-settings action (admin only)
4. menu toggle

## Mobile navigation (4.7.46)
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
- نصب PedalPro

مدیریت (admin only):
- تنظیمات سایت
- پیامک

## Bottom navigation
- خانه
- فعالیت‌ها
- شروع رکاب
- مسیرها
- من

This contract is used for parity review and must be updated only when the website itself changes.
