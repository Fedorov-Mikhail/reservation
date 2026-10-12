package com.mikey.reservation.notifications;
import java.util.*; import java.time.Instant;
import com.mikey.reservation.identity.CurrentUser;
import com.mikey.reservation.shared.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/admin/email-deliveries")
public class EmailAdminController {
 public record Delivery(UUID id,UUID bookingId,String kind,String state,int attempts,String lastError,Instant createdAt,Instant sentAt) {}
 private final JdbcTemplate jdbc; private final CurrentUser current;
 public EmailAdminController(JdbcTemplate jdbc,CurrentUser current){this.jdbc=jdbc;this.current=current;}
 @GetMapping public List<Delivery> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
  current.requireAdmin();PageResponse.validate(page,size);
  return jdbc.query("select * from email_deliveries order by created_at desc,id limit ? offset ?",
   (rs,n)->new Delivery(rs.getObject("id",UUID.class),rs.getObject("booking_id",UUID.class),rs.getString("kind"),rs.getString("state"),
    rs.getInt("attempts"),rs.getString("last_error"),rs.getTimestamp("created_at").toInstant(),
    rs.getTimestamp("sent_at")==null?null:rs.getTimestamp("sent_at").toInstant()),size,(long)page*size);
 }
 @PostMapping("/{id}/retry") public Map<String,Boolean> retry(@PathVariable UUID id){
  current.requireAdmin();
  if(jdbc.update("update email_deliveries set state='PENDING',attempts=0,available_at=now(),last_error=null where id=? and state='FAILED'",id)==0)
   throw ApiException.invalid("Only failed email deliveries can be retried.");
  return Map.of("queued",true);
 }
}
