package com.mikey.reservation.shared.metrics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
@Configuration
public class OperationalMetrics {
 @Bean MeterBinder reservationMetrics(JdbcTemplate jdbc) {
  return registry->{
   gauge(registry,jdbc,"reservation.holds.active","select count(*) from bookings where status='HELD' and expires_at>now()");
   gauge(registry,jdbc,"reservation.holds.expiry.backlog","select count(*) from bookings where status='HELD' and expires_at<=now()");
   gauge(registry,jdbc,"reservation.waitlist.pending","select count(*) from waitlist_entries where status='WAITING'");
   gauge(registry,jdbc,"reservation.outbox.pending","select count(*) from outbox_events where notified_at is null");
   gauge(registry,jdbc,"reservation.email.pending","select count(*) from email_deliveries where state in ('PENDING','PROCESSING')");
   gauge(registry,jdbc,"reservation.email.failed","select count(*) from email_deliveries where state='FAILED'");
   gauge(registry,jdbc,"reservation.calendar.pending","select count(*) from calendar_sync_jobs where state in ('PENDING','LEASED')");
   gauge(registry,jdbc,"reservation.calendar.failed","select count(*) from calendar_sync_jobs where state='FAILED'");
   gauge(registry,jdbc,"reservation.resources.archived","select count(*) from resources where archived_at is not null");
  };
 }
 private static void gauge(io.micrometer.core.instrument.MeterRegistry registry,JdbcTemplate jdbc,String name,String sql){Gauge.builder(name,()->jdbc.queryForObject(sql,Long.class).doubleValue()).register(registry);}
}
