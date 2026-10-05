package com.mikey.reservation.booking.application;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.*;
import java.util.UUID;
@Component @EnableScheduling @ConditionalOnProperty(name="app.workers.enabled",havingValue="true",matchIfMissing=true)
public class ExpiryWorker {
 private final JdbcTemplate jdbc;private final BookingTransactionalService bookings;private static final Logger log=LoggerFactory.getLogger(ExpiryWorker.class);
 public ExpiryWorker(JdbcTemplate jdbc,BookingTransactionalService bookings){this.jdbc=jdbc;this.bookings=bookings;}
 @Scheduled(fixedDelayString="${booking.expiry.interval-ms:10000}")
 public void expire(){
  try{
   var ids=jdbc.query("select distinct resource_id from bookings where status='HELD' and expires_at<=now() limit 100",(rs,n)->rs.getObject(1,UUID.class));
   for(var id:ids)try{bookings.expireResource(id);}catch(RuntimeException e){log.warn("hold_expiry_failed resourceId={} type={}",id,e.getClass().getSimpleName());}
  }catch(RuntimeException e){log.warn("hold_expiry_scan_failed type={}",e.getClass().getSimpleName());}
 }
}

