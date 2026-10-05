package com.mikey.reservation.schedule;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import com.mikey.reservation.identity.CurrentUser;
@RestController @RequestMapping("/api/v1/resources/{id}")
public class ScheduleController {
 private final ScheduleService schedules;private final CurrentUser current;
 public ScheduleController(ScheduleService schedules,CurrentUser current){this.schedules=schedules;this.current=current;}
 @GetMapping("/schedule")public ScheduleService.Definition get(@PathVariable UUID id){return schedules.get(id);}
 @PutMapping("/schedule")public ScheduleService.Definition put(@PathVariable UUID id,@RequestBody ScheduleService.Definition body){return schedules.update(id,body);}
 @GetMapping("/permissions")public Map<String,Boolean> permissions(@PathVariable UUID id){return Map.of("canManage",current.manages(id));}
}

