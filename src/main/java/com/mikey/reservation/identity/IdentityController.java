package com.mikey.reservation.identity;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.*;
@RestController @RequestMapping("/api/v1")
public class IdentityController {
 private final IdentityService service;private final CurrentUser current;
 public IdentityController(IdentityService service,CurrentUser current){this.service=service;this.current=current;}
 @GetMapping("/auth/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken());}
 @GetMapping("/auth/me") public CurrentUser.Account me(){return current.current();}
 @PostMapping("/auth/register") public ResponseEntity<CurrentUser.Account> register(@Valid @RequestBody IdentityService.Registration body){
   return ResponseEntity.status(HttpStatus.CREATED).body(service.register(body));
 }
 @GetMapping("/admin/users") public List<CurrentUser.Account> users(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.users(page,size);}
 @PutMapping("/admin/users/{id}") public Map<String,Boolean> update(@PathVariable UUID id,@Valid @RequestBody IdentityService.UserUpdate body){service.update(id,body);return Map.of("updated",true);}
 @PutMapping("/resources/{resource}/managers/{user}") public Map<String,Boolean> assign(@PathVariable UUID resource,@PathVariable UUID user){service.manager(resource,user,true);return Map.of("assigned",true);}
 @DeleteMapping("/resources/{resource}/managers/{user}") public Map<String,Boolean> revoke(@PathVariable UUID resource,@PathVariable UUID user){service.manager(resource,user,false);return Map.of("assigned",false);}
}

