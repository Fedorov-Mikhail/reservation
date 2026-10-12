package com.mikey.reservation.calendar;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import java.time.*;

@Component
@ConditionalOnExpression("${app.calendar.enabled:false} && ${app.workers.enabled:true}")
public class CalendarSyncWorker {
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final TransactionTemplate independent;private final TokenCipher cipher;private final GoogleCalendarClient provider;private final Clock clock;
 public CalendarSyncWorker(JdbcTemplate jdbc,PlatformTransactionManager manager,TokenCipher cipher,GoogleCalendarClient provider,Clock clock){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.independent=new TransactionTemplate(manager);this.independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);this.cipher=cipher;this.provider=provider;this.clock=clock;}
 record Job(UUID booking,UUID user,UUID generation,UUID lease,String status,int attempts){}
 @Scheduled(fixedDelayString="${app.calendar.interval-ms:5000}") public void tick(){
  try{scan();for(int i=0;i<20;i++){Job job=claim();if(job==null)break;deliver(job);}}
  catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("calendar_sync_failed type={}",e.getClass().getSimpleName());}
 }
 public void scan(){
  jdbc.update("insert into calendar_event_mappings(booking_id) select b.id from bookings b join calendar_connections c on c.user_id=b.created_by and c.status='CONNECTED' where b.status in ('CONFIRMED','CANCELLED') on conflict do nothing");
  jdbc.update("""
   insert into calendar_sync_jobs(booking_id,user_id,generation,desired_status,state)
   select b.id,b.created_by,c.generation,b.status,'PENDING' from bookings b
   join calendar_connections c on c.user_id=b.created_by and c.status='CONNECTED'
   where b.status in ('CONFIRMED','CANCELLED') and (b.status='CANCELLED' or b.ends_at>now())
   on conflict(booking_id) do update set desired_status=excluded.desired_status,generation=excluded.generation,
    state='PENDING',attempts=0,next_attempt_at=now(),lease_token=null,lease_until=null,updated_at=now()
   where calendar_sync_jobs.desired_status<>excluded.desired_status or calendar_sync_jobs.generation<>excluded.generation
    or (calendar_sync_jobs.state='DONE' and excluded.desired_status='CONFIRMED' and calendar_sync_jobs.updated_at<now()-interval '6 hours')
   """);
 }
 Job claim(){return tx.execute(s->{jdbc.update("update calendar_sync_jobs set state='FAILED',last_error='LEASE_RETRIES_EXHAUSTED',lease_token=null,lease_until=null where state='LEASED' and lease_until<now() and attempts>=6");UUID lease=UUID.randomUUID();return jdbc.query("""
  with candidate as (select booking_id from calendar_sync_jobs where
   (state='PENDING' and next_attempt_at<=now()) or (state='LEASED' and lease_until<now())
   order by next_attempt_at for update skip locked limit 1)
  update calendar_sync_jobs j set state='LEASED',lease_token=?,lease_until=now()+interval '2 minutes',attempts=attempts+1
   from candidate c where j.booking_id=c.booking_id returning j.*
  """,(r,n)->new Job(r.getObject("booking_id",UUID.class),r.getObject("user_id",UUID.class),r.getObject("generation",UUID.class),lease,r.getString("desired_status"),r.getInt("attempts")),lease).stream().findFirst().orElse(null);});}
 void deliver(Job job){
  // Connection row lock serializes refresh, disconnect and delivery. This is never a booking transaction.
  tx.executeWithoutResult(s->{
   var connections=jdbc.queryForList("select * from calendar_connections where user_id=? for update",job.user());
   if(connections.isEmpty()||!"CONNECTED".equals(connections.getFirst().get("status"))||!job.generation().equals(connections.getFirst().get("generation"))){finish(job,"CANCELLED",null,0);return;}
   var active=jdbc.queryForList("select booking_id from calendar_sync_jobs where booking_id=? and lease_token=? and state='LEASED' for update",job.booking(),job.lease());if(active.isEmpty())return;
   var conn=connections.getFirst();
   var bookings=jdbc.queryForList("select b.status,b.starts_at,b.ends_at,r.name from bookings b join resources r on r.id=b.resource_id where b.id=? and b.created_by=?",job.booking(),job.user());
   if(bookings.isEmpty()){finish(job,"CANCELLED",null,0);return;}var booking=bookings.getFirst();
   String status=booking.get("status").toString();if(!status.equals(job.status())){finish(job,"DONE",null,0);return;}
   try{
    String token=cipher.decrypt(job.user(),conn.get("access_token").toString());
    var expires=((java.sql.Timestamp)conn.get("expires_at")).toInstant();
    if(expires.isBefore(clock.instant().plusSeconds(30)))token=refresh(job,conn);
    try{sync(job,booking,token);}catch(GoogleCalendarClient.ProviderFailure e){if(e.status!=401)throw e;token=refresh(job,conn);sync(job,booking,token);}
    finish(job,"DONE",null,0);
   }catch(GoogleCalendarClient.ProviderFailure e){
    if(e.reconnect||e.status==401){jdbc.update("update calendar_connections set status='RECONNECT_REQUIRED',access_token=null,refresh_token=null,expires_at=null,updated_at=now() where user_id=?",job.user());finish(job,"FAILED","RECONNECT_REQUIRED",0);}
    else if(e.retryable()&&job.attempts()<6)finish(job,"PENDING","PROVIDER_RETRY",Math.min(3600,5L<<Math.min(10,job.attempts())));
    else finish(job,"FAILED","PROVIDER_FAILED",0);
   }catch(RuntimeException e){finish(job,"FAILED","CREDENTIAL_OR_SYNC_FAILED",0);}
  });
 }
 private String refresh(Job job,Map<String,Object> conn){var tokens=provider.refresh(cipher.decrypt(job.user(),conn.get("refresh_token").toString()));jdbc.update("update calendar_connections set access_token=?,refresh_token=?,expires_at=?,updated_at=now() where user_id=?",cipher.encrypt(job.user(),tokens.accessToken()),tokens.refreshToken()==null?conn.get("refresh_token"):cipher.encrypt(job.user(),tokens.refreshToken()),java.sql.Timestamp.from(clock.instant().plusSeconds(tokens.expiresIn())),job.user());return tokens.accessToken();}
 private void sync(Job job,Map<String,Object> b,String token){
  int revision=jdbc.queryForObject("select event_revision from calendar_event_mappings where booking_id=?",Integer.class,job.booking());
  try{syncRevision(job,b,token,revision);}catch(GoogleCalendarClient.EventTombstone e){
   // Commit the new identity BEFORE the create request: a lost response or process crash must reuse it.
   int next=independent.execute(s->jdbc.queryForObject("update calendar_event_mappings set event_revision=event_revision+1 where booking_id=? returning event_revision",Integer.class,job.booking()));
   syncRevision(job,b,token,next);
  }
 }
 private void syncRevision(Job job,Map<String,Object> b,String token,int revision){String id=GoogleCalendarClient.eventId(job.user(),job.booking())+(revision==0?"":"g"+revision);provider.sync(token,id,job.status().equals("CANCELLED"),"Reservation: "+b.get("name"),((java.sql.Timestamp)b.get("starts_at")).toInstant(),((java.sql.Timestamp)b.get("ends_at")).toInstant());}
 private void finish(Job job,String state,String error,long delay){jdbc.update("update calendar_sync_jobs set state=?,last_error=?,next_attempt_at=now()+(? * interval '1 second'),lease_token=null,lease_until=null,updated_at=now() where booking_id=? and lease_token=?",state,error,delay,job.booking(),job.lease());}
}
