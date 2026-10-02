# Reservation MVP implementation ledger

Approved design: technical plan in this conversation, 2026-10-02.
Scope: resource API, bookings, cancellation, availability, PostgreSQL exclusion
constraint, validation, errors, logging, migrations and integration tests.

Ruling: implement in the existing directory as requested; no Git repository exists.
Ruling: preserve IDE files and existing database credentials; use an isolated test DB.
Ruling: future users, holds, messaging and calendars are out of this MVP.

Tasks:
1. Build and failing tests.
2. Migrations and database invariant.
3. Resources and transactional bookings.
4. Cancellation, availability, errors and logging.
5. Concurrent integration tests, documentation and independent review.

Pre-flight: API timestamps -> OffsetDateTime -> Instant -> timestamptz;
all intervals [start,end), all active bookings CONFIRMED; 23P01 maps to 409
only for bookings_no_confirmed_overlap. Cancellation is terminal and idempotent.
Evidence: generated application and empty changelog before changes.
\nRuling: user explicitly requested the working PostgreSQL database. Integration tests use a randomly named temporary schema in that same DB, with cleanup. No Docker dependency; no test records in public.

RED: initial API lifecycle returned 401 before implementation; then GREEN.
RED: 8-way HTTP race produced SQLSTATE 40P01 and 503 responses, while exclusion
constraint still protected database integrity.
Ruling: serialize creation using a PESSIMISTIC_WRITE lock on the resource row.
The database exclusion constraint remains the authoritative backstop, including
for direct SQL. Cost: disjoint writes to one resource serialize, acceptable for MVP.
Ruling: use installed Surefire with a separate integration-test phase execution
instead of Failsafe; preserves test vs verify separation without another download.

GREEN: lifecycle integration passed after API and migrations.
GREEN: full offline Maven verify passed, including 20 PostgreSQL integration tests.
The HTTP race has exactly 1 success and 7 conflicts in each of 20 rounds.
Four controlled JDBC races observed pg_blocking_pids before commit/rollback.
Config includes loopback binding, timeouts, UTC, requestId and no stack traces.
Docs: README.md, docs/architecture.md, scripts/smoke.ps1.
Review focus: SQL constraint bypass, rollback/cancel races, out-of-range inputs,
temporary-schema isolation, malformed timestamps/JSON, actual test execution.

Review RED: extreme query dates and NUL text returned 500; numeric epochs created
bookings (201); real pool exhaustion returned 500 rather than 503.
Fix: explicit ISO parsing, supported timestamp bounds, no-NUL validation,
and transient/recoverable JDBC connection classification. See docs/review.md.
Manual packaged-app check on working DB passed: health UP, creation,409,availability,
cancellation and repeated cancellation. One demo resource/cancelled booking remains.

Final verification: mvnw -B -o -ntp clean verify -> BUILD SUCCESS (2026-10-02).
41 tests: 16 unit, 25 integration, zero failures/errors/skips.
Review fixes all GREEN, including actual connection-pool exhaustion and lock timeout.
Final packaged JAR started against reservation/public, health UP and resource API200.
Verification process stopped; user's IDEA process was not stopped or reconfigured.
Database readback: see final verification output. Full build log: target/final-verification.log.
All five implementation tasks complete. Future features remain explicitly out of MVP.
No Git repository exists, so branch integration/commit/PR steps do not apply.
