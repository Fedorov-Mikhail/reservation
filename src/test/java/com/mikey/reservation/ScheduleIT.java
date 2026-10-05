package com.mikey.reservation;
import com.mikey.reservation.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"app.bootstrap.username=integration-admin","app.bootstrap.password=Integration-admin-123"})
class ScheduleIT extends PostgresSupport {
 @Value("${local.server.port}") int port;final JsonMapper json=JsonMapper.builder().build();
 @Test void closedDayRejectsBookingAndScheduleChangeCannotInvalidateBooking() throws Exception{
  var c=new SessionClient(port);c.login("integration-admin","Integration-admin-123");
  String id=json.readTree(c.call("POST","/api/v1/resources","{\"name\":\"Schedule test\"}").body()).path("id").asText();
  String base="/api/v1/resources/"+id;
  var day=java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(3);
  String body="{\"zoneId\":\"UTC\",\"weekly\":{\"1\":[{\"startMinute\":540,\"endMinute\":1080}],\"2\":[{\"startMinute\":540,\"endMinute\":1080}],\"3\":[{\"startMinute\":540,\"endMinute\":1080}],\"4\":[{\"startMinute\":540,\"endMinute\":1080}],\"5\":[{\"startMinute\":540,\"endMinute\":1080}],\"6\":[{\"startMinute\":540,\"endMinute\":1080}],\"7\":[{\"startMinute\":540,\"endMinute\":1080}]},\"exceptions\":{}}";
  assertThat(c.call("PUT",base+"/schedule",body).statusCode()).isEqualTo(200);
  var booked=c.call("POST",base+"/bookings","{\"startsAt\":\""+day+"T10:00:00Z\",\"endsAt\":\""+day+"T11:00:00Z\"}");
  assertThat(booked.statusCode()).as(booked.body()).isEqualTo(201);
  String closed="{\"zoneId\":\"UTC\",\"weekly\":{},\"exceptions\":{}}";
  assertThat(c.call("PUT",base+"/schedule",closed).statusCode()).isEqualTo(409);
  assertThat(c.call("POST",base+"/bookings","{\"startsAt\":\""+day+"T08:00:00Z\",\"endsAt\":\""+day+"T10:00:00Z\"}").statusCode()).isEqualTo(409);
  var available=json.readTree(c.call("GET",base+"/availability?from="+day+"T00:00:00Z&to="+day.plusDays(1)+"T00:00:00Z",null).body());
  assertThat(available.path("intervals").size()).isEqualTo(2);
  assertThat(available.path("intervals").get(0).path("startsAt").asText()).endsWith("T09:00:00Z");
  String exception=body.replace("\"exceptions\":{}","\"exceptions\":{\""+day.plusDays(1)+"\":[]}");
  assertThat(c.call("PUT",base+"/schedule",exception).statusCode()).isEqualTo(200);
  assertThat(json.readTree(c.call("GET",base+"/availability?from="+day.plusDays(1)+"T00:00:00Z&to="+day.plusDays(2)+"T00:00:00Z",null).body()).path("intervals").size()).isZero();
 }
}

