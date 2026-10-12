package com.mikey.reservation;
import com.mikey.reservation.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;import java.util.*;import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"app.bootstrap.username=integration-admin","app.bootstrap.password=Integration-admin-123"})
class LifecycleAuditIT extends PostgresSupport {
 @Value("${local.server.port}") int port; @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager manager;
 final JsonMapper json=JsonMapper.builder().build();
 SessionClient admin()throws Exception{var c=new SessionClient(port);assertThat(c.login("integration-admin","Integration-admin-123").statusCode()).isEqualTo(200);return c;}
 String resource(SessionClient c,String name)throws Exception{return json.readTree(c.call("POST","/api/v1/resources","{\"name\":\""+name+"\",\"location\":\"North\"}").body()).path("id").asText();}
 String interval(){String day=LocalDate.now(ZoneOffset.UTC).plusDays(2).toString();return "{\"startsAt\":\""+day+"T10:00:00Z\",\"endsAt\":\""+day+"T11:00:00Z\"}";}
 @Test void archiveRestoreAndSearch()throws Exception{
  var c=admin();String name="archive_"+UUID.randomUUID().toString().replace("-","");String id=resource(c,name);
  assertThat(c.call("POST","/api/v1/resources/"+id+"/archive",null).statusCode()).isEqualTo(200);
  assertThat(json.readTree(c.call("GET","/api/v1/resources?q="+name,null).body()).path("totalElements").asInt()).isZero();
  assertThat(json.readTree(c.call("GET","/api/v1/resources?archived=true&q="+name,null).body()).path("totalElements").asInt()).isEqualTo(1);
  assertThat(c.call("POST","/api/v1/resources/"+id+"/bookings",interval()).statusCode()).isEqualTo(409);
  assertThat(c.call("POST","/api/v1/resources/"+id+"/waitlist",interval()).statusCode()).isEqualTo(409);
  assertThat(c.call("GET","/api/v1/resources/"+id,null).statusCode()).isEqualTo(200);
  assertThat(c.call("POST","/api/v1/resources/"+id+"/restore",null).statusCode()).isEqualTo(200);
  assertThat(c.call("POST","/api/v1/resources/"+id+"/bookings",interval()).statusCode()).isEqualTo(201);
  assertThat(c.call("POST","/api/v1/resources/"+id+"/archive",null).statusCode()).isEqualTo(409);
 }
 @Test void auditIsAtomicAndRecordsActor()throws Exception{
  var c=admin();String id=resource(c,"Atomic audit");UUID uuid=UUID.fromString(id);String owner=json.readTree(c.call("GET","/api/v1/auth/me",null).body()).path("id").asText();
  var response=json.readTree(c.call("GET","/api/v1/admin/audit?entityId="+id,null).body());assertThat(response.path("items").get(0).path("actorId").asText()).isEqualTo(owner);
  int before=jdbc.queryForObject("select count(*) from audit_events where entity_id=?",Integer.class,uuid);
  assertThatThrownBy(()->new TransactionTemplate(manager).executeWithoutResult(s->{jdbc.update("update resources set name='rolled-back' where id=?",uuid);throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
  assertThat(jdbc.queryForObject("select count(*) from audit_events where entity_id=?",Integer.class,uuid)).isEqualTo(before);
  assertThat(jdbc.queryForObject("select name from resources where id=?",String.class,uuid)).isEqualTo("Atomic audit");
  var user=new SessionClient(port);String username="u"+UUID.randomUUID().toString().replace("-","");user.call("POST","/api/v1/auth/register","{\"username\":\""+username+"\",\"password\":\"Pass1234\"}");user.login(username,"Pass1234");
  assertThat(user.call("GET","/api/v1/admin/audit",null).statusCode()).isEqualTo(403);
  assertThat(user.call("POST","/api/v1/resources/"+id+"/archive",null).statusCode()).isEqualTo(403);
 }
 @Test void deliveryMetricsRequireCurrentAdminRole()throws Exception {
  var admin=admin();assertThat(admin.call("GET","/actuator/metrics/reservation.email.failed",null).statusCode()).isEqualTo(200);
  assertThat(admin.call("GET","/actuator/metrics/reservation.calendar.pending",null).statusCode()).isEqualTo(200);
  var user=new SessionClient(port);String username="metrics_"+UUID.randomUUID().toString().replace("-","");user.call("POST","/api/v1/auth/register","{\"username\":\""+username+"\",\"password\":\"Pass1234\"}");user.login(username,"Pass1234");
  assertThat(user.call("GET","/actuator/metrics",null).statusCode()).isEqualTo(403);
  jdbc.update("update app_users set role='ADMIN' where username=?",username);
  assertThat(user.call("GET","/actuator/metrics/reservation.email.pending",null).statusCode()).isEqualTo(200);
  jdbc.update("update app_users set role='USER' where username=?",username);
  assertThat(user.call("GET","/actuator/metrics",null).statusCode()).isEqualTo(403);
 }
 @Test void archivingAndBookingSerialize()throws Exception{
  var creator=admin();var archivist=admin();String id=resource(creator,"Archive race");String body=interval();var start=new CountDownLatch(1);
  try(var executor=Executors.newFixedThreadPool(2)){
   var booking=executor.submit(()->{start.await();return creator.call("POST","/api/v1/resources/"+id+"/bookings",body).statusCode();});
   var archive=executor.submit(()->{start.await();return archivist.call("POST","/api/v1/resources/"+id+"/archive",null).statusCode();});start.countDown();int b=booking.get(30,TimeUnit.SECONDS),a=archive.get(30,TimeUnit.SECONDS);
   assertThat((b==201&&a==409)||(b==409&&a==200)).as("booking=%s archive=%s",b,a).isTrue();
  }
 }
}
