--liquibase formatted sql

--changeset mikey:001-extension
CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;

--changeset mikey:002-resources
CREATE TABLE resources (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL,
    description varchar(2000),
    location varchar(255),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT resources_name_not_blank CHECK (length(btrim(name)) > 0)
);

--changeset mikey:003-bookings
CREATE TABLE bookings (
    id uuid PRIMARY KEY,
    resource_id uuid NOT NULL REFERENCES resources(id) ON DELETE RESTRICT,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    cancelled_at timestamptz,
    CONSTRAINT bookings_valid_interval CHECK (isfinite(starts_at) AND isfinite(ends_at) AND starts_at < ends_at),
    CONSTRAINT bookings_valid_status CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    CONSTRAINT bookings_valid_cancellation CHECK (
        (status = 'CONFIRMED' AND cancelled_at IS NULL) OR
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
    )
);
CREATE INDEX bookings_resource_start_idx ON bookings(resource_id, starts_at, id);

--changeset mikey:004-exclusion
ALTER TABLE bookings ADD CONSTRAINT bookings_no_confirmed_overlap
    EXCLUDE USING gist (
        resource_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status = 'CONFIRMED');

