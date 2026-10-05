--liquibase formatted sql
--changeset mikey:007-holds
ALTER TABLE bookings ADD COLUMN expires_at timestamptz;
ALTER TABLE bookings ADD COLUMN confirmed_at timestamptz;
UPDATE bookings SET confirmed_at=created_at WHERE status='CONFIRMED';
ALTER TABLE bookings DROP CONSTRAINT bookings_valid_status;
ALTER TABLE bookings DROP CONSTRAINT bookings_valid_cancellation;
ALTER TABLE bookings ADD CONSTRAINT bookings_valid_status CHECK(status IN ('HELD','CONFIRMED','CANCELLED','EXPIRED'));
ALTER TABLE bookings ADD CONSTRAINT bookings_valid_cancellation CHECK((status='CANCELLED')=(cancelled_at IS NOT NULL));
ALTER TABLE bookings ADD CONSTRAINT bookings_hold_expiry CHECK(status NOT IN ('HELD','EXPIRED') OR expires_at IS NOT NULL);
ALTER TABLE bookings DROP CONSTRAINT bookings_no_confirmed_overlap;
ALTER TABLE bookings ADD CONSTRAINT bookings_no_confirmed_overlap EXCLUDE USING gist(resource_id WITH =,tstzrange(starts_at,ends_at,'[)') WITH &&) WHERE(status IN ('HELD','CONFIRMED'));
CREATE INDEX bookings_expiry_idx ON bookings(expires_at) WHERE status='HELD';
CREATE TABLE booking_requests(user_id uuid NOT NULL REFERENCES app_users(id),request_key varchar(100) NOT NULL,fingerprint text NOT NULL,booking_id uuid NOT NULL REFERENCES bookings(id),PRIMARY KEY(user_id,request_key));

