package com.mikey.reservation;
import com.mikey.reservation.support.*;
import org.junit.jupiter.api.Test;import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.*;import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"app.bootstrap.username=integration-admin","app.bootstrap.password=Integration-admin-123","app.workers.enabled=false"})
class WaitlistIT extends PostgresSupport {
 @Value("${local.server.port}")int port;@Autowired JdbcTemplate jdbc;final JsonMapper json=JsonMapper.builder().build();
 String id(java.net.http.HttpResponse<String> r){return json.readTree(r.body()).path("id").asText();}
 @Test void cancellationOffersToOldestAndConfirmationFulfills()throws Exception{
  var c=new SessionClient(port);c.login("integration-admin","Integration-admin-123");
  String resource=id(c.call("POST","/api/v1/resources","{\"name\":\"Queue test\"}"));
  var day=java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(3);
  String body="{\"startsAt\":\""+day+"T10:00:00Z\",\"endsAt\":\""+day+"T11:00:00Z\"}";
  String booking=id(c.call("POST","/api/v1/resources/"+resource+"/bookings",body));
  var waiting=c.call("POST","/api/v1/resources/"+resource+"/waitlist",body);
  assertThat(waiting.statusCode()).as(waiting.body()).isEqualTo(201);
  assertThat(c.call("POST","/api/v1/bookings/"+booking+"/cancel",null).statusCode()).isEqualTo(200);
  var entries=json.readTree(c.call("GET","/api/v1/me/waitlist",null).body());
  var entry=entries.findValues("status");assertThat(entry.toString()).contains("OFFERED");
  var hold=jdbc.queryForObject("select offered_booking_id from waitlist_entries where id=?",UUID.class,UUID.fromString(id(waiting)));
  assertThat(c.call("POST","/api/v1/resources/"+resource+"/bookings",body).statusCode()).isEqualTo(409);
  assertThat(c.call("POST","/api/v1/bookings/"+hold+"/confirm",null).statusCode()).isEqualTo(200);
  assertThat(jdbc.queryForObject("select status from waitlist_entries where id=?",String.class,UUID.fromString(id(waiting)))).isEqualTo("FULFILLED");
 }
 @Test void notificationEventsAreTransactionalAndDeliveryIdempotent()throws Exception{
  assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=current_schema() and table_name='outbox_events'",Integer.class)).isEqualTo(1);
 }
}

