# Google Calendar integration

Google Calendar is optional and disabled by default. Booking creation, confirmation and cancellation do not call Google or depend on Google availability. Each signed-in user connects their own Google account, and only bookings whose `created_by` equals that user's ID are synchronized to that account's primary calendar. Manager or administrator access does not export someone else's bookings.

## Configuration

Enable the Google Calendar API and create a Google OAuth web application client. Register the exact backend callback URI, for example `http://127.0.0.1:8080/api/v1/me/calendar/callback`. Configure the consent screen and permitted test users as appropriate for your Google project. The app requests `https://www.googleapis.com/auth/calendar.events.owned`, offline access and consent so that it receives a refresh token. Google consent-screen publication and testing restrictions are controlled by Google; no real credentials are needed for automated tests.

Set these Spring properties through the application configuration or corresponding environment variables:

| Property | Environment variable | Value |
| --- | --- | --- |
| `app.calendar.enabled` | `GOOGLE_CALENDAR_ENABLED` | `true`; defaults to `false` |
| `app.calendar.client-id` | `GOOGLE_CLIENT_ID` | Google web client ID |
| `app.calendar.client-secret` | `GOOGLE_CLIENT_SECRET` | Google client secret |
| `app.calendar.redirect-uri` | `GOOGLE_REDIRECT_URI` | Exact registered backend callback URI |
| `app.calendar.encryption-key` | `CALENDAR_ENCRYPTION_KEY` | Base64 encoding of 32 random bytes |
| `app.calendar.frontend-url` | `FRONTEND_URL` | Frontend origin; defaults to `http://127.0.0.1:5173` |
| `app.calendar.interval-ms` | `APP_CALENDAR_INTERVAL_MS` | Job scanner/worker interval; defaults to `5000` |

Keep credentials and the encryption key out of source control. Retain the encryption key across restarts. Replacing it makes existing encrypted tokens unreadable and requires users to reconnect. TLS and secure session-cookie deployment settings are also required when serving this beyond a local development environment. The worker also respects `app.workers.enabled=false`.

The authorization, token and event endpoint properties have official Google defaults. Tests override `app.calendar.authorization-endpoint`, `app.calendar.token-endpoint` and `app.calendar.events-endpoint` to use a loopback fake provider. These override properties are intended for tests, not arbitrary user-selected URLs.

## HTTP contract

All four endpoints require the existing authenticated application session. Ordinary application CSRF rules apply to the disconnect POST. OAuth state validates the callback independently.

- `GET /api/v1/me/calendar`: `{enabled, connected, reconnectRequired, status, remoteEventsRetained}`. `status` is `DISABLED`, `DISCONNECTED`, `CONNECTED` or `RECONNECT_REQUIRED`. No tokens, authorization codes or client secrets are exposed.
- `GET /api/v1/me/calendar/connect`: `{authorizationUrl}`. Navigate the browser to that URL, retaining the application session cookie.
- `GET /api/v1/me/calendar/callback?code=...&state=...`: exchanges the code and redirects with HTTP 303 to the configured frontend origin plus `/?calendar=connected`.
- `POST /api/v1/me/calendar/disconnect`: erases stored access and refresh tokens, invalidates pending OAuth states and cancels this user's jobs. Returns the status DTO.

State contains 32 unpredictable random bytes. Only a SHA-256 digest is stored. It expires after ten minutes, is bound to the initiating application user and session, and is consumed before token exchange, including when that exchange fails. Starting another authorization invalidates the earlier state. Callback state replay, another user's state and another session's state are rejected before provider calls. A concurrent disconnect invalidates an authorization attempt already exchanging its code, so it cannot restore credentials after disconnect returns.

Access and refresh tokens use AES-256-GCM with a random 96-bit nonce per encryption and the user ID as authenticated associated data. The database contains ciphertext only. The client refreshes expired tokens and retries a 401 once after refresh. `invalid_grant` or a persistent 401 sets `RECONNECT_REQUIRED` and clears credentials.

## Durable synchronization

Migration `009-calendar.sql` adds connections, OAuth-state digests, per-booking jobs and persistent event revisions. The scanner reconciles `CONFIRMED` and `CANCELLED` booking status; held and expired bookings are never exported. Initial confirmed exports are limited to bookings whose end is in the future. Existing future confirmed events are reconciled every six hours, which repairs remote edits or deletions. Cancellation jobs remove events even if the original booking time has passed.

Workers claim jobs using a PostgreSQL `FOR UPDATE SKIP LOCKED` lease, commit the claim before provider calls and recover expired leases. Attempts are bounded at six, including expired leases. Retryable network errors, 429, Google rate-limit 403 and 5xx failures use exponential backoff capped at one hour. Exhausted or permanent errors remain durable as `FAILED` with a sanitized reason; the scanner does not continuously reset them. Reconnecting starts a new connection generation and requeues eligible bookings. The job table can be inspected operationally without exposing credentials.

The Google event ID is derived from the user UUID and booking UUID using Google-compatible characters. Retries reuse that identity. A missing event (404) is inserted with the same ID; an insert conflict (409), including an earlier insert whose response was lost, updates that ID. A deleted-event tombstone (410) selects the next persistent event revision. The revision commits in a separate short transaction **before** the replacement create request, so a crash or lost response retries the same replacement instead of creating another event. Deletes returning 404 or 410 succeed.

Provider I/O runs exclusively in the calendar worker or OAuth callback, never in the booking transaction. A calendar connection row lock serializes token refresh, delivery and disconnect. The tombstone revision commit uses a separate pooled connection; configure a pool of at least two connections (the project default is ten). Requests have a five-second connect timeout and a ten-second request timeout. A disconnect can wait for an in-progress delivery and may need retry if the configured database lock timeout is reached.

Disconnect deliberately retains existing remote Google events and does not revoke the Google account's grant. It prevents subsequent exports, updates and cancellations by this app. Users can remove retained events and revoke the grant in Google separately. Reconnecting a different Google account leaves events in the former account untouched; the new account receives eligible bookings. `remoteEventsRetained: true` reports this behavior.

## Verification

`TokenCipherTest` checks randomized ciphertext, tamper detection, user binding and key length. `GoogleCalendarClientTest` uses a loopback HTTP server for exchange, refresh, invalid-grant, deterministic IDs, insert-conflict recovery, missing events, tombstones, deletion and retry classification. `CalendarIntegrationIT` uses the project's isolated PostgreSQL test schema and a loopback server for single-use session-bound states, ciphertext persistence, 401 refresh, durable bounded 429 retries, stable cancellation identities, tombstone replacement after a lost response, lease recovery and disconnect racing an OAuth callback.

Run the regular offline Maven unit-test command and the project's PostgreSQL integration-test verification with `DB_PASSWORD` configured. Tests never contact Google. Real consent and Google-side account permissions must be checked manually after configuring a real Google project.

References: [Google web-server OAuth](https://developers.google.com/identity/protocols/oauth2/web-server), [event insertion and custom event IDs](https://developers.google.com/workspace/calendar/api/v3/reference/events/insert), [Google Calendar errors](https://developers.google.com/workspace/calendar/api/guides/errors).

Callback initialization and disconnect also lock the stable application user row, preventing a first connection from being restored by an in-flight callback after disconnect.
