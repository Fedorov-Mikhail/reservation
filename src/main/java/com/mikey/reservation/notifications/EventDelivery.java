package com.mikey.reservation.notifications;
import org.springframework.stereotype.Service;import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;import java.util.*;
@Service
public class EventDelivery {
 private final JdbcTemplate jdbc;public EventDelivery(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Transactional public void deliver(UUID id){
  var rows=jdbc.queryForList("select id,user_id,booking_id,status from outbox_events where id=? and notified_at is null and attempts<10 and next_attempt_at<=now() for update skip locked",id);
  if(rows.isEmpty())return;
  var event=rows.getFirst();
  jdbc.update("insert into notifications(id,event_id,user_id,booking_id,status) values (?,?,?,?,?) on conflict(event_id) do nothing",UUID.randomUUID(),event.get("id"),event.get("user_id"),event.get("booking_id"),event.get("status"));
  jdbc.update("update outbox_events set notified_at=now(),last_error=null where id=?",id);
 }
}

