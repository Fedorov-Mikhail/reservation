package com.mikey.reservation.shared.config;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import java.io.IOException;
@Configuration
public class SecurityConfiguration {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean UserDetailsService users(JdbcTemplate jdbc){
   return name->jdbc.query("select username,password_hash,role,enabled from app_users where username=?",
    (rs,n)->User.withUsername(rs.getString("username")).password(rs.getString("password_hash"))
       .roles(rs.getString("role")).disabled(!rs.getBoolean("enabled")).build(),
    name.toLowerCase(java.util.Locale.ROOT)).stream().findFirst().orElseThrow(()->new UsernameNotFoundException("Invalid credentials"));
 }
 private static void problem(HttpServletResponse response,int status,String code)throws IOException{
   response.setStatus(status);response.setContentType("application/problem+json");
   response.getWriter().write("{\"status\":"+status+",\"code\":\""+code+"\"}");
 }
 @Bean SecurityFilterChain apiSecurity(HttpSecurity http, com.mikey.reservation.identity.CurrentUser current)throws Exception {
   return http.csrf(csrf->csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository())
       .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
     .authorizeHttpRequests(auth->auth.requestMatchers("/api/v1/auth/csrf","/api/v1/auth/login","/api/v1/auth/register","/actuator/health","/actuator/health/**","/error").permitAll()
       .requestMatchers("/actuator/metrics", "/actuator/metrics/**").access((authentication,context)->{
         try{return new org.springframework.security.authorization.AuthorizationDecision(current.isAdmin());}
         catch(com.mikey.reservation.shared.api.ApiException e){return new org.springframework.security.authorization.AuthorizationDecision(false);}
       })
       .requestMatchers("/api/v1/**").authenticated().anyRequest().denyAll())
     .requestCache(cache->cache.disable())
     .exceptionHandling(errors->errors.authenticationEntryPoint((req,res,e)->problem(res,401,"UNAUTHENTICATED"))
       .accessDeniedHandler((req,res,e)->problem(res,403,"FORBIDDEN")))
     .formLogin(login->login.loginProcessingUrl("/api/v1/auth/login")
       .successHandler((req,res,auth)->{res.setContentType("application/json");res.getWriter().write("{\"authenticated\":true}");})
       .failureHandler((req,res,e)->problem(res,401,"INVALID_CREDENTIALS")))
     .logout(logout->logout.logoutUrl("/api/v1/auth/logout").deleteCookies("JSESSIONID")
       .logoutSuccessHandler((req,res,auth)->{res.setContentType("application/json");res.getWriter().write("{\"authenticated\":false}");}))
     .build();
 }
}
