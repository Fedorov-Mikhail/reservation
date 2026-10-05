--liquibase formatted sql
--changeset mikey:006-schedules
ALTER TABLE resources ADD COLUMN time_zone varchar(100) NOT NULL DEFAULT 'UTC';
CREATE TABLE resource_schedules(resource_id uuid PRIMARY KEY REFERENCES resources(id), definition jsonb NOT NULL);

