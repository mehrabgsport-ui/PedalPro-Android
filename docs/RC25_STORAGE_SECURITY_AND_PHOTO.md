# RC25 encrypted session and upload optimization
- Encrypted per-installation native cookie/CSRF/user_json using Android Keystore AES-GCM, with atomic per-field legacy migration and fail-closed key errors.
- TrackingService reuses secure native cookie and CSRF for GPS uploads without modifying accepted points or pending route queues.
- Native multipart photo uploads automatically convert to bounded JPEG target ~500 KiB, hard limit less than 1 MiB, preserving regular EXIF rotation. Original photo files are untouched.
- Medals' transparent PNG art remains excluded from JPEG conversion.
- Physical-device migration, photo upload, GPS background, staging PHP response validation and DB authorization remain unverified. This is a release candidate, not production acceptance.
