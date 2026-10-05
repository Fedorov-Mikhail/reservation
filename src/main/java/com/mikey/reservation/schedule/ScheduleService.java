package com.mikey.reservation.schedule;
import com.mikey.reservation.booking.domain.TimeInterval;
import com.mikey.reservation.identity.CurrentUser;
import com.mikey.reservation.resource.persistence.ResourceRepository;
import com.mikey.reservation.shared.api.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;import java.util.*;

@Service
public class ScheduleService {
 public record Range(int startMinute,int endMinute) {}
 public record Definition(String zoneId,Map<Integer,List<Range>> weekly,Map<LocalDate,List<Range>> exceptions) {}
 private final JdbcTemplate jdbc;private final JsonMapper json;private final CurrentUser current;private final ResourceRepository resources;private final Clock clock;
 public ScheduleService(JdbcTemplate jdbc,JsonMapper json,CurrentUser current,ResourceRepository resources,Clock clock){
  this.jdbc=jdbc;this.json=json;this.current=current;this.resources=resources;this.clock=clock;
 }
 public Definition definition(UUID id) {
  var result=jdbc.query("select definition::text from resource_schedules where resource_id=?",(rs,n)->rs.getString(1),id);
  if(!result.isEmpty())return json.readValue(result.getFirst(),Definition.class);
  Map<Integer,List<Range>> weekly=new TreeMap<>();for(int day=1;day<=7;day++)weekly.put(day,List.of(new Range(0,1440)));
  return new Definition("UTC",weekly,Map.of());
 }
 @Transactional(readOnly=true) public Definition get(UUID id){current.current();requireResource(id);return definition(id);}
 @Transactional public Definition update(UUID id,Definition body){
  current.requireManager(id);resources.findLockedById(id).orElseThrow(()->ApiException.notFound("RESOURCE"));
  validate(body);
  var future=jdbc.query("select starts_at,ends_at from bookings where resource_id=? and status in ('CONFIRMED','HELD') and ends_at>?",
    (rs,n)->new TimeInterval(rs.getTimestamp(1).toInstant(),rs.getTimestamp(2).toInstant()),id,java.sql.Timestamp.from(clock.instant()));
  for(var interval:future)if(!contains(body,interval))throw new ApiException(HttpStatus.CONFLICT,"SCHEDULE_CONFLICT","Existing bookings would be outside this schedule.");
  jdbc.update("insert into resource_schedules(resource_id,definition) values (?,?::jsonb) on conflict(resource_id) do update set definition=excluded.definition",id,json.writeValueAsString(body));
  jdbc.update("update resources set time_zone=? where id=?",body.zoneId(),id);
  return body;
 }
 public void validateBooking(UUID id,TimeInterval interval) {
  if(!contains(definition(id),interval))throw new ApiException(HttpStatus.CONFLICT,"OUTSIDE_SCHEDULE","The complete interval must be inside working hours.");
 }
 private boolean contains(Definition definition,TimeInterval interval){
  return open(definition,interval).stream().anyMatch(i->!i.start().isAfter(interval.start())&&!i.end().isBefore(interval.end()));
 }
 public List<TimeInterval> openIntervals(UUID id,TimeInterval window){return open(definition(id),window);}
 public static List<TimeInterval> open(Definition definition,TimeInterval window){
  ZoneId zone=ZoneId.of(definition.zoneId());List<TimeInterval> candidates=new ArrayList<>();
  LocalDate day=window.start().atZone(zone).toLocalDate(),last=window.end().atZone(zone).toLocalDate();
  for(;!day.isAfter(last);day=day.plusDays(1)){
   var ranges=definition.exceptions().getOrDefault(day,definition.weekly().getOrDefault(day.getDayOfWeek().getValue(),List.of()));
   for(var range:ranges){
    Instant a=day.atStartOfDay().plusMinutes(range.startMinute()).atZone(zone).withEarlierOffsetAtOverlap().toInstant();
    Instant b=day.atStartOfDay().plusMinutes(range.endMinute()).atZone(zone).withLaterOffsetAtOverlap().toInstant();
    if(a.isBefore(window.start()))a=window.start();if(b.isAfter(window.end()))b=window.end();
    if(a.isBefore(b))candidates.add(new TimeInterval(a,b));
   }
  }
  candidates.sort(Comparator.comparing(TimeInterval::start));
  List<TimeInterval> merged=new ArrayList<>();
  for(var next:candidates){
   if(merged.isEmpty()||merged.getLast().end().isBefore(next.start()))merged.add(next);
   else{var previous=merged.removeLast();merged.add(new TimeInterval(previous.start(),previous.end().isAfter(next.end())?previous.end():next.end()));}
  }
  return List.copyOf(merged);
 }
 private void validate(Definition body) {
  if(body==null||body.zoneId()==null||body.weekly()==null||body.exceptions()==null)throw ApiException.invalid("Schedule fields are required.");
  try{ZoneId.of(body.zoneId());}catch(DateTimeException e){throw ApiException.invalid("Unknown time zone.");}
  if(body.weekly().size()>7||body.exceptions().size()>366)throw ApiException.invalid("Too many schedule entries.");
  for(var entry:body.weekly().entrySet()){if(entry.getKey()==null||entry.getKey()<1||entry.getKey()>7)throw ApiException.invalid("Weekday must be 1..7.");validateRanges(entry.getValue());}
  for(var entry:body.exceptions().entrySet()){if(entry.getKey()==null||entry.getKey().getYear()<1||entry.getKey().getYear()>9998)throw ApiException.invalid("Invalid exception date.");validateRanges(entry.getValue());}
 }
 private void validateRanges(List<Range> ranges){
  if(ranges==null||ranges.size()>24)throw ApiException.invalid("At most 24 intervals per day.");
  int end=-1;
  for(var range:ranges){if(range==null||range.startMinute()<0||range.endMinute()>1440||range.startMinute()>=range.endMinute()||range.startMinute()<end)throw ApiException.invalid("Intervals must be ordered, non-overlapping, within 0..1440 minutes.");end=range.endMinute();}
 }
 private void requireResource(UUID id){if(!resources.existsById(id))throw ApiException.notFound("RESOURCE");}
}

