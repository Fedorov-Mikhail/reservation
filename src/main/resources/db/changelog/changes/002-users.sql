--liquibase formatted sql
--changeset mikey:005-users
CREATE TABLE app_users (
 id uuid PRIMARY KEY, username varchar(64) NOT NULL UNIQUE,
 password_hash varchar(100) NOT NULL, role varchar(20) NOT NULL CHECK(role IN ('USER','ADMIN')),
 enabled boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO app_users(id,username,password_hash,role,enabled)
 VALUES ('00000000-0000-0000-0000-000000000001','legacy-system','disabled','USER',false);
ALTER TABLE bookings ADD COLUMN created_by uuid NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001' REFERENCES app_users(id);
ALTER TABLE bookings ADD COLUMN cancelled_by uuid REFERENCES app_users(id);
ALTER TABLE bookings ADD COLUMN cancellation_reason varchar(500);
ALTER TABLE resources ADD COLUMN created_by uuid NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001' REFERENCES app_users(id);
CREATE TABLE resource_managers (
 resource_id uuid NOT NULL REFERENCES resources(id), user_id uuid NOT NULL REFERENCES app_users(id),
 PRIMARY KEY(resource_id,user_id)
);
CREATE INDEX bookings_owner_idx ON bookings(created_by,starts_at,id);

