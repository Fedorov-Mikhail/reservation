package com.mikey.reservation.notifications;

import com.mikey.reservation.support.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "app.bootstrap.username=integration-admin","app.bootstrap.password=Integration-admin-123","app.workers.enabled=false"})
class EmailDeliveryIT extends PostgresSupport {
 @Autowired JdbcTemplate jdbc; @Autowired EmailQueue queue; @Autowired EventDelivery events;
 @Value("${local.server.port}") int port;
 final JsonMapper json=JsonMapper.builder().build();
 record Fixture(UUID user,UUID booking,UUID event){}
 Fixture fixture(){
  UUID user=UUID.randomUUID(),resource=UUID.randomUUID(),booking=UUID.randomUUID();
  jdbc.update("insert into app_users(id,username,password_hash,role) values (?,?,?,'USER')",user,"email-"+user,"disabled");
  jdbc.update("insert into resources(id,name,created_at,updated_at,created_by) values (?,'Email room',now(),now(),?)",resource,user);
  jdbc.update("insert into notification_preferences(user_id,email,email_enabled,reminder_minutes) values (?,'fake@example.test',true,30)",user);
  jdbc.update("insert into bookings(id,resource_id,starts_at,ends_at,status,created_at,created_by) values (?,?,?,?,'CONFIRMED',now(),?)",
   booking,resource,Timestamp.from(Instant.now().plusSeconds(1200)),Timestamp.from(Instant.now().plusSeconds(4800)),user);
  return new Fixture(user,booking,jdbc.queryForObject("select id from outbox_events where booking_id=?",UUID.class,booking));
 }
 String state(UUID id){return jdbc.queryForObject("select state from email_deliveries where id=?",String.class,id);}
 @Test void committedEventsAndRemindersDeduplicateAndOptOutCancelsQueuedEmail(){
  var f=fixture();events.deliver(f.event());events.deliver(f.event());queue.reminders();queue.reminders();
  assertThat(jdbc.queryForObject("select count(*) from email_deliveries where booking_id=?",Integer.class,f.booking())).isEqualTo(2);
  assertThat(jdbc.queryForObject("select resource_name from notifications where event_id=?",String.class,f.event())).isEqualTo("Email room");
  jdbc.update("update notification_preferences set email_enabled=false where user_id=?",f.user());
  jdbc.update("update email_deliveries set available_at=now()-interval '1 day' where booking_id=?",f.booking());
  var job=queue.claim().getFirst();
  assertThat(job.bookingId()).isEqualTo(f.booking());assertThat(queue.recipient(job)).isEmpty();
  queue.complete(job,"SKIPPED");assertThat(state(job.id())).isEqualTo("SKIPPED");
  // Complete the second queued job to keep subsequent tests isolated.
  var next=queue.claim().getFirst();queue.complete(next,"SKIPPED");
 }
 @Test void multiWorkerClaimsLeaseRecoveryAndRetryCap()throws Exception{
  var f=fixture();events.deliver(f.event());queue.reminders();
  jdbc.update("update email_deliveries set available_at=now()-interval '2 days' where booking_id=?",f.booking());
  var pool=Executors.newFixedThreadPool(2);
  List<EmailQueue.Job> jobs;
  try{
   var a=pool.submit(()->queue.claim());var b=pool.submit(()->queue.claim());
   jobs=new ArrayList<>(a.get(10,TimeUnit.SECONDS));jobs.addAll(b.get(10,TimeUnit.SECONDS));
  }finally{pool.shutdownNow();}
  assertThat(jobs).hasSize(2);assertThat(jobs.stream().map(EmailQueue.Job::id).distinct()).hasSize(2);
  var old=jobs.getFirst();var other=jobs.getLast();queue.complete(other,"SENT");
  jdbc.update("update email_deliveries set lease_until=now()-interval '1 second' where id=?",old.id());
  var recovered=queue.claim().getFirst();assertThat(recovered.id()).isEqualTo(old.id());
  assertThat(recovered.leaseToken()).isNotEqualTo(old.leaseToken());assertThat(recovered.attempts()).isEqualTo(2);
  queue.complete(old,"SENT");assertThat(state(old.id())).isEqualTo("PROCESSING");
  queue.failed(recovered);assertThat(state(old.id())).isEqualTo("PENDING");
  assertThat(jdbc.queryForObject("select available_at>now() from email_deliveries where id=?",Boolean.class,old.id())).isTrue();
  jdbc.update("update email_deliveries set attempts=4,available_at=now()-interval '2 days' where id=?",old.id());
  var last=queue.claim().getFirst();queue.failed(last);assertThat(state(old.id())).isEqualTo("FAILED");
 }
 @Test void cancelledBookingsNeverReceiveReminders(){
  var f=fixture();queue.reminders();
  jdbc.update("update bookings set status='CANCELLED',cancelled_at=now() where id=?",f.booking());
  var reminder=jdbc.queryForObject("select id from email_deliveries where booking_id=? and kind='REMINDER'",UUID.class,f.booking());
  jdbc.update("update email_deliveries set available_at=now()-interval '3 days' where id=?",reminder);
  var job=queue.claim().getFirst();assertThat(job.id()).isEqualTo(reminder);assertThat(queue.recipient(job)).isEmpty();queue.complete(job,"SKIPPED");
  var held=fixture();
  jdbc.update("update bookings set status='HELD',expires_at=now()+interval '5 minutes' where id=?",held.booking());
  queue.reminders();
  assertThat(jdbc.queryForObject("select count(*) from email_deliveries where booking_id=? and kind='REMINDER'",Integer.class,held.booking())).isZero();
 }
 SessionClient user()throws Exception{
  var c=new SessionClient(port);String username="mail"+UUID.randomUUID().toString().replace("-","");
  assertThat(c.call("POST","/api/v1/auth/register","{\"username\":\""+username+"\",\"password\":\"User-password-123\"}").statusCode()).isEqualTo(201);
  assertThat(c.login(username,"User-password-123").statusCode()).isEqualTo(200);return c;
 }
 @Test void preferencesValidateAndArePrivateAndAdminMetadataIsRestricted()throws Exception{
  var alice=user();var bob=user();
  assertThat(alice.call("PUT","/api/v1/me/notification-preferences","{\"email\":\"bad\",\"emailEnabled\":true,\"reminderMinutes\":0}").statusCode()).isEqualTo(400);
  assertThat(alice.call("PUT","/api/v1/me/notification-preferences","{\"email\":\"alice@example.test\",\"emailEnabled\":true,\"reminderMinutes\":15}").statusCode()).isEqualTo(200);
  assertThat(json.readTree(alice.call("GET","/api/v1/me/notification-preferences",null).body()).path("email").asText()).isEqualTo("alice@example.test");
  assertThat(json.readTree(bob.call("GET","/api/v1/me/notification-preferences",null).body()).path("email").asText()).isEmpty();
  assertThat(alice.call("GET","/api/v1/admin/email-deliveries",null).statusCode()).isEqualTo(403);
  var admin=new SessionClient(port);admin.login("integration-admin","Integration-admin-123");
  var response=admin.call("GET","/api/v1/admin/email-deliveries",null);
  assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).doesNotContain("email","leaseToken","fake@example");
 }
}
