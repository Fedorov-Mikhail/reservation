package com.mikey.reservation.notifications;
import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/me/notifications")
public class NotificationController {
 private final NotificationService service;public NotificationController(NotificationService service){this.service=service;}
 @GetMapping public List<NotificationService.Notice> list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.mine(page,size);}
 @PostMapping("/{id}/read")public Map<String,Boolean> read(@PathVariable UUID id){service.read(id);return Map.of("read",true);}
}

