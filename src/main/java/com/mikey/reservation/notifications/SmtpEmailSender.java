package com.mikey.reservation.notifications;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
@Component
public class SmtpEmailSender implements EmailSender {
 private final ObjectProvider<JavaMailSender> mail; private final boolean enabled; private final String from; private final String host;
 public SmtpEmailSender(ObjectProvider<JavaMailSender> mail,@Value("${app.email.enabled:false}") boolean enabled,
                        @Value("${app.email.from:}") String from,@Value("${spring.mail.host:}") String host){this.mail=mail;this.enabled=enabled;this.from=from;this.host=host;}
 public boolean configured(){return enabled && !host.isBlank() && !from.isBlank() && mail.getIfAvailable()!=null;}
 public void send(String recipient,String subject,String body){
  if(!configured())throw new IllegalStateException("Email is not configured");
  var message=new SimpleMailMessage();message.setFrom(from);message.setTo(recipient);
  message.setSubject(subject);message.setText(body);mail.getObject().send(message);
 }
}
