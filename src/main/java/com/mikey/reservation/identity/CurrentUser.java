package com.mikey.reservation.identity;
import com.mikey.reservation.shared.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class CurrentUser {
 public record Account(UUID id,String username,String role,boolean enabled) {}
 private final JdbcTemplate jdbc;
 public CurrentUser(JdbcTemplate jdbc) {this.jdbc=jdbc;}
 public Account current() {
   var auth=SecurityContextHolder.getContext().getAuthentication();
   if(auth==null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) throw unauthorized();
   return jdbc.query("select id,username,role,enabled from app_users where username=? and enabled",
     (rs,n)->new Account(rs.getObject("id",UUID.class),rs.getString("username"),rs.getString("role"),rs.getBoolean("enabled")),
     auth.getName()).stream().findFirst().orElseThrow(CurrentUser::unauthorized);
 }
 public UUID requiredId(){return current().id();}
 public boolean isAdmin(){return current().role().equals("ADMIN");}
 public void requireAdmin(){if(!isAdmin()) throw forbidden();}
 public boolean manages(UUID resourceId) {
   var account=current();
   return account.role().equals("ADMIN") || Boolean.TRUE.equals(jdbc.queryForObject(
     "select exists(select 1 from resource_managers where resource_id=? and user_id=?)",Boolean.class,resourceId,account.id()));
 }
 public void requireManager(UUID resourceId){if(!manages(resourceId)) throw forbidden();}
 public void requireOwner(UUID owner){var account=current();if(!account.id().equals(owner)&&!account.role().equals("ADMIN"))throw forbidden();}
 public static ApiException forbidden(){return new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Operation is not permitted.");}
 public static ApiException unauthorized(){return new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","Sign in to continue.");}
}

