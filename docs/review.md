# Final independent review

Read-only review of the current checkout (no Git repository).
No critical findings in the concurrency architecture or test-schema isolation.

## Confirmed and repaired
1. Numeric JSON timestamps were accepted as epoch seconds by the default
   OffsetDateTime decoder. HTTP regression reproduced 201 instead of 400.
   Parse explicit ISO strings in the request DTO.
2. Extreme query dates reached PostgreSQL and returned 500. HTTP regression
   reproduced it. TimeInterval now bounds instants to UTC years 0001..9999.
3. JSON NUL in resource name/description/location reached PostgreSQL and returned
   500. HTTP regression reproduced it. Add validation to all resource text fields.
4. Exhausted Hikari pool produced SQLTransientConnectionException without a
   SQLSTATE, returning 500. Real HTTP test holding all connections reproduced it.
   Classify transient/recoverable connection exceptions as 503.

Regression verification and final suite results are recorded in implementation-progress.md.

## Outside MVP (reviewed exclusions)
Authentication/ownership, schedules, holds, notifications, deletion and rescheduling
are intentionally deferred. Public deployment is not supported by this unauthenticated
loopback MVP. Existing credentials are not published or changed.
No historical Git migration history exists; Liquibase integration and normal startup
against the user's working database are verified by the implementer.

## Minor deferred
The implementation ledger retains chronological decisions (including initial test DB
wording and a literal newline marker) to preserve the progression; the current approach
is documented in README and architecture.md.

