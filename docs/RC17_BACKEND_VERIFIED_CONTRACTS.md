# RC17 — Source-confirmed Backend integration

Verified PHP source from user-provided audit snapshot: website 4.7.46, root public_html/bike. No production PHP was edited.

Delivered on native/web-parity:
- Corrected group start: personal ride start_ride event_id=0, followed by event_group_start(event_id,ride_id). Group stop is an independent action with queue-drain barrier, and personal tracking resumes.
- Wired medal_image_upload multipart medal_id/photo PNG, corrected medal_save names metric_type and match_text. Existing medal image is not deleted on upload. No nonexistent clear-image endpoint used.
- Replaced guessed bootstrap Bale mapping with administrator-only GET bale_status, POST bale_save_settings, bale_connected_users search, nonce-idempotent bale_custom_send, GET bale_custom_history, plus confirmed test/webhook/run-queue actions with explicit confirmation. Token is write-only.
- Tracking transport uses the common native PedalProApiClient bootstrap for CSRF/session rotation, handles server ignored points, retries 419 multipart once, and does not evict old pending points silently.
- Previous RC16 GPS live-polyline and finish safeguards preserved. Source-backed CI contracts added.
- No speculative cycling route/profile support. Current Neshan routing is car-only. Dedicated cycling provider remains blocked until configured/tested in staging.
- Backend P0 GPS timestamp precision/idempotency, group late-partial credits, security method/CSRF hardening and production repairs remain server-side tasks. Their implementation needs staged migrations and API tests; audit alone does not authorize a live deployment.
- Signed APK build + source QA are not device acceptance. Validate group segment continuity, Bale admin permissions, medal MIME/CSRF, and live GPS using nonproduction records in staging first.
- PR #29 Draft, no main merge, no publication.
