package com.mikey.reservation.calendar;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import static org.assertj.core.api.Assertions.*;

class GoogleCalendarClientTest {
 HttpServer server;GoogleCalendarClient client;Queue<Integer> responses;List<String> requests;
 @BeforeEach void start() throws Exception {
  responses=new ConcurrentLinkedQueue<>();requests=Collections.synchronizedList(new ArrayList<>());
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",exchange->{String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);requests.add(exchange.getRequestMethod()+" "+exchange.getRequestURI()+" "+body);int status=responses.remove();byte[] content=(exchange.getRequestURI().getPath().equals("/token")?(status==200?"{\"access_token\":\"access\",\"refresh_token\":\"refresh\",\"expires_in\":3600}":"{\"error\":\"invalid_grant\"}"):"{}").getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,status==204?-1:content.length);if(status!=204)exchange.getResponseBody().write(content);exchange.close();});
  server.start();var p=new CalendarProperties();String base="http://127.0.0.1:"+server.getAddress().getPort();p.tokenEndpoint=base+"/token";p.eventsEndpoint=base+"/events";p.clientId="client";p.clientSecret="secret";p.redirectUri=base+"/callback";client=new GoogleCalendarClient(p);
 }
 @AfterEach void stop(){server.stop(0);}
 void sync(boolean cancel){client.sync("access","rabc012",cancel,"Reservation",Instant.parse("2030-01-01T10:00:00Z"),Instant.parse("2030-01-01T11:00:00Z"));}
 @Test void insertConflictAfterLostResponseReusesSameEventId(){responses.addAll(List.of(404,409,200));sync(false);assertThat(requests).hasSize(3);assertThat(requests.get(1)).contains("POST /events", "\"id\":\"rabc012\"");assertThat(requests.get(2)).startsWith("PUT /events/rabc012");}
 @Test void missingRemoteEventIsRecreated(){responses.addAll(List.of(404,200));sync(false);assertThat(requests.get(1)).startsWith("POST /events");}
 @Test void deletedEventTombstoneRequiresNewDurableIdentity(){responses.add(410);assertThatThrownBy(()->sync(false)).isInstanceOf(GoogleCalendarClient.EventTombstone.class);assertThat(requests).hasSize(1);}
 @Test void missingDeleteIsSuccess(){responses.add(404);sync(true);responses.add(410);sync(true);}
 @Test void unauthorizedAndRateLimitAreClassified(){responses.add(401);assertThatThrownBy(()->sync(false)).isInstanceOfSatisfying(GoogleCalendarClient.ProviderFailure.class,e->assertThat(e.reconnect).isTrue());responses.add(429);assertThatThrownBy(()->sync(false)).isInstanceOfSatisfying(GoogleCalendarClient.ProviderFailure.class,e->assertThat(e.retryable()).isTrue());}
 @Test void offlineCodeExchangeAndRefresh(){responses.addAll(List.of(200,200));assertThat(client.exchange("code").refreshToken()).isEqualTo("refresh");assertThat(client.refresh("refresh").accessToken()).isEqualTo("access");assertThat(requests.get(0)).contains("grant_type=authorization_code", "redirect_uri=");assertThat(requests.get(1)).contains("grant_type=refresh_token");}
 @Test void invalidGrantRequiresReconnect(){responses.add(400);assertThatThrownBy(()->client.refresh("revoked")).isInstanceOfSatisfying(GoogleCalendarClient.ProviderFailure.class,e->assertThat(e.reconnect).isTrue());}
 @Test void deterministicEventIdIsGoogleCompatible(){UUID user=UUID.randomUUID(),booking=UUID.randomUUID();String id=GoogleCalendarClient.eventId(user,booking);assertThat(id).matches("[0-9a-v]{5,1024}").isEqualTo(GoogleCalendarClient.eventId(user,booking)).isNotEqualTo(GoogleCalendarClient.eventId(UUID.randomUUID(),booking));}
}
