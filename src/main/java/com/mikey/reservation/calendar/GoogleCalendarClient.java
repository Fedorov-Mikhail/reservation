package com.mikey.reservation.calendar;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GoogleCalendarClient {
 public record Tokens(String accessToken,String refreshToken,long expiresIn) {}
 public static class EventTombstone extends ProviderFailure { EventTombstone(){super(410,false);} }
 public static class ProviderFailure extends RuntimeException {
  public final int status; public final boolean reconnect;
  ProviderFailure(int status,boolean reconnect){super("Calendar provider request failed");this.status=status;this.reconnect=reconnect;}
  public boolean retryable(){return status==429||status>=500||status==0;}
 }
 private final CalendarProperties properties;
 private final ObjectMapper json=new ObjectMapper();
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
 public GoogleCalendarClient(CalendarProperties properties){this.properties=properties;}
 public static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
 public Tokens exchange(String code){return token(Map.of("code",code,"grant_type","authorization_code","redirect_uri",properties.redirectUri));}
 public Tokens refresh(String token){return token(Map.of("refresh_token",token,"grant_type","refresh_token"));}
 private Tokens token(Map<String,String> input){
  var values=new java.util.HashMap<>(input);values.put("client_id",properties.clientId);values.put("client_secret",properties.clientSecret);
  String body=values.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(Collectors.joining("&"));
  var response=send(HttpRequest.newBuilder(URI.create(properties.tokenEndpoint)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)));
  if(response.statusCode()!=200)throw new ProviderFailure(response.statusCode(),response.body().contains("invalid_grant"));
  try{JsonNode root=json.readTree(response.body());String access=root.path("access_token").asText();if(access.isBlank())throw new ProviderFailure(502,false);
   return new Tokens(access,root.path("refresh_token").isMissingNode()?null:root.path("refresh_token").asText(),Math.max(1,root.path("expires_in").asLong(3600)));
  }catch(ProviderFailure e){throw e;}catch(Exception e){throw new ProviderFailure(502,false);}
 }
 public static String eventId(java.util.UUID user,java.util.UUID booking){return "r"+user.toString().replace("-","")+booking.toString().replace("-","");}
 public void sync(String token,String eventId,boolean cancelled,String title,java.time.Instant start,java.time.Instant end){
  String base=properties.eventsEndpoint;
  if(cancelled){var result=request("DELETE",base+"/"+eventId,token,null);if(result!=204&&result!=200&&result!=404&&result!=410)throw new ProviderFailure(result,result==401);return;}
  String body;
  try{body=json.writeValueAsString(Map.of("id",eventId,"summary",title,"start",Map.of("dateTime",start.toString()),"end",Map.of("dateTime",end.toString()),"extendedProperties",Map.of("private",Map.of("reservationEventId",eventId))));}catch(Exception e){throw new IllegalStateException(e);}
  int result=request("PUT",base+"/"+eventId,token,body);
  if(result==410)throw new EventTombstone();
  if(result==404){result=request("POST",base,token,body);if(result==409){result=request("PUT",base+"/"+eventId,token,body);}}
  if(result==410)throw new EventTombstone();
  if(result<200||result>=300)throw new ProviderFailure(result,result==401);
 }
 private int request(String method,String url,String token,String body){
  var request=HttpRequest.newBuilder(URI.create(url)).header("Authorization","Bearer "+token).header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
  var response=send(request);if(response.statusCode()==403&&(response.body().contains("rateLimitExceeded")||response.body().contains("userRateLimitExceeded")))return 429;return response.statusCode();
 }
 private HttpResponse<String> send(HttpRequest.Builder request){
  try{return client.send(request.timeout(Duration.ofSeconds(10)).build(),HttpResponse.BodyHandlers.ofString());}
  catch(InterruptedException e){Thread.currentThread().interrupt();throw new ProviderFailure(0,false);}catch(java.io.IOException e){throw new ProviderFailure(0,false);}
 }
}
