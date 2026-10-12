package com.mikey.reservation.calendar;

import com.mikey.reservation.support.PostgresSupport;
import com.mikey.reservation.shared.api.ApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"app.calendar.enabled=false","app.workers.enabled=false"})
class CalendarIntegrationIT extends PostgresSupport {
 @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager manager;
 HttpServer server;CalendarProperties properties;CalendarService service;CalendarSyncWorker worker;TokenCipher cipher;
 UUID user,booking;Queue<Integer> responses;List<String> requests;AtomicInteger exchanges;
 @BeforeEach void setup() throws Exception {
  responses=new ConcurrentLinkedQueue<>();requests=Collections.synchronizedList(new ArrayList<>());exchanges=new AtomicInteger();
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",e->{String body=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);requests.add(e.getRequestMethod()+" "+e.getRequestURI()+" "+e.getRequestHeaders().getFirst("Authorization")+" "+body);boolean token=e.getRequestURI().getPath().equals("/token");if(token)exchanges.incrementAndGet();Integer queued=responses.poll();int status=queued==null?200:queued;String response=token?(status==200?"{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\",\"expires_in\":3600}":"{\"error\":\"invalid_grant\"}"):"{}";byte[] bytes=response.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(status,status==204?-1:bytes.length);if(status!=204)e.getResponseBody().write(bytes);e.close();});server.start();
  properties=new CalendarProperties();properties.enabled=true;properties.clientId="local-test";properties.clientSecret="local-secret";properties.redirectUri="http://127.0.0.1/callback";properties.encryptionKey=Base64.getEncoder().encodeToString(new byte[32]);String base="http://127.0.0.1:"+server.getAddress().getPort();properties.tokenEndpoint=base+"/token";properties.eventsEndpoint=base+"/events";
  cipher=new TokenCipher(properties);var provider=new GoogleCalendarClient(properties);service=new CalendarService(properties,jdbc,cipher,provider,manager,Clock.systemUTC());worker=new CalendarSyncWorker(jdbc,manager,cipher,provider,Clock.systemUTC());
  user=UUID.randomUUID();UUID resource=UUID.randomUUID();booking=UUID.randomUUID();jdbc.update("insert into app_users(id,username,password_hash,role) values (?,?,?,'USER')",user,"calendar-"+user,"disabled");jdbc.update("insert into resources(id,name,created_at,updated_at,created_by) values (?,'Calendar test',now(),now(),?)",resource,user);jdbc.update("insert into bookings(id,resource_id,created_by,starts_at,ends_at,status,created_at,confirmed_at) values (?,?,?,now()+interval '1 day',now()+interval '1 day 1 hour','CONFIRMED',now(),now())",booking,resource,user);
 }
 @AfterEach void cleanup(){server.stop(0);jdbc.update("delete from calendar_event_mappings where booking_id=?",booking);jdbc.update("delete from calendar_sync_jobs where user_id=?",user);jdbc.update("delete from calendar_oauth_states where user_id=?",user);jdbc.update("delete from calendar_connections where user_id=?",user);}
 String state(String session){String url=service.connect(user,session);assertThat(url).contains("access_type=offline","prompt=consent");return url.substring(url.indexOf("&state=")+7);}
 void connect(){service.callback(user,"session","code",state("session"));requests.clear();exchanges.set(0);}
 void deliver(){worker.scan();CalendarSyncWorker.Job job;while((job=worker.claim())!=null){worker.deliver(job);}}
 @Test void stateRejectsWrongSessionReplayAndExpiredStateBeforeTokenExchange(){
  String state=state("session");assertThatThrownBy(()->service.callback(user,"wrong","code",state)).isInstanceOf(ApiException.class);assertThat(exchanges).hasValue(0);service.callback(user,"session","code",state);assertThat(exchanges).hasValue(1);assertThatThrownBy(()->service.callback(user,"session","code",state)).isInstanceOf(ApiException.class);assertThat(exchanges).hasValue(1);
  String expired=state("session");jdbc.update("update calendar_oauth_states set expires_at=now()-interval '1 second' where user_id=?",user);assertThatThrownBy(()->service.callback(user,"session","code",expired)).isInstanceOf(ApiException.class);assertThat(exchanges).hasValue(1);
 }
 @Test void stateRejectsDifferentUserAndConsumesEvenFailedExchange(){
  String state=state("session");assertThatThrownBy(()->service.callback(UUID.randomUUID(),"session","code",state)).isInstanceOf(ApiException.class);assertThat(exchanges).hasValue(0);responses.add(400);assertThatThrownBy(()->service.callback(user,"session","code",state)).isInstanceOf(GoogleCalendarClient.ProviderFailure.class);assertThatThrownBy(()->service.callback(user,"session","code",state)).isInstanceOf(ApiException.class);assertThat(exchanges).hasValue(1);
 }
 @Test void callbackStoresCiphertextAndStatusNeverContainsTokens(){connect();var row=jdbc.queryForMap("select * from calendar_connections where user_id=?",user);assertThat(row.get("access_token").toString()).doesNotContain("new-access");assertThat(cipher.decrypt(user,row.get("refresh_token").toString())).isEqualTo("new-refresh");assertThat(service.status(user).toString()).doesNotContain("new-access","new-refresh");}
 @Test void unauthorizedEventRefreshesOnceAndCompletes(){connect();responses.addAll(List.of(401,200,200));deliver();assertThat(exchanges).hasValue(1);assertThat(requests.getFirst()).contains("Bearer new-access");assertThat(jdbc.queryForObject("select state from calendar_sync_jobs where booking_id=?",String.class,booking)).isEqualTo("DONE");}
 @Test void rateLimitRetriesDurablyWithoutDuplicateInsertAndBoundedAttempts(){connect();responses.add(429);deliver();assertThat(jdbc.queryForObject("select state from calendar_sync_jobs where booking_id=?",String.class,booking)).isEqualTo("PENDING");assertThat(jdbc.queryForObject("select attempts from calendar_sync_jobs where booking_id=?",Integer.class,booking)).isEqualTo(1);jdbc.update("update calendar_sync_jobs set next_attempt_at=now()-interval '1 second',attempts=5 where booking_id=?",booking);responses.add(429);deliver();assertThat(jdbc.queryForObject("select state from calendar_sync_jobs where booking_id=?",String.class,booking)).isEqualTo("FAILED");int calls=requests.size();deliver();assertThat(requests).hasSize(calls);}
 @Test void duplicateScanAndCancellationKeepStableIdentity(){connect();responses.addAll(List.of(404,200));deliver();String id=GoogleCalendarClient.eventId(user,booking);assertThat(requests.get(1)).contains("\"id\":\""+id+"\"");int calls=requests.size();deliver();assertThat(requests).hasSize(calls);jdbc.update("update bookings set status='CANCELLED',cancelled_at=now(),cancelled_by=? where id=?",user,booking);responses.add(410);deliver();assertThat(requests.getLast()).startsWith("DELETE /events/"+id);assertThat(jdbc.queryForObject("select state from calendar_sync_jobs where booking_id=?",String.class,booking)).isEqualTo("DONE");}
 @Test void deletedEventRecreationPersistsRevisionAcrossLostResponse(){
  connect();responses.add(200);deliver();requests.clear();jdbc.update("update calendar_sync_jobs set updated_at=now()-interval '7 hours' where booking_id=?",booking);responses.addAll(List.of(410,404,503));deliver();String replacement=GoogleCalendarClient.eventId(user,booking)+"g1";
  assertThat(jdbc.queryForObject("select event_revision from calendar_event_mappings where booking_id=?",Integer.class,booking)).isEqualTo(1);
  assertThat(requests.getLast()).contains("POST /events", "\"id\":\""+replacement+"\"");
  assertThat(jdbc.queryForObject("select state from calendar_sync_jobs where booking_id=?",String.class,booking)).isEqualTo("PENDING");
  jdbc.update("update calendar_sync_jobs set next_attempt_at=now()-interval '1 second' where booking_id=?",booking);responses.add(200);deliver();assertThat(requests.getLast()).startsWith("PUT /events/"+replacement);
  assertThat(jdbc.queryForObject("select state from calendar_sync_jobs where booking_id=?",String.class,booking)).isEqualTo("DONE");
 }
 @Test void revokedRefreshMarksReconnectAndDisconnectErasesTokensCancelsJobs(){connect();jdbc.update("update calendar_connections set expires_at=now()-interval '1 second' where user_id=?",user);responses.add(400);deliver();assertThat(service.status(user).reconnectRequired()).isTrue();assertThat(jdbc.queryForMap("select access_token,refresh_token from calendar_connections where user_id=?",user).values()).containsOnlyNulls();assertThat(service.disconnect(user).connected()).isFalse();assertThat(jdbc.queryForObject("select state from calendar_sync_jobs where booking_id=?",String.class,booking)).isEqualTo("CANCELLED");int calls=requests.size();deliver();assertThat(requests).hasSize(calls);}
 @Test void claimIsExclusiveAndExpiredLeaseIsRecoverable(){connect();worker.scan();var first=worker.claim();assertThat(first).isNotNull();assertThat(worker.claim()).isNull();jdbc.update("update calendar_sync_jobs set lease_until=now()-interval '1 second' where booking_id=?",booking);var recovered=worker.claim();assertThat(recovered.lease()).isNotEqualTo(first.lease());worker.deliver(first);assertThat(requests).isEmpty();responses.add(200);worker.deliver(recovered);assertThat(jdbc.queryForObject("select state from calendar_sync_jobs where booking_id=?",String.class,booking)).isEqualTo("DONE");}

 @Test void disconnectDuringFirstConnectionInsertInvalidatesLateTokens() throws Exception {
  String state=state("session");CountDownLatch atInsert=new CountDownLatch(1),allowInsert=new CountDownLatch(1),atProvider=new CountDownLatch(1),allowProvider=new CountDownLatch(1);AtomicInteger disconnectPid=new AtomicInteger();
  JdbcTemplate probe=new JdbcTemplate(jdbc.getDataSource()){
   @Override public int update(String sql,Object...args){if(sql.startsWith("insert into calendar_connections")&&Thread.currentThread().getName().equals("callback-test")){atInsert.countDown();try{if(!allowInsert.await(4,TimeUnit.SECONDS))throw new IllegalStateException("insert timed out");}catch(InterruptedException e){throw new IllegalStateException(e);}}return super.update(sql,args);}
   @Override public List<Map<String,Object>> queryForList(String sql,Object...args){if(Thread.currentThread().getName().equals("disconnect-test")&&disconnectPid.get()==0)disconnectPid.set(super.queryForObject("select pg_backend_pid()",Integer.class));return super.queryForList(sql,args);}
  };
  var raced=new CalendarService(properties,probe,cipher,new GoogleCalendarClient(properties),manager,Clock.systemUTC());
  server.removeContext("/");server.createContext("/",e->{e.getRequestBody().readAllBytes();atProvider.countDown();try{allowProvider.await(5,TimeUnit.SECONDS);}catch(InterruptedException x){Thread.currentThread().interrupt();}byte[] bytes="{\"access_token\":\"late\",\"refresh_token\":\"late-refresh\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();});
  try(var executor=Executors.newFixedThreadPool(2)){
   var callback=executor.submit(()->{Thread.currentThread().setName("callback-test");raced.callback(user,"session","code",state);});
   assertThat(atInsert.await(3,TimeUnit.SECONDS)).isTrue();var disconnect=executor.submit(()->{Thread.currentThread().setName("disconnect-test");raced.disconnect(user);});
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);boolean blocked=false;while(System.nanoTime()<deadline){if(disconnectPid.get()!=0&&Boolean.TRUE.equals(jdbc.queryForObject("select cardinality(pg_blocking_pids(?))>0",Boolean.class,disconnectPid.get()))){blocked=true;break;}Thread.sleep(10);}assertThat(blocked).isTrue();
   allowInsert.countDown();assertThat(atProvider.await(3,TimeUnit.SECONDS)).isTrue();disconnect.get(3,TimeUnit.SECONDS);allowProvider.countDown();
   assertThatThrownBy(()->callback.get(5,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ApiException.class);
   assertThat(raced.status(user).connected()).isFalse();
  }finally{allowInsert.countDown();allowProvider.countDown();}
 }
 @Test void disconnectPreventsInFlightCallbackFromRestoringTokens() throws Exception {
  String state=state("session");server.removeContext("/");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);server.createContext("/",e->{e.getRequestBody().readAllBytes();entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException x){Thread.currentThread().interrupt();}byte[] bytes="{\"access_token\":\"late\",\"refresh_token\":\"late-refresh\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();});
  try(var executor=Executors.newSingleThreadExecutor()){Future<?> callback=executor.submit(()->service.callback(user,"session","code",state));assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();service.disconnect(user);release.countDown();assertThatThrownBy(()->callback.get(5,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ApiException.class);assertThat(service.status(user).connected()).isFalse();assertThat(jdbc.queryForMap("select access_token,refresh_token from calendar_connections where user_id=?",user).values()).containsOnlyNulls();}finally{release.countDown();}
 }
}
