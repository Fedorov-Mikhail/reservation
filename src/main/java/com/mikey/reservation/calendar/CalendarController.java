package com.mikey.reservation.calendar;

import com.mikey.reservation.identity.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/me/calendar")
public class CalendarController {
 private final CalendarService service;private final CurrentUser users;private final CalendarProperties properties;
 public CalendarController(CalendarService service,CurrentUser users,CalendarProperties properties){this.service=service;this.users=users;this.properties=properties;}
 @GetMapping public CalendarService.Status status(){return service.status(users.requiredId());}
 @GetMapping("/connect") public Map<String,String> connect(HttpServletRequest request){return Map.of("authorizationUrl",service.connect(users.requiredId(),request.getSession().getId()));}
 @PostMapping("/disconnect") public CalendarService.Status disconnect(){return service.disconnect(users.requiredId());}
 @GetMapping("/callback") public ResponseEntity<Void> callback(@RequestParam String code,@RequestParam String state,HttpServletRequest request){
  var user=users.requiredId();var session=request.getSession(false);if(session==null)throw CurrentUser.unauthorized();
  service.callback(user,session.getId(),code,state);
  URI frontend=URI.create(properties.frontendUrl);if(frontend.getHost()==null||(!frontend.getScheme().equals("https")&&!frontend.getScheme().equals("http"))||frontend.getRawQuery()!=null||frontend.getRawFragment()!=null)throw new IllegalStateException("Invalid calendar frontend URL");
  return ResponseEntity.status(303).location(URI.create(properties.frontendUrl+"/?calendar=connected")).build();
 }
}
