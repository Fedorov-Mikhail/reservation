package com.mikey.reservation;
import com.mikey.reservation.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;import java.util.concurrent.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"app.bootstrap.username=integration-admin","app.bootstrap.password=Integration-admin-123","app.workers.enabled=false"})
class HoldIT extends PostgresSupport {
 @Value("${local.server.port}")int port;@Autowired JdbcTemplate jdbc;final JsonMapper json=JsonMapper.builder().build();
 @Test void competingHoldsAndExpiration()throws Exception{
  var c=new SessionClient(port);c.login("integration-admin","Integration-admin-123");
  String resource=json.readTree(c.call("POST","/api/v1/resources","{\"name\":\"Hold test\"}").body()).path("id").asText();
  var day=java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(2);
  String body="{\"startsAt\":\""+day+"T10:00:00Z\",\"endsAt\":\""+day+"T11:00:00Z\"}";
  var pool=Executors.newFixedThreadPool(6);List<Future<java.net.http.HttpResponse<String>>> futures=new ArrayList<>();
  var ready=new CountDownLatch(6);var go=new CountDownLatch(1);
  try{
   for(int i=0;i<6;i++)futures.add(pool.submit(()->{ready.countDown();go.await();return c.call("POST","/api/v1/resources/"+resource+"/holds",body);}));
   ready.await(5,TimeUnit.SECONDS);go.countDown();
   int created=0,conflicts=0;String id=null;
   for(var future:futures){var r=future.get(25,TimeUnit.SECONDS);if(r.statusCode()==201){created++;id=json.readTree(r.body()).path("id").asText();}else if(r.statusCode()==409)conflicts++;}
   assertThat(created).isEqualTo(1);assertThat(conflicts).isEqualTo(5);
   jdbc.update("update bookings set expires_at=now()-interval '1 second' where id=?",UUID.fromString(id));
   assertThat(c.call("POST","/api/v1/bookings/"+id+"/confirm",null).statusCode()).isEqualTo(409);
   var replacement=c.call("POST","/api/v1/resources/"+resource+"/holds",body);
   assertThat(replacement.statusCode()).as(replacement.body()).isEqualTo(201);
   String next=json.readTree(replacement.body()).path("id").asText();
   assertThat(c.call("POST","/api/v1/bookings/"+next+"/confirm",null).statusCode()).isEqualTo(200);
   assertThat(c.call("POST","/api/v1/bookings/"+next+"/confirm",null).statusCode()).isEqualTo(200);
   assertThat(jdbc.queryForObject("select count(*) from bookings where resource_id=? and status in ('HELD','CONFIRMED')",Integer.class,UUID.fromString(resource))).isEqualTo(1);
  }finally{pool.shutdownNow();}
 }
}

