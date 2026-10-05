package com.mikey.reservation.waitlist;
import com.mikey.reservation.booking.domain.*;
import com.mikey.reservation.booking.persistence.BookingRepository;
import com.mikey.reservation.identity.CurrentUser;
import com.mikey.reservation.resource.persistence.ResourceRepository;
import com.mikey.reservation.schedule.ScheduleService;
import com.mikey.reservation.shared.api.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;import java.util.*;

@Service
public class WaitlistService {
 public record Entry(UUID id,UUID resourceId,String resourceName,Instant startsAt,Instant endsAt,String status,UUID offeredBookingId) {}
 private final JdbcTemplate jdbc;private final CurrentUser current;private final ResourceRepository resources;private final BookingRepository bookings;private final ScheduleService schedules;private final BookingPolicy policy;private final Clock clock;
 public WaitlistService(JdbcTemplate jdbc,CurrentUser current,ResourceRepository resources,BookingRepository bookings,ScheduleService schedules,BookingPolicy policy,Clock clock){
 this.jdbc=jdbc;this.current=current;this.resources=resources;this.bookings=bookings;this.schedules=schedules;this.policy=policy;this.clock=clock;
 }
 @Transactional public Entry join(UUID resourceId,TimeInterval interval){
  UUID owner=current.requiredId();resources.findLockedById(resourceId).orElseThrow(()->ApiException.notFound("RESOURCE"));
  Instant now=clock.instant();policy.validateCreation(interval,now);schedules.validateBooking(resourceId,interval);
  var prior=jdbc.query("select id from waitlist_entries where user_id=? and resource_id=? and starts_at=? and ends_at=? and status in ('WAITING','OFFERED')",(rs,n)->rs.getObject(1,UUID.class),owner,resourceId,java.sql.Timestamp.from(interval.start()),java.sql.Timestamp.from(interval.end()));
  if(!prior.isEmpty())return entry(prior.getFirst());
  UUID id=UUID.randomUUID();
  jdbc.update("insert into waitlist_entries(id,resource_id,user_id,starts_at,ends_at,status) values (?,?,?,?,?,'WAITING')",id,resourceId,owner,java.sql.Timestamp.from(interval.start()),java.sql.Timestamp.from(interval.end()));
  promoteLocked(resourceId,now);return entry(id);
 }
 @Transactional(readOnly=true)public List<Entry> mine(int page,int size){
  PageResponse.validate(page,size);
  return jdbc.query("select w.*,r.name resource_name from waitlist_entries w join resources r on r.id=w.resource_id where user_id=? order by created_at desc,id limit ? offset ?",this::map,current.requiredId(),size,(long)page*size);
 }
 @Transactional public Entry cancel(UUID id){
  var rows=jdbc.queryForList("select resource_id,user_id from waitlist_entries where id=?",id);
  if(rows.isEmpty())throw ApiException.notFound("WAITLIST");
  current.requireOwner((UUID)rows.getFirst().get("user_id"));
  UUID resourceId=(UUID)rows.getFirst().get("resource_id");resources.findLockedById(resourceId).orElseThrow(()->ApiException.notFound("RESOURCE"));
  Entry existing=entry(id);
  if(existing.status().equals("FULFILLED"))throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,"WAITLIST_FULFILLED","Cancel the confirmed booking instead.");
  jdbc.update("update waitlist_entries set status='CANCELLED' where id=? and status in ('WAITING','OFFERED')",id);
  if(existing.status().equals("OFFERED")){
   var booking=bookings.findLockedById(existing.offeredBookingId()).orElseThrow();
   if(booking.getStatus()==BookingStatus.HELD){booking.expire(clock.instant());if(booking.getStatus()==BookingStatus.HELD){booking.cancel(clock.instant());booking.cancellationActor(current.requiredId(),"Waitlist request cancelled");}bookings.flush();}
  }
  promoteLocked(resourceId,clock.instant());return entry(id);
 }
 // Caller holds the resource row lock; every booking mutation uses the same lock order.
 public void promoteLocked(UUID resourceId,Instant now){
  for(var booking:bookings.findByResourceIdAndStatus(resourceId,BookingStatus.HELD))booking.expire(now);
  bookings.flush();
  jdbc.update("update waitlist_entries w set status=case when b.status='CONFIRMED' then 'FULFILLED' else 'EXPIRED' end from bookings b where w.resource_id=? and w.status='OFFERED' and b.id=w.offered_booking_id and b.status<>'HELD'",resourceId);
  var waiting=jdbc.queryForList("select w.* from waitlist_entries w where resource_id=? and status='WAITING' order by created_at,id for update",resourceId);
  for(var row:waiting){
   UUID id=(UUID)row.get("id"),user=(UUID)row.get("user_id");
   Instant start=((java.sql.Timestamp)row.get("starts_at")).toInstant(),end=((java.sql.Timestamp)row.get("ends_at")).toInstant();
   if(!start.isAfter(now)||!Boolean.TRUE.equals(jdbc.queryForObject("select enabled from app_users where id=?",Boolean.class,user))){
    jdbc.update("update waitlist_entries set status='EXPIRED' where id=?",id);continue;
   }
   var interval=new TimeInterval(start,end);
   try{schedules.validateBooking(resourceId,interval);}catch(ApiException e){jdbc.update("update waitlist_entries set status='EXPIRED' where id=?",id);continue;}
   if(!bookings.occupied(resourceId,start,end).isEmpty())continue;
   var booking=new Booking(resourceId,interval,now);booking.assignOwner(user);booking.hold(now.plusSeconds(300).isBefore(start)?now.plusSeconds(300):start);
   bookings.saveAndFlush(booking);
   jdbc.update("update waitlist_entries set status='OFFERED',offered_booking_id=? where id=?",booking.getId(),id);
  }
 }
 private Entry entry(UUID id){return jdbc.query("select w.*,r.name resource_name from waitlist_entries w join resources r on r.id=w.resource_id where w.id=?",this::map,id).stream().findFirst().orElseThrow(()->ApiException.notFound("WAITLIST"));}
 private Entry map(java.sql.ResultSet rs,int row)throws java.sql.SQLException{return new Entry(rs.getObject("id",UUID.class),rs.getObject("resource_id",UUID.class),rs.getString("resource_name"),rs.getTimestamp("starts_at").toInstant(),rs.getTimestamp("ends_at").toInstant(),rs.getString("status"),rs.getObject("offered_booking_id",UUID.class));}
}

