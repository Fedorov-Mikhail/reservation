package com.mikey.reservation.identity;
import java.util.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
@Component
public class AdminBootstrap implements ApplicationRunner {
 private final JdbcTemplate jdbc;private final PasswordEncoder encoder;private final TransactionTemplate transactions;
 private final String username,password;
 public AdminBootstrap(JdbcTemplate jdbc,PasswordEncoder encoder,TransactionTemplate transactions,
 @Value("${app.bootstrap.username:}")String username,@Value("${app.bootstrap.password:}")String password){
 this.jdbc=jdbc;this.encoder=encoder;this.transactions=transactions;this.username=username;this.password=password;
 }
 public void run(ApplicationArguments args) {
   if(username.isBlank()&&password.isBlank())return;
   if(!username.matches("[a-z0-9_-]{3,64}")||password.length()<12||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
     throw new IllegalStateException("Bootstrap admin requires a lowercase username and a password of 12+ characters, at most 72 UTF-8 bytes.");
   transactions.executeWithoutResult(status->{
     jdbc.execute("select pg_advisory_xact_lock(810501)");
     var existing=jdbc.queryForList("select role from app_users where username=?",username);
     if(!existing.isEmpty()){
       if(!existing.getFirst().get("role").equals("ADMIN"))throw new IllegalStateException("Bootstrap username is already registered as a non-admin.");
       return;
     }
     jdbc.update("insert into app_users(id,username,password_hash,role) values (?,?,?,'ADMIN')",UUID.randomUUID(),username,encoder.encode(password));
   });
 }
}

