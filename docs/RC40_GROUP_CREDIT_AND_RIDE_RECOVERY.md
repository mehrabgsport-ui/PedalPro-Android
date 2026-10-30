# PedalPro RC40 development handoff — Android 2.0.0-native-rc40, code 84

**Working branch:** `native/web-parity`, PR #29 Draft.  
**Production:** RC39 v83 submitted to Bazaar; RC40 **NOT** submitted/published/merged.

## Completed client changes
1. NativeMainActivity: keep the completed-event, actual attendance time picker wired **after cancellation or a failed retry**. The existing `attended_at` field is sent; a one-click retry cannot silently claim full leader distance.
2. NativeMainActivity: `ride_delete` is now authenticated/server-confirmed BEFORE `TrackingService.ACTION_DISCARD`. Failed server deletion no longer immediately kills an active GPS recorder. Success explicitly clears the deleted ride's local pending GPS queue. Post-deletion bootstrap failure is nonfatal.
3. Code 84, RC40 store QA metadata and source-regression guards for both cases.

## RC40 staging backend bundle

A standalone Staging deployment ZIP `PedalPro_RC40_Staging_Group_Credit.zip` was prepared from the previously used RC38 Staging PHP API and contains:
- `api.php` plus `pp_rc38_core.php` and `pp_rc40_group_credit.php`
- `migration_staging.sql`: `pp_event_participation(event_id,user_id,joined_at,checkin_kind)`
- auditable `api_rc40_changes.patch`
- `tests/test_group_credit.php`, 8 isolated passing unit checks
- `README_FA.md`

**Not deployed to any server**; do not apply to production. The exact new PHP module and Staging patch are delivered in the project chat ZIP. All Staging changes require SQL backup and permissioned deployment.

### Group rules implemented in Staging code
- Check-in before start => prestart/full segment possible.
- Admin attendance on active event => join from recorded server time.
- `event_late_add` now has a server admin-only handler and immutable first check-in.
- Checking someone in after event completion requires actual `attended_at` within the group segment.
- Final credit uses reliable GPS step-distance ratio from recorded join time to segment end, multiplying the authoritative segment total; skipped disconnected GPS gaps and implausible teleport jumps.
- Previously completed events with **no join metadata** are not backdated or silently rewritten.
- The existing coarse personal-vs-group overlap heuristic is still pending a complete interval ledger (don't claim full double-count protection).

### Validation not yet performed
- Real Staging MySQL integration: sample users, at least one midpoint join, duplicate join retry, no-ACL access, attendance off, event completion and account/medal credit.
- GPS foreground/offline queue, Neshan map, group privacy with more than one account on a physical Android phone.
- Performance/power measurements and final marketplace signing identity/production service compatibility.

### Follow-on priority
1. Staging deploy/test RC40 group credit, then promote only with explicit backup/test authorization.
2. Replace the existing coarse binary `event_distance_same_ride` heuristic with member-specific tracking intervals and an auditable points ledger.
3. Restore point delivery robustly across offline/restart; keep server point IDs/idempotency and timestamps consistent.
4. Audit live GPS privacy, group ACL, server authentication and Bale messaging before production.
5. Recheck UI/UX and GPS accuracy with actual devices and accounts.

**Release rule:** do not publish RC40, alter production PHP/DB or merge PR #29 automatically.
