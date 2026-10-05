package com.mikey.reservation.notifications;
import com.mikey.reservation.identity.CurrentUser;
import com.mikey.reservation.shared.api.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;import java.time.Instant;
@Service
public class NotificationService {
 public record Notice(UUID id,UUID bookingId,String status,Instant createdAt,Instant readAt) {}
 private final JdbcTemplate jdbc;private final CurrentUser current;private final EventDelivery delivery;
 public NotificationService(JdbcTemplate jdbc,CurrentUser current,EventDelivery delivery){this.jdbc=jdbc;this.current=current;this.delivery=delivery;}
 public void deliver(){
  var ids=jdbc.query("select id from outbox_events where notified_at is null and attempts<10 and next_attempt_at<=now() order by created_at,id limit 100",(rs,n)->rs.getObject(1,UUID.class));
  for(var id:ids)try{delivery.deliver(id);}catch(RuntimeException failure){
   jdbc.update("update outbox_events set attempts=attempts+1,next_attempt_at=now()+make_interval(secs=>least(3600,power(2,attempts+1)::int)),last_error='DELIVERY_FAILED' where id=? and notified_at is null",id);
  }
 } @Transactional(readOnly=true)public List<Notice> mine(int page,int size){
  PageResponse.validate(page,size);
  return jdbc.query("select * from notifications where user_id=? order by created_at desc,id limit ? offset ?",(rs,n)->new Notice(rs.getObject("id",UUID.class),rs.getObject("booking_id",UUID.class),rs.getString("status"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("read_at")==null?null:rs.getTimestamp("read_at").toInstant()),current.requiredId(),size,(long)page*size);
 }
 @Transactional public void read(UUID id){
  if(jdbc.update("update notifications set read_at=coalesce(read_at,now()) where id=? and user_id=?",id,current.requiredId())==0)throw ApiException.notFound("NOTIFICATION");
 }
}


