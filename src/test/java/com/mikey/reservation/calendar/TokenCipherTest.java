package com.mikey.reservation.calendar;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class TokenCipherTest {
 @Test void encryptedTokensAreRandomizedAuthenticatedAndBoundToUser(){
  var p=new CalendarProperties();p.encryptionKey=Base64.getEncoder().encodeToString(new byte[32]);
  var cipher=new TokenCipher(p);UUID user=UUID.randomUUID();String token="sensitive-refresh-token";
  String first=cipher.encrypt(user,token),second=cipher.encrypt(user,token);
  assertThat(first).doesNotContain(token).isNotEqualTo(second);assertThat(cipher.decrypt(user,first)).isEqualTo(token);
  assertThatThrownBy(()->cipher.decrypt(UUID.randomUUID(),first)).isInstanceOf(IllegalStateException.class);
  byte[] tampered=Base64.getDecoder().decode(first);tampered[15]^=1;
  assertThatThrownBy(()->cipher.decrypt(user,Base64.getEncoder().encodeToString(tampered))).isInstanceOf(IllegalStateException.class);
 }
 @Test void rejectsNon256BitKey(){var p=new CalendarProperties();p.encryptionKey=Base64.getEncoder().encodeToString(new byte[16]);assertThatThrownBy(()->new TokenCipher(p).encrypt(UUID.randomUUID(),"token")).isInstanceOf(IllegalStateException.class);}
}
