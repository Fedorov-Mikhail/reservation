package com.mikey.reservation.calendar;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TokenCipher {
 private final CalendarProperties properties;
 private final SecureRandom random=new SecureRandom();
 public TokenCipher(CalendarProperties properties){this.properties=properties;}
 public String encrypt(UUID user,String token){
  if(token==null)return null;
  byte[] nonce=new byte[12];random.nextBytes(nonce);
  byte[] ciphertext=crypt(Cipher.ENCRYPT_MODE,user,nonce,token.getBytes(StandardCharsets.UTF_8));
  byte[] payload=new byte[nonce.length+ciphertext.length];System.arraycopy(nonce,0,payload,0,12);System.arraycopy(ciphertext,0,payload,12,ciphertext.length);
  return Base64.getEncoder().encodeToString(payload);
 }
 public String decrypt(UUID user,String payload){
  byte[] bytes=Base64.getDecoder().decode(payload);
  if(bytes.length<29)throw new IllegalStateException("Invalid encrypted credential");
  return new String(crypt(Cipher.DECRYPT_MODE,user,java.util.Arrays.copyOf(bytes,12),java.util.Arrays.copyOfRange(bytes,12,bytes.length)),StandardCharsets.UTF_8);
 }
 private byte[] crypt(int mode,UUID user,byte[] nonce,byte[] input){
  try {
   byte[] key=Base64.getDecoder().decode(properties.encryptionKey);
   if(key.length!=32)throw new IllegalStateException("Calendar encryption key must contain 32 bytes");
   Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
   cipher.updateAAD(user.toString().getBytes(StandardCharsets.UTF_8));return cipher.doFinal(input);
  }catch(Exception e){throw new IllegalStateException("Calendar credential encryption failed",e);}
 }
}
