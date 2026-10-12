package com.mikey.reservation.calendar;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.mikey.reservation.shared.api.ApiException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.time.*;
import java.security.*;
import java.nio.charset.StandardCharsets;

@Service
public class CalendarService {
 public record Status(boolean enabled,boolean connected,boolean reconnectRequired,String status,boolean remoteEventsRetained){}
 private final CalendarProperties properties;private final JdbcTemplate jdbc;private final TokenCipher cipher;private final GoogleCalendarClient provider;private final TransactionTemplate tx;private final Clock clock;
 public CalendarService(CalendarProperties properties,JdbcTemplate jdbc,TokenCipher cipher,GoogleCalendarClient provider,PlatformTransactionManager manager,Clock clock){this.properties=properties;this.jdbc=jdbc;this.cipher=cipher;this.provider=provider;this.tx=new TransactionTemplate(manager);this.clock=clock;}
 public Status status(UUID user){if(!properties.enabled)return new Status(false,false,false,"DISABLED",true);String status=jdbc.query("select status from calendar_connections where user_id=?",(r,n)->r.getString(1),user).stream().findFirst().orElse("DISCONNECTED");return new Status(true,status.equals("CONNECTED"),status.equals("RECONNECT_REQUIRED"),status,true);}
 public String connect(UUID user,String session){
  requireEnabled();byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String state=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  tx.executeWithoutResult(s->{lockUser(user);jdbc.update("delete from calendar_oauth_states where user_id=? or expires_at<now()",user);jdbc.update("insert into calendar_oauth_states values (?,?,?,?)",hash(state),user,hash(session),java.sql.Timestamp.from(clock.instant().plusSeconds(600)));});
  return properties.authorizationEndpoint+"?client_id="+GoogleCalendarClient.encode(properties.clientId)+"&redirect_uri="+GoogleCalendarClient.encode(properties.redirectUri)+"&response_type=code&scope="+GoogleCalendarClient.encode("https://www.googleapis.com/auth/calendar.events.owned")+"&access_type=offline&prompt=consent&state="+state;
 }
 public void callback(UUID user,String session,String code,String state){
  requireEnabled();
  UUID attempt=UUID.randomUUID();
  Boolean consumed=tx.execute(s->{lockUser(user);boolean valid=jdbc.update("delete from calendar_oauth_states where state_hash=? and user_id=? and session_hash=? and expires_at>?",hash(state),user,hash(session),java.sql.Timestamp.from(clock.instant()))==1; if(valid)jdbc.update("insert into calendar_connections(user_id,status,generation) values (?,'DISCONNECTED',?) on conflict(user_id) do update set generation=excluded.generation,updated_at=now()",user,attempt);return valid;});
  if(!Boolean.TRUE.equals(consumed))throw new ApiException(HttpStatus.BAD_REQUEST,"CALENDAR_STATE_INVALID","Calendar authorization expired or was already used.");
  var tokens=provider.exchange(code);if(tokens.refreshToken()==null||tokens.refreshToken().isBlank())throw new ApiException(HttpStatus.BAD_REQUEST,"CALENDAR_OFFLINE_ACCESS_REQUIRED","Grant offline calendar access to connect.");
  Integer saved=tx.execute(s->jdbc.update("update calendar_connections set status='CONNECTED',access_token=?,refresh_token=?,expires_at=?,updated_at=now() where user_id=? and generation=?",cipher.encrypt(user,tokens.accessToken()),cipher.encrypt(user,tokens.refreshToken()),java.sql.Timestamp.from(clock.instant().plusSeconds(tokens.expiresIn())),user,attempt));
  if(saved==null||saved!=1)throw new ApiException(HttpStatus.CONFLICT,"CALENDAR_CONNECTION_CHANGED","Calendar connection changed during authorization. Connect again.");
 }
 public Status disconnect(UUID user){tx.executeWithoutResult(s->{lockUser(user);jdbc.queryForList("select user_id from calendar_connections where user_id=? for update",user);jdbc.update("update calendar_connections set status='DISCONNECTED',access_token=null,refresh_token=null,expires_at=null,generation=?,updated_at=now() where user_id=?",UUID.randomUUID(),user);jdbc.update("delete from calendar_oauth_states where user_id=?",user);jdbc.update("update calendar_sync_jobs set state='CANCELLED',lease_token=null,lease_until=null where user_id=?",user);});return status(user);}
 private void lockUser(UUID user){if(jdbc.queryForList("select id from app_users where id=? for update",user).isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","Account not found.");}
 private void requireEnabled(){if(!properties.enabled)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CALENDAR_DISABLED","Calendar integration is disabled.");if(properties.clientId.isBlank()||properties.clientSecret.isBlank()||properties.redirectUri.isBlank())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CALENDAR_NOT_CONFIGURED","Calendar integration is not configured.");cipher.encrypt(new UUID(0,0),"configuration-check");}
 static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
