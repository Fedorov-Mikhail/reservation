package com.mikey.reservation.notifications;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
@Component @ConditionalOnProperty(name="app.workers.enabled",havingValue="true",matchIfMissing=true)
public class EmailDeliveryWorker {
 private final EmailQueue queue; private final EmailSender sender; private final String baseUrl;
 public EmailDeliveryWorker(EmailQueue queue,EmailSender sender,@Value("${app.email.base-url:http://localhost:5173}") String baseUrl){
  this.queue=queue;this.sender=sender;this.baseUrl=baseUrl.replaceAll("/+$","");
 }
 @Scheduled(fixedDelayString="${notifications.email-interval-ms:5000}")
 public void deliver(){
  if(!sender.configured())return;
  queue.reminders();
  for(var job:queue.claim()){
   try{
    var recipient=queue.recipient(job);
    if(recipient.isEmpty()){queue.complete(job,"SKIPPED");continue;}
    sender.send(recipient.get(),job.kind().equals("REMINDER")?"Booking reminder":"Booking "+job.status(),
     job.resourceName()+"\n"+job.startsAt()+" — "+job.endsAt()+"\n"+job.status()+"\n"+baseUrl+"/?booking="+job.bookingId());
    queue.complete(job,"SENT");
   }catch(RuntimeException failure){queue.failed(job);}
  }
 }
}
