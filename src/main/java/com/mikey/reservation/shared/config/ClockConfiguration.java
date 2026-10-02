package com.mikey.reservation.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration
@EnableConfigurationProperties(BookingPolicyProperties.class)
public class ClockConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }
}

