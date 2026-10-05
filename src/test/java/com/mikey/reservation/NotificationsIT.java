package com.mikey.reservation;
import com.mikey.reservation.support.*;import com.mikey.reservation.notifications.*;
import org.junit.jupiter.api.Test;import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.*;import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"app.bootstrap.username=integration-admin","app.bootstrap.password=Integration-admin-123","app.workers.enabled=false"})
class NotificationsIT extends PostgresSupport {
 @Value("${local.server.port}")int port;@Autowired JdbcTemplate jdbc;@Autowired TransactionTemplate tx;@Autowired NotificationService notifications;
 final JsonMapper json=JsonMapper.builder().build();
 @Test void rollbackLeavesNoEventAndRepeatedDeliveryHasNoDuplicate()throws Exception {
  var c=new SessionClient(port);c.login("integration-admin","Integration-admin-123");
  UUID resource=UUID.fromString(json.readTree(c.call("POST","/api/v1/resources","{\"name\":\"Events test\"}").body()).path("id").asText());
  UUID rolledBack=UUID.randomUUID();
  tx.executeWithoutResult(status->{
   jdbc.update("insert into bookings(id,resource_id,starts_at,ends_at,status,created_at) values (?,?,now()+interval '1 day',now()+interval '25 hours','CONFIRMED',now())",rolledBack,resource);
   assertThat(jdbc.queryForObject("select count(*) from outbox_events where booking_id=?",Integer.class,rolledBack)).isEqualTo(1);
   status.setRollbackOnly();
  });
  assertThat(jdbc.queryForObject("select count(*) from outbox_events where booking_id=?",Integer.class,rolledBack)).isZero();
  var day=java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(2);
  String booking=json.readTree(c.call("POST","/api/v1/resources/"+resource+"/bookings","{\"startsAt\":\""+day+"T10:00:00Z\",\"endsAt\":\""+day+"T11:00:00Z\"}").body()).path("id").asText();
  notifications.deliver();notifications.deliver();
  assertThat(jdbc.queryForObject("select count(*) from notifications where booking_id=?",Integer.class,UUID.fromString(booking))).isEqualTo(1);
  UUID notice=jdbc.queryForObject("select id from notifications where booking_id=?",UUID.class,UUID.fromString(booking));
  assertThat(c.call("POST","/api/v1/me/notifications/"+notice+"/read",null).statusCode()).isEqualTo(200);
 }
}

