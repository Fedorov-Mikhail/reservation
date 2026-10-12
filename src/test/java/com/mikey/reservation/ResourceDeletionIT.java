package com.mikey.reservation;

import com.mikey.reservation.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "app.bootstrap.username=integration-admin","app.bootstrap.password=Integration-admin-123"})
class ResourceDeletionIT extends PostgresSupport {
 @Value("${local.server.port}") int port;
 @Autowired JdbcTemplate jdbc;
 final JsonMapper json=JsonMapper.builder().build();
 SessionClient admin() throws Exception {var c=new SessionClient(port);assertThat(c.login("integration-admin","Integration-admin-123").statusCode()).isEqualTo(200);return c;}
 String resource(SessionClient c)throws Exception{return json.readTree(c.call("POST","/api/v1/resources","{\"name\":\"Deletion test\"}").body()).path("id").asText();}
 String interval(){String day=LocalDate.now(ZoneOffset.UTC).plusDays(2).toString();return "{\"startsAt\":\""+day+"T10:00:00Z\",\"endsAt\":\""+day+"T11:00:00Z\"}";}
 @Test void onlyAdminDeletesEmptyResourceAndItsSettings()throws Exception{
  var admin=admin();String id=resource(admin);
  var user=new SessionClient(port);String name="u"+UUID.randomUUID().toString().replace("-","");
  user.call("POST","/api/v1/auth/register","{\"username\":\""+name+"\",\"password\":\"Pass1234\"}");user.login(name,"Pass1234");
  String userId=json.readTree(user.call("GET","/api/v1/auth/me",null).body()).path("id").asText();
  admin.call("PUT","/api/v1/resources/"+id+"/managers/"+userId,null);
  assertThat(user.call("DELETE","/api/v1/resources/"+id,null).statusCode()).isEqualTo(403);
  jdbc.update("insert into resource_schedules values (?, '{}'::jsonb)",UUID.fromString(id));
  assertThat(admin.call("DELETE","/api/v1/resources/"+id,null).statusCode()).isEqualTo(204);
  assertThat(admin.call("GET","/api/v1/resources/"+id,null).statusCode()).isEqualTo(404);
  assertThat(jdbc.queryForObject("select count(*) from resource_managers where resource_id=?",Integer.class,UUID.fromString(id))).isZero();
 }
 @Test void deletionPreservesCancelledBookingHistory()throws Exception{
  var c=admin();String id=resource(c);var booking=c.call("POST","/api/v1/resources/"+id+"/bookings",interval());assertThat(booking.statusCode()).isEqualTo(201);
  String bid=json.readTree(booking.body()).path("id").asText();c.call("POST","/api/v1/bookings/"+bid+"/cancel",null);
  var response=c.call("DELETE","/api/v1/resources/"+id,null);assertThat(response.statusCode()).isEqualTo(409);
  assertThat(json.readTree(response.body()).path("code").asText()).isEqualTo("RESOURCE_IN_USE");
  assertThat(c.call("GET","/api/v1/bookings/"+bid,null).statusCode()).isEqualTo(200);
 }
 @Test void deletionPreservesWaitlistHistory()throws Exception{
  var c=admin();String id=resource(c);UUID owner=UUID.fromString(json.readTree(c.call("GET","/api/v1/auth/me",null).body()).path("id").asText());
  jdbc.update("insert into waitlist_entries(id,resource_id,user_id,starts_at,ends_at,status) values (?,?,?,now()+interval '2 days',now()+interval '2 days 1 hour','CANCELLED')",UUID.randomUUID(),UUID.fromString(id),owner);
  assertThat(c.call("DELETE","/api/v1/resources/"+id,null).statusCode()).isEqualTo(409);
 }
 @Test void deletionAndBookingCreationSerialize()throws Exception{
  var creator=admin();var remover=admin();String id=resource(creator);String body=interval();var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
  try(var executor=Executors.newFixedThreadPool(2)){
   Future<HttpResponse<String>> create=executor.submit(()->{ready.countDown();start.await();return creator.call("POST","/api/v1/resources/"+id+"/bookings",body);});
   Future<HttpResponse<String>> delete=executor.submit(()->{ready.countDown();start.await();return remover.call("DELETE","/api/v1/resources/"+id,null);});
   assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();int created=create.get(30,TimeUnit.SECONDS).statusCode(),deleted=delete.get(30,TimeUnit.SECONDS).statusCode();
   assertThat((created==201&&deleted==409)||(created==404&&deleted==204)).as("create=%s delete=%s",created,deleted).isTrue();
   assertThat(jdbc.queryForObject("select count(*) from bookings b left join resources r on r.id=b.resource_id where r.id is null",Integer.class)).isZero();
  }finally{start.countDown();}
 }
}
