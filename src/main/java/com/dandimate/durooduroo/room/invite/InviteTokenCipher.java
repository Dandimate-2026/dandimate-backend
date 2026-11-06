package com.dandimate.durooduroo.room.invite;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
@Component
public class InviteTokenCipher {
 private final SecretKeySpec key;
 private final SecureRandom random=new SecureRandom();
 public InviteTokenCipher(@Value("${INVITE_TOKEN_SECRET:}") String secret,Environment env) {
  if(secret.isBlank()) {
   if(Arrays.asList(env.getActiveProfiles()).contains("mysql"))throw new IllegalStateException("INVITE_TOKEN_SECRET must be configured");
   secret="local-development-invite-key-not-for-production";
  }
  try {key=new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)),"AES");}
  catch(GeneralSecurityException e){throw new IllegalStateException(e);}
 }
 public String encrypt(String room,String token) {
  try {byte[] iv=new byte[12];random.nextBytes(iv);var cipher=Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));cipher.updateAAD(room.getBytes(StandardCharsets.UTF_8));
   byte[] encrypted=cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
   return Base64.getEncoder().encodeToString(iv)+"."+Base64.getEncoder().encodeToString(encrypted);
  }catch(GeneralSecurityException e){throw new IllegalStateException(e);}
 }
 public String decrypt(String room,String encrypted) {
  try {var parts=encrypted.split("[.]");var cipher=Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])));
   cipher.updateAAD(room.getBytes(StandardCharsets.UTF_8));
   return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])),StandardCharsets.UTF_8);
  }catch(GeneralSecurityException e){throw new IllegalStateException("Invite token cannot be decrypted",e);}
 }
}
