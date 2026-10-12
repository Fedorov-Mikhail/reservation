--liquibase formatted sql
--changeset mikey:011-audit splitStatements:false
CREATE TABLE audit_events (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), actor_id uuid REFERENCES app_users(id),
 action varchar(40) NOT NULL, entity_type varchar(40) NOT NULL, entity_id uuid NOT NULL,
 details jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_time_idx ON audit_events(created_at DESC,id DESC);
CREATE INDEX audit_entity_idx ON audit_events(entity_id,created_at DESC,id DESC);
CREATE FUNCTION reservation_audit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE actor uuid; entity uuid; kind text; verb text; payload jsonb; old_json jsonb; new_json jsonb;
BEGIN
 actor := nullif(current_setting('reservation.actor_id',true),'')::uuid;
 old_json := CASE WHEN TG_OP='INSERT' THEN '{}'::jsonb ELSE to_jsonb(OLD) END;
 new_json := CASE WHEN TG_OP='DELETE' THEN '{}'::jsonb ELSE to_jsonb(NEW) END;
 kind := CASE TG_TABLE_NAME WHEN 'bookings' THEN 'BOOKING' WHEN 'resources' THEN 'RESOURCE' WHEN 'app_users' THEN 'USER' WHEN 'resource_managers' THEN 'RESOURCE_MANAGER' ELSE 'SCHEDULE' END;
 entity := CASE WHEN TG_TABLE_NAME IN ('resource_schedules','resource_managers') THEN coalesce(new_json->>'resource_id',old_json->>'resource_id')::uuid ELSE coalesce(new_json->>'id',old_json->>'id')::uuid END;
 verb := TG_OP;
 IF TG_TABLE_NAME='bookings' THEN
  IF TG_OP='UPDATE' AND old_json->>'status'=new_json->>'status' THEN RETURN NEW; END IF;
  payload := jsonb_build_object('resourceId',coalesce(new_json->'resource_id',old_json->'resource_id'),'oldStatus',old_json->'status','status',new_json->'status','reason',new_json->'cancellation_reason');
 ELSIF TG_TABLE_NAME='app_users' THEN
  IF TG_OP='UPDATE' AND old_json->>'role'=new_json->>'role' AND old_json->>'enabled'=new_json->>'enabled' THEN RETURN NEW; END IF;
  payload := jsonb_build_object('username',coalesce(new_json->'username',old_json->'username'),'oldRole',old_json->'role','role',new_json->'role','enabled',new_json->'enabled');
 ELSIF TG_TABLE_NAME='resource_managers' THEN payload := jsonb_build_object('userId',coalesce(new_json->'user_id',old_json->'user_id'));
 ELSIF TG_TABLE_NAME='resource_schedules' THEN payload := jsonb_build_object('scheduleChanged',true);
 ELSE
  IF TG_OP='UPDATE' AND old_json->'archived_at' IS DISTINCT FROM new_json->'archived_at' THEN verb := CASE WHEN new_json->>'archived_at' IS NULL THEN 'RESTORE' ELSE 'ARCHIVE' END; END IF;
  payload := jsonb_build_object('name',coalesce(new_json->'name',old_json->'name'),'archivedAt',new_json->'archived_at');
 END IF;
 INSERT INTO audit_events(actor_id,action,entity_type,entity_id,details) VALUES(actor,verb,kind,entity,payload);
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER audit_bookings AFTER INSERT OR UPDATE OR DELETE ON bookings FOR EACH ROW EXECUTE FUNCTION reservation_audit();
CREATE TRIGGER audit_resources AFTER INSERT OR UPDATE OR DELETE ON resources FOR EACH ROW EXECUTE FUNCTION reservation_audit();
CREATE TRIGGER audit_users AFTER INSERT OR UPDATE OR DELETE ON app_users FOR EACH ROW EXECUTE FUNCTION reservation_audit();
CREATE TRIGGER audit_managers AFTER INSERT OR UPDATE OR DELETE ON resource_managers FOR EACH ROW EXECUTE FUNCTION reservation_audit();
CREATE TRIGGER audit_schedules AFTER INSERT OR UPDATE OR DELETE ON resource_schedules FOR EACH ROW EXECUTE FUNCTION reservation_audit();
