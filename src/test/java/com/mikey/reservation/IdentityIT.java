package com.mikey.reservation;
import com.mikey.reservation.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.*;import java.net.http.*;import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "app.bootstrap.username=integration-admin","app.bootstrap.password=Integration-admin-123"})
class IdentityIT extends PostgresSupport {
 @Value("${local.server.port}") int port;
 @Autowired JdbcTemplate jdbc;
 final JsonMapper json=JsonMapper.builder().build();
 String id(HttpResponse<String> response) {return json.readTree(response.body()).path("id").asText();}
 SessionClient user() throws Exception {
   var c=new SessionClient(port);String name="u"+UUID.randomUUID().toString().replace("-","");
   assertThat(c.call("POST","/api/v1/auth/register","{\"username\":\""+name+"\",\"password\":\"User-password-123\"}").statusCode()).isEqualTo(201);
   assertThat(c.login(name,"User-password-123").statusCode()).isEqualTo(200); return c;
 }
 @Test void authenticationCsrfAndOwnership() throws Exception {
   var anonymous=new SessionClient(port);
   assertThat(anonymous.call("GET","/api/v1/resources",null).statusCode()).isEqualTo(401);
   var admin=new SessionClient(port);
   assertThat(admin.login("integration-admin","wrong").statusCode()).isEqualTo(401);
   assertThat(admin.login("integration-admin","Integration-admin-123").statusCode()).isEqualTo(200);
   String resource=id(admin.call("POST","/api/v1/resources","{\"name\":\"Private booking test\"}"));
   var alice=user();var bob=user();
   assertThat(alice.call("POST","/api/v1/resources","{\"name\":\"Not allowed\"}").statusCode()).isEqualTo(403);
   var day=java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(2);
   String booking=id(alice.call("POST","/api/v1/resources/"+resource+"/bookings","{\"startsAt\":\""+day+"T10:00:00Z\",\"endsAt\":\""+day+"T11:00:00Z\"}"));
   assertThat(bob.call("GET","/api/v1/bookings/"+booking,null).statusCode()).isEqualTo(403);
   assertThat(bob.call("POST","/api/v1/bookings/"+booking+"/cancel",null).statusCode()).isEqualTo(403);
   assertThat(json.readTree(bob.call("GET","/api/v1/resources/"+resource+"/bookings",null).body()).path("totalElements").asInt()).isZero();
   assertThat(alice.call("POST","/api/v1/bookings/"+booking+"/cancel",null).statusCode()).isEqualTo(200);
   assertThat(alice.call("POST","/api/v1/auth/logout",null).statusCode()).isEqualTo(200);
   assertThat(alice.call("GET","/api/v1/auth/me",null).statusCode()).isEqualTo(401);
 }
 @Test void csrfRequiredForRegistration() throws Exception {
   var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/auth/register"))
    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"attack\",\"password\":\"Password-12345\"}")).build(),HttpResponse.BodyHandlers.ofString());
   assertThat(response.statusCode()).isEqualTo(403);
 }
 @Test void disabledAccountAndRoleAssignments() throws Exception {
   var admin=new SessionClient(port);admin.login("integration-admin","Integration-admin-123");
   var user=user();String userId=id(user.call("GET","/api/v1/auth/me",null));
   String resource=id(admin.call("POST","/api/v1/resources","{\"name\":\"Managed resource\"}"));
   assertThat(user.call("PUT","/api/v1/resources/"+resource,"{\"name\":\"Changed\"}").statusCode()).isEqualTo(403);
   assertThat(admin.call("PUT","/api/v1/resources/"+resource+"/managers/"+userId,null).statusCode()).isEqualTo(200);
   assertThat(user.call("PUT","/api/v1/resources/"+resource,"{\"name\":\"Changed\"}").statusCode()).isEqualTo(200);
   assertThat(admin.call("PUT","/api/v1/admin/users/"+userId,"{\"role\":\"USER\",\"enabled\":false}").statusCode()).isEqualTo(200);
   assertThat(user.call("GET","/api/v1/resources",null).statusCode()).isEqualTo(401);
 }
}

