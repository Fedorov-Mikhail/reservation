package com.mikey.reservation.identity;
import com.mikey.reservation.shared.api.*;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

@Service
public class IdentityService {
 public record Registration(@NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{3,64}") String username,
                            @NotBlank @Size(min=12,max=64) String password) {}
 public record UserUpdate(@NotNull @Pattern(regexp="USER|ADMIN") String role,@NotNull Boolean enabled) {}
 private final JdbcTemplate jdbc;private final CurrentUser current;private final PasswordEncoder encoder;
 public IdentityService(JdbcTemplate jdbc,CurrentUser current,PasswordEncoder encoder){this.jdbc=jdbc;this.current=current;this.encoder=encoder;}
 public CurrentUser.Account register(Registration body) {
   if(body.password().getBytes(StandardCharsets.UTF_8).length>72)throw ApiException.invalid("Password exceeds 72 UTF-8 bytes.");
   UUID id=UUID.randomUUID();String name=body.username().toLowerCase(Locale.ROOT);
   try {jdbc.update("insert into app_users(id,username,password_hash,role) values (?,?,?,'USER')",id,name,encoder.encode(body.password()));}
   catch(DuplicateKeyException ex){throw new ApiException(HttpStatus.CONFLICT,"USERNAME_TAKEN","Username is already registered.");}
   return new CurrentUser.Account(id,name,"USER",true);
 }
 public List<CurrentUser.Account> users(int page,int size) {
   current.requireAdmin();PageResponse.validate(page,size);
   return jdbc.query("select id,username,role,enabled from app_users order by username limit ? offset ?",
     (rs,n)->new CurrentUser.Account(rs.getObject("id",UUID.class),rs.getString("username"),rs.getString("role"),rs.getBoolean("enabled")),size,(long)page*size);
 }
 @Transactional public void update(UUID id,UserUpdate update) {
   current.requireAdmin();
   jdbc.execute("select pg_advisory_xact_lock(810501)");
   var old=jdbc.queryForList("select role,enabled from app_users where id=? for update",id);
   if(old.isEmpty()) throw ApiException.notFound("USER");
   if(id.equals(UUID.fromString("00000000-0000-0000-0000-000000000001")))throw CurrentUser.forbidden();
   if(old.getFirst().get("role").equals("ADMIN") && Boolean.TRUE.equals(old.getFirst().get("enabled"))
       && (!update.enabled()||!update.role().equals("ADMIN"))
       && jdbc.queryForObject("select count(*) from app_users where role='ADMIN' and enabled",Integer.class)<=1)
     throw new ApiException(HttpStatus.CONFLICT,"LAST_ADMIN","At least one active administrator is required.");
   jdbc.update("update app_users set role=?,enabled=? where id=?",update.role(),update.enabled(),id);
 }
 @Transactional public void manager(UUID resource,UUID user,boolean assign) {
   current.requireAdmin();
   if(jdbc.queryForObject("select count(*) from resources where id=?",Integer.class,resource)==0)throw ApiException.notFound("RESOURCE");
   if(jdbc.queryForObject("select count(*) from app_users where id=? and enabled",Integer.class,user)==0)throw ApiException.notFound("USER");
   if(assign)jdbc.update("insert into resource_managers values (?,?) on conflict do nothing",resource,user);
   else jdbc.update("delete from resource_managers where resource_id=? and user_id=?",resource,user);
 }
}

