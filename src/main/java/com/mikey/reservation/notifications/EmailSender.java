package com.mikey.reservation.notifications;
public interface EmailSender {
 boolean configured();
 void send(String recipient,String subject,String body);
}
