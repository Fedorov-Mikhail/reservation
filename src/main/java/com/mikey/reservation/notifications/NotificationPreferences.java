package com.mikey.reservation.notifications;

import com.mikey.reservation.identity.CurrentUser;
import com.mikey.reservation.shared.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Service
public class NotificationPreferences {
 public record Preferences(@NotNull @Email @Size(max=254) String email,
                           @NotNull Boolean emailEnabled,
                           @NotNull @Min(1) @Max(10080) Integer reminderMinutes) {}
 public record Response(String email,boolean emailEnabled,int reminderMinutes,boolean providerEnabled) {}
 private final JdbcTemplate jdbc; private final CurrentUser current; private final EmailSender sender;
 public NotificationPreferences(JdbcTemplate jdbc,CurrentUser current,EmailSender sender){this.jdbc=jdbc;this.current=current;this.sender=sender;}
 public Response mine(){
  var p=jdbc.query("select email,email_enabled,reminder_minutes from notification_preferences where user_id=?",
   (rs,n)->new Preferences(rs.getString(1),rs.getBoolean(2),rs.getInt(3)),current.requiredId()).stream()
   .findFirst().orElse(new Preferences("",false,30));
  return new Response(p.email(),p.emailEnabled(),p.reminderMinutes(),sender.configured());
 }
 @Transactional public Response save(Preferences request){
  if(request.emailEnabled() && request.email().isBlank())throw ApiException.invalid("Email is required when email notifications are enabled.");
  var saved=new Preferences(request.email().trim(),request.emailEnabled(),request.reminderMinutes());
  jdbc.update("insert into notification_preferences(user_id,email,email_enabled,reminder_minutes) values (?,?,?,?) on conflict(user_id) do update set email=excluded.email,email_enabled=excluded.email_enabled,reminder_minutes=excluded.reminder_minutes",
   current.requiredId(),saved.email(),saved.emailEnabled(),saved.reminderMinutes());
  return new Response(saved.email(),saved.emailEnabled(),saved.reminderMinutes(),sender.configured());
 }
}
@RestController @RequestMapping("/api/v1/me/notification-preferences")
class NotificationPreferencesController {
 private final NotificationPreferences service;
 NotificationPreferencesController(NotificationPreferences service){this.service=service;}
 @GetMapping NotificationPreferences.Response get(){return service.mine();}
 @PutMapping NotificationPreferences.Response put(@Valid @RequestBody NotificationPreferences.Preferences request){return service.save(request);}
}
