# PedalPro RC41: P0 interval-credit ledger — pre-integration

Branch: `native/web-parity`; PR #29 remains Draft. Android release unchanged: `2.0.0-native-rc40`, versionCode 84. No changes to `main`, Production, Staging or marketplaces in this work.

## Locally produced deliverable (not committed to this PUBLIC Android repository)

Private/offline package: `PedalPro_RC41_P0_Ledger_Staging_PreIntegration.zip`
SHA256: `39d42c08d5d849b3d2b07b888308e043c14bff0659d24d3944a4fc1f471a2625`

- `lib/GroupCreditCalculator.php`: pure, deterministic interval attribution from valid group GPS steps, first verified join, optional exit and already credited personal intervals; skip implausible steps; fail closed when server authoritative distance and reliable GPS differ >25%.
- `lib/GroupCreditLedgerRepository.php`: transactional, immutable **proposal-only** record with MySQL unique constraint and mismatched retry guard. Does NOT award points, alter scores or medals.
- `sql/migration_staging.sql`: additive InnoDB table **explicitly schema-qualified** to `apricit1_Staging`. Does not modify existing tables.
- CLI audit/probe, migration and release-gate instructions, security checklist and independently runnable PHP tests.

## Verified locally (PHP 8.4)

- 33 unit assertions for partial join, exit, invalid GPS, personal-ride overlap, legacy missing join and deterministic replay.
- 7 contract checks against FAKE PDO (not MySQL).
- 243 deterministic invariants for monotonic join time, overlap exclusion and duplicate GPS steps.
- PHP lint checks all files; CLI fixture gives 200m credit out of 400m; ZIP archive validated.

## Remaining release blockers (NOT verified)

1. Existing `PedalPro_RC40_Staging_Group_Credit.zip` and exact Staging `api.php` / `pp_rc40_group_credit.php` are absent here. Do not guess integration into the private Backend.
2. Real staging MySQL schema compatibility, staging backups and integration tests are not yet run; this is **not** a deployable replacement for RC40 API.
3. Overlapping **other group events**, personal GPS points still pending offline, historical attendance timestamps, UTC conversion, server-side RBAC/CSRF, old score ledger reconciliation and medal posting require schema-aware transactions and cross-account tests.
4. `event_distance_same_ride` legacy heuristic is not yet replaced in the live application. The standalone package must not be presented as full double-count prevention.
5. Do not deploy to Production or publish any new store version without owner approval.

Next integration: obtain exact RC40 private Staging archive securely, inspect live schema and permissions, back up Staging, then connect and test the new engine in isolation before touching legacy totals.
