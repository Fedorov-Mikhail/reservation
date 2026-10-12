--liquibase formatted sql
--changeset mikey:018-notification-delivery
CREATE TABLE notification_preferences (
 user_id uuid PRIMARY KEY REFERENCES app_users(id), email varchar(254) NOT NULL DEFAULT '',
 email_enabled boolean NOT NULL DEFAULT false,
 reminder_minutes integer NOT NULL DEFAULT 30 CHECK(reminder_minutes BETWEEN 1 AND 10080),
 CHECK(NOT email_enabled OR length(btrim(email))>0)
);
ALTER TABLE notifications ADD COLUMN resource_name varchar(120);
ALTER TABLE notifications ADD COLUMN starts_at timestamptz;
ALTER TABLE notifications ADD COLUMN ends_at timestamptz;
UPDATE notifications n SET resource_name=r.name,starts_at=b.starts_at,ends_at=b.ends_at
 FROM bookings b JOIN resources r ON r.id=b.resource_id WHERE b.id=n.booking_id;
CREATE TABLE email_deliveries (
 id uuid PRIMARY KEY, dedup_key varchar(100) NOT NULL UNIQUE,
 user_id uuid NOT NULL REFERENCES app_users(id), booking_id uuid NOT NULL REFERENCES bookings(id),
 kind varchar(20) NOT NULL CHECK(kind IN ('STATUS','REMINDER')), booking_status varchar(20) NOT NULL,
 resource_name varchar(120) NOT NULL, starts_at timestamptz NOT NULL, ends_at timestamptz NOT NULL,
 state varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','PROCESSING','SENT','FAILED','SKIPPED')),
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0), available_at timestamptz NOT NULL DEFAULT now(),
 lease_until timestamptz, lease_token uuid, last_error varchar(40),
 created_at timestamptz NOT NULL DEFAULT now(), sent_at timestamptz
);
CREATE INDEX email_deliveries_pending ON email_deliveries(available_at,created_at) WHERE state IN ('PENDING','PROCESSING');
