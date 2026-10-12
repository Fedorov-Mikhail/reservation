package com.mikey.reservation.notifications;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable jobs. Every queue transaction finishes before provider I/O. */
@Service
public class EmailQueue {
 public static final int MAX_ATTEMPTS=5;
 public record Job(UUID id,UUID leaseToken,UUID userId,UUID bookingId,String kind,String status,
                   String resourceName,Instant startsAt,Instant endsAt,int attempts) {}
 private final JdbcTemplate jdbc;
 public EmailQueue(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Transactional public void reminders(){
  jdbc.update("""
   insert into email_deliveries(id,dedup_key,user_id,booking_id,kind,booking_status,resource_name,starts_at,ends_at)
   select gen_random_uuid(),'reminder:'||b.id,b.created_by,b.id,'REMINDER',b.status,r.name,b.starts_at,b.ends_at
   from bookings b join resources r on r.id=b.resource_id
   join notification_preferences p on p.user_id=b.created_by
   join app_users u on u.id=b.created_by
   where b.status='CONFIRMED' and p.email_enabled and u.enabled and b.starts_at>now()
    and b.starts_at<=now()+make_interval(mins=>p.reminder_minutes)
   on conflict(dedup_key) do nothing
   """);
 }
 @Transactional public List<Job> claim(){
  jdbc.update("update email_deliveries set state='FAILED',last_error='LEASE_EXPIRED',lease_until=null,lease_token=null where state='PROCESSING' and lease_until<now() and attempts>=?",MAX_ATTEMPTS);
  UUID token=UUID.randomUUID();
  return jdbc.query("""
   with candidates as (
    select id from email_deliveries where attempts<? and
      ((state='PENDING' and available_at<=now()) or (state='PROCESSING' and lease_until<now()))
    order by available_at,created_at,id limit 1 for update skip locked
   )
   update email_deliveries d set state='PROCESSING',attempts=attempts+1,lease_token=?,lease_until=now()+interval '2 minutes'
   from candidates c where d.id=c.id returning d.*
   """,(rs,n)->new Job(rs.getObject("id",UUID.class),rs.getObject("lease_token",UUID.class),
    rs.getObject("user_id",UUID.class),rs.getObject("booking_id",UUID.class),rs.getString("kind"),
    rs.getString("booking_status"),rs.getString("resource_name"),rs.getTimestamp("starts_at").toInstant(),
    rs.getTimestamp("ends_at").toInstant(),rs.getInt("attempts")),MAX_ATTEMPTS,token);
 }
 public Optional<String> recipient(Job job){
  return jdbc.query("""
   select p.email from notification_preferences p join app_users u on u.id=p.user_id
   join bookings b on b.id=? where p.user_id=? and p.email_enabled and u.enabled
   and (?<>'REMINDER' or (b.status='CONFIRMED' and b.starts_at>now()))
   """,(rs,n)->rs.getString(1),job.bookingId(),job.userId(),job.kind()).stream().findFirst();
 }
 public void complete(Job job,String state){
  jdbc.update("update email_deliveries set state=?,sent_at=case when ?='SENT' then now() else null end,lease_until=null,lease_token=null,last_error=null where id=? and state='PROCESSING' and lease_token=?",
   state,state,job.id(),job.leaseToken());
 }
 public void failed(Job job){
  jdbc.update("""
   update email_deliveries set state=case when attempts>=? then 'FAILED' else 'PENDING' end,
    available_at=now()+make_interval(secs=>least(3600,power(2,attempts)*30)::int),
    lease_until=null,lease_token=null,last_error='SMTP_FAILED'
   where id=? and state='PROCESSING' and lease_token=?
   """,MAX_ATTEMPTS,job.id(),job.leaseToken());
 }
}
