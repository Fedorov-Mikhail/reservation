package com.mikey.reservation.notifications;
import org.springframework.stereotype.Component;import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.*;
@Component @ConditionalOnProperty(name="app.workers.enabled",havingValue="true",matchIfMissing=true)
public class NotificationWorker {
 private final NotificationService service;private static final Logger log=LoggerFactory.getLogger(NotificationWorker.class);
 public NotificationWorker(NotificationService service){this.service=service;}
 @Scheduled(fixedDelayString="${notifications.interval-ms:3000}")public void deliver(){try{service.deliver();}catch(RuntimeException e){log.warn("notification_delivery_failed type={}",e.getClass().getSimpleName());}}
}

