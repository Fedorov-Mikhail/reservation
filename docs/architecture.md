# Reservation MVP

## Scope
Resources and their descriptive metadata, bookings, cancellation, availability,
validation, Problem Details, request correlation, PostgreSQL migrations and tests.
User accounts, authorization, schedules, holds, calendars and notifications are
future work. The unauthenticated MVP binds to 127.0.0.1.

## Domain and time
One booking occupies one whole resource. No resource deletion or disabling yet.
Resource names are not unique. PUT replaces descriptive fields.
Booking states: CONFIRMED -> CANCELLED, terminal. No reschedule operation.
Cancellation is allowed strictly before startsAt. Repeated cancellation is a no-op,
even after the original start time, and preserves cancelledAt.

JSON accepts ISO 8601 timestamps with an explicit offset. Domain uses Instant;
PostgreSQL uses timestamptz; responses use UTC. Precision is at most microseconds.
All intervals are [start,end), so adjacent intervals do not overlap.
Creation validates start >= current Clock time, 1 minute <= duration <=24 hours,
and end <= now+365 days. Limits are configuration properties.
Availability returns maximal free intervals; minDurationMinutes filters gaps,
not a list of all start times. Its window is <=31 days. Historical availability
is allowed for inspection; creation still rejects past time.
Listing filters use interval intersection and stable startsAt,id ordering.

## Transaction boundaries and concurrent creation
Controller -> nontransactional BookingApplicationService ->
transactional BookingTransactionalService -> JPA/PostgreSQL.

The transaction is READ COMMITTED. It locks the resource row FOR UPDATE,
validates time after acquiring that lock, inserts and flushes the booking,
then commits. Only after the transactional proxy returns is success logged
and HTTP 201 sent. Writes on the same resource serialize; different resources
remain independent. No HTTP/external calls happen inside transactions.

The authoritative invariant is bookings_no_confirmed_overlap:
EXCLUDE USING gist(resource_id WITH =,
tstzrange(starts_at,ends_at,'[)') WITH &&) WHERE status='CONFIRMED'.
btree_gist lives in public. The constraint protects direct SQL writers too.
This uses a PostgreSQL-specific migration, while Java maps ordinary timestamps.

**Evidence-driven adjustment to the initial design:** exclusion alone prevented
bad data but the eight-request HTTP stress test observed PostgreSQL 40P01
deadlocks and 503 responses. The resource row lock removed those application
write races. Keeping both mechanisms provides predictable MVP API behavior and
a database backstop. Disjoint writes to the same resource serialize as a cost.

Errors are translated outside the transaction, after rollback, including commit
failures. Only SQLSTATE 23P01 AND the exact constraint name become BOOKING_OVERLAP.
Lock/statement timeouts and availability failures become 503, not fake conflicts.
There are no automatic retries of unknown transaction outcomes.

Cancellation locks the booking row, checks state/time, updates status and commits.
It does not acquire a resource lock (avoids reversing the creation lock order).
A racing creation may conflict or succeed depending on commit order; never two
confirmed overlapping bookings. A rolled-back cancellation does not free time.

## Queries
Availability uses a single occupied-bookings query to obtain a consistent statement
snapshot, clips intervals, merges occupied spans, returns gaps of sufficient length.
It is advisory only and does not reserve time. No cache or queues in the MVP.
Pagination maximum is 100; API never exposes JPA entities.

## Database evolution
Liquibase owns DDL; Hibernate only validates. Never edit already applied changesets.
Integration tests apply the same migrations to a unique reservation_it_* schema
inside the selected DB and remove only that schema when the test JVM exits.
The shared btree_gist extension remains installed in public.

## Verification
- Unit tests: interval boundaries, duration/horizon, cancellation, free-gap calculation.
- HTTP integration: real application, random port, fixed Clock, real PostgreSQL.
- Eight simultaneous HTTP requests in 20 rounds: one 201, seven 409, one DB row.
- Different overlapping intervals, separate groups, adjacent intervals, different resources.
- Cancellation/create and multiple cancellations; validation, pagination, filters.
- Two independent JDBC transactions: verify pg_blocking_pids before commit/rollback.
- Direct SQL cannot bypass exclusion, CHECKs or foreign keys.
- Self-join verifies no confirmed overlapping pairs.

## Future evolution
Users: ownership, roles, authorization. Schedules: resource ZoneId plus local rules,
with a concurrency protocol for changing schedules while creating bookings.
Holds: HELD/CONFIRMED participate in exclusion; explicit EXPIRED transition, never
an index predicate based on now(). Confirmation/expiration lock the same booking.
Idempotency keys: transactionally store request fingerprint and result.
Notifications: transactional outbox, then workers and a broker when justified.
Organizations: ownership and cross-tenant foreign-key protection.
Cache: read optimization only; writes always pass PostgreSQL constraints.
Partitioning requires re-evaluating cross-partition exclusion guarantees.


Review hardening: all interval instants must be within UTC years 0001..9999. Booking JSON timestamps are parsed ISO strings, not numeric epochs. Text fields reject NUL. Pool exhaustion without SQLSTATE maps to 503.
