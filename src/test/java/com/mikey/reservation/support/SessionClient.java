package com.mikey.reservation.support;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import tools.jackson.databind.json.JsonMapper;
public final class SessionClient {
  private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
  private final String base;
  private final JsonMapper json = JsonMapper.builder().build();
  public SessionClient(int port) { base="http://127.0.0.1:"+port; }
  public HttpResponse<String> call(String method,String path,String body) throws Exception {
    var builder=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(25));
    if(!method.equals("GET")) {
      var csrf=client.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
      builder.header("X-CSRF-TOKEN",json.readTree(csrf.body()).path("token").asText());
    }
    builder.header("Content-Type",path.equals("/api/v1/auth/login")?"application/x-www-form-urlencoded":"application/json");
    return client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
  }
  public HttpResponse<String> login(String name,String password) throws Exception {
    return call("POST","/api/v1/auth/login","username="+URLEncoder.encode(name,java.nio.charset.StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(password,java.nio.charset.StandardCharsets.UTF_8));
  }
}

