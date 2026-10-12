package com.mikey.reservation.calendar;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("app.calendar")
public class CalendarProperties {
 public boolean enabled=false; public String frontendUrl="http://127.0.0.1:5173"; public String getFrontendUrl(){return frontendUrl;} public void setFrontendUrl(String v){frontendUrl=v.replaceAll("/+$", "");}
 public String clientId="",clientSecret="",redirectUri="",encryptionKey="";
 public String authorizationEndpoint="https://accounts.google.com/o/oauth2/v2/auth";
 public String tokenEndpoint="https://oauth2.googleapis.com/token";
 public String eventsEndpoint="https://www.googleapis.com/calendar/v3/calendars/primary/events";
 public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
 public String getClientId(){return clientId;} public void setClientId(String v){clientId=v;}
 public String getClientSecret(){return clientSecret;} public void setClientSecret(String v){clientSecret=v;}
 public String getRedirectUri(){return redirectUri;} public void setRedirectUri(String v){redirectUri=v;}
 public String getEncryptionKey(){return encryptionKey;} public void setEncryptionKey(String v){encryptionKey=v;}
 public String getAuthorizationEndpoint(){return authorizationEndpoint;} public void setAuthorizationEndpoint(String v){authorizationEndpoint=v;}
 public String getTokenEndpoint(){return tokenEndpoint;} public void setTokenEndpoint(String v){tokenEndpoint=v;}
 public String getEventsEndpoint(){return eventsEndpoint;} public void setEventsEndpoint(String v){eventsEndpoint=v;}
}
