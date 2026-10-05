--liquibase formatted sql
--changeset mikey:008-waitlist
CREATE TABLE waitlist_entries (
 id uuid PRIMARY KEY, resource_id uuid NOT NULL REFERENCES resources(id),
 user_id uuid NOT NULL REFERENCES app_users(id), starts_at timestamptz NOT NULL, ends_at timestamptz NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(), status varchar(20) NOT NULL CHECK(status IN ('WAITING','OFFERED','FULFILLED','CANCELLED','EXPIRED')),
 offered_booking_id uuid REFERENCES bookings(id), CHECK(starts_at<ends_at)
);
CREATE UNIQUE INDEX waitlist_active_request ON waitlist_entries(user_id,resource_id,starts_at,ends_at) WHERE status IN ('WAITING','OFFERED');
CREATE INDEX waitlist_fifo ON waitlist_entries(resource_id,created_at,id) WHERE status='WAITING';
--changeset mikey:009-outbox
CREATE TABLE outbox_events (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), booking_id uuid NOT NULL REFERENCES bookings(id),
 user_id uuid NOT NULL REFERENCES app_users(id), status varchar(20) NOT NULL,
 payload jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 notified_at timestamptz, attempts integer NOT NULL DEFAULT 0,
 next_attempt_at timestamptz NOT NULL DEFAULT now(), last_error varchar(200)
);
CREATE INDEX outbox_pending ON outbox_events(next_attempt_at,created_at) WHERE notified_at IS NULL;
CREATE TABLE notifications (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES app_users(id),
 event_id uuid NOT NULL UNIQUE REFERENCES outbox_events(id), booking_id uuid NOT NULL,
 status varchar(20) NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), read_at timestamptz
);
CREATE INDEX notifications_user ON notifications(user_id,created_at DESC,id);
--changeset mikey:010-outbox-trigger splitStatements:false
CREATE FUNCTION reservation_booking_event() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='INSERT' OR NEW.status IS DISTINCT FROM OLD.status THEN
  INSERT INTO outbox_events(booking_id,user_id,status,payload)
   VALUES(NEW.id,NEW.created_by,NEW.status,jsonb_build_object('version',1,'resourceId',NEW.resource_id,'startsAt',NEW.starts_at,'endsAt',NEW.ends_at,'status',NEW.status));
 END IF;
 RETURN NEW;
END
$$;
CREATE TRIGGER reservation_booking_event AFTER INSERT OR UPDATE ON bookings FOR EACH ROW EXECUTE FUNCTION reservation_booking_event();

