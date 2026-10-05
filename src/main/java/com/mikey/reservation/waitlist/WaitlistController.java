package com.mikey.reservation.waitlist;
import com.mikey.reservation.booking.api.CreateBookingRequest;
import jakarta.validation.Valid;import java.util.*;
import org.springframework.http.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class WaitlistController {
 private final WaitlistService service;public WaitlistController(WaitlistService service){this.service=service;}
 @PostMapping("/resources/{id}/waitlist") public ResponseEntity<WaitlistService.Entry> join(@PathVariable UUID id,@Valid @RequestBody CreateBookingRequest body){return ResponseEntity.status(201).body(service.join(id,body.interval()));}
 @GetMapping("/me/waitlist")public List<WaitlistService.Entry> mine(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.mine(page,size);}
 @PostMapping("/waitlist/{id}/cancel")public WaitlistService.Entry cancel(@PathVariable UUID id){return service.cancel(id);}
}

