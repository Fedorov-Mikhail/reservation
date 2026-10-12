package com.mikey.reservation.notifications;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class EventDelivery {
 private final JdbcTemplate jdbc;
 public EventDelivery(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Transactional public void deliver(UUID id){
  var rows=jdbc.queryForList("select id,user_id,booking_id,status from outbox_events where id=? and notified_at is null and attempts<10 and next_attempt_at<=now() for update skip locked",id);
  if(rows.isEmpty())return;
  var event=rows.getFirst();
  jdbc.update("""
   insert into notifications(id,event_id,user_id,booking_id,status,resource_name,starts_at,ends_at)
   select ?,e.id,e.user_id,e.booking_id,e.status,r.name,b.starts_at,b.ends_at
   from outbox_events e join bookings b on b.id=e.booking_id join resources r on r.id=b.resource_id
   where e.id=? on conflict(event_id) do nothing
   """,UUID.randomUUID(),id);
  // Only committed outbox rows are visible; internal delivery and durable email job commit atomically.
  jdbc.update("""
   insert into email_deliveries(id,dedup_key,user_id,booking_id,kind,booking_status,resource_name,starts_at,ends_at)
   select gen_random_uuid(),'event:'||e.id,e.user_id,e.booking_id,'STATUS',e.status,r.name,b.starts_at,b.ends_at
   from outbox_events e join bookings b on b.id=e.booking_id join resources r on r.id=b.resource_id
   join notification_preferences p on p.user_id=e.user_id join app_users u on u.id=e.user_id
   where e.id=? and p.email_enabled and u.enabled on conflict(dedup_key) do nothing
   """,id);
  jdbc.update("update outbox_events set notified_at=now(),last_error=null where id=?",event.get("id"));
 }
}
