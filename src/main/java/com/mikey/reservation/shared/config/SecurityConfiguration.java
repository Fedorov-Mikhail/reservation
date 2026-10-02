package com.mikey.reservation.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean org.springframework.security.core.userdetails.UserDetailsService localUsers() {
        // Local API does not authenticate users yet; do not generate misleading credentials.
        return new org.springframework.security.provisioning.InMemoryUserDetailsManager();
    }
    @Bean SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        // Local MVP uses no authentication cookies. Add authentication before exposing it remotely.
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/**", "/actuator/health", "/error").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}


