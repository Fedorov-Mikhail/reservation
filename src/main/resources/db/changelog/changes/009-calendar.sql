--liquibase formatted sql
--changeset mikey:009-calendar
CREATE TABLE calendar_connections (
 user_id uuid PRIMARY KEY REFERENCES app_users(id), status varchar(30) NOT NULL,
 access_token text, refresh_token text, expires_at timestamptz,
 generation uuid NOT NULL, updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK(status IN ('CONNECTED','DISCONNECTED','RECONNECT_REQUIRED'))
);
CREATE TABLE calendar_oauth_states (
 state_hash varchar(64) PRIMARY KEY,user_id uuid NOT NULL REFERENCES app_users(id),session_hash varchar(64) NOT NULL,
 expires_at timestamptz NOT NULL
);
CREATE INDEX calendar_oauth_expiry_idx ON calendar_oauth_states(expires_at);
CREATE TABLE calendar_sync_jobs (
 booking_id uuid PRIMARY KEY REFERENCES bookings(id),user_id uuid NOT NULL REFERENCES app_users(id),
 generation uuid NOT NULL,desired_status varchar(20) NOT NULL CHECK(desired_status IN ('CONFIRMED','CANCELLED')),
 state varchar(20) NOT NULL CHECK(state IN ('PENDING','LEASED','DONE','FAILED','CANCELLED')),
 attempts integer NOT NULL DEFAULT 0, next_attempt_at timestamptz NOT NULL DEFAULT now(),
 lease_token uuid,lease_until timestamptz,last_error varchar(64),updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX calendar_sync_due_idx ON calendar_sync_jobs(next_attempt_at) WHERE state IN ('PENDING','LEASED');

--changeset mikey:009-calendar-event-mappings
CREATE TABLE calendar_event_mappings (
 booking_id uuid PRIMARY KEY REFERENCES bookings(id),
 event_revision integer NOT NULL DEFAULT 0 CHECK(event_revision>=0)
);
