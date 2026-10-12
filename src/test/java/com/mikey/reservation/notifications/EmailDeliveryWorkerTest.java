package com.mikey.reservation.notifications;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class EmailDeliveryWorkerTest {
 EmailQueue queue=mock(EmailQueue.class);
 FakeSender sender=new FakeSender();
 EmailDeliveryWorker worker=new EmailDeliveryWorker(queue,sender,"https://example.test/");
 EmailQueue.Job job=new EmailQueue.Job(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
  "STATUS","CONFIRMED","Room",Instant.parse("2030-01-01T10:00:00Z"),Instant.parse("2030-01-01T11:00:00Z"),1);
 static class FakeSender implements EmailSender {
  boolean configured=true,fail; int sent; String body;
  public boolean configured(){return configured;}
  public void send(String recipient,String subject,String body){
   if(fail)throw new IllegalStateException("private provider response");
   sent++;this.body=body;
  }
 }
 @Test void disabledProviderNeverClaims(){
  sender.configured=false;worker.deliver();verifyNoInteractions(queue);assertThat(sender.sent).isZero();
 }
 @Test void optOutAfterEnqueueSkipsSend(){
  when(queue.claim()).thenReturn(List.of(job));when(queue.recipient(job)).thenReturn(Optional.empty());
  worker.deliver();verify(queue).complete(job,"SKIPPED");assertThat(sender.sent).isZero();
 }
 @Test void successAndFailureArePersisted(){
  when(queue.claim()).thenReturn(List.of(job));when(queue.recipient(job)).thenReturn(Optional.of("user@example.test"));
  worker.deliver();assertThat(sender.sent).isEqualTo(1);assertThat(sender.body).contains("https://example.test/?booking="+job.bookingId());
  verify(queue).complete(job,"SENT");
  sender.fail=true;worker.deliver();verify(queue).failed(job);
 }
}
