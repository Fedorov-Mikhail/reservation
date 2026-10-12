--liquibase formatted sql
--changeset mikey:010-resource-lifecycle
ALTER TABLE resources ADD COLUMN archived_at timestamptz;
ALTER TABLE resources ADD COLUMN archived_by uuid REFERENCES app_users(id);
ALTER TABLE resources ADD CONSTRAINT resources_archive_actor CHECK ((archived_at IS NULL) = (archived_by IS NULL));
CREATE INDEX resources_catalog_idx ON resources(name,id) WHERE archived_at IS NULL;
CREATE INDEX resources_archive_idx ON resources(name,id) WHERE archived_at IS NOT NULL;
