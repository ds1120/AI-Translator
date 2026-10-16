package com.aitranslator.line;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import com.aitranslator.config.AppConfig.Settings;
import org.springframework.stereotype.Component;
@Component
public class SignatureVerifier {
 private final Settings settings;
 public SignatureVerifier(Settings settings){this.settings=settings;}
 public boolean valid(byte[] body,String signature){
  if(signature==null) return false;
  try {
   Mac mac=Mac.getInstance("HmacSHA256");
   mac.init(new SecretKeySpec(settings.secret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
   return MessageDigest.isEqual(mac.doFinal(body),Base64.getDecoder().decode(signature));
  } catch(IllegalArgumentException ex){return false;}
    catch(Exception ex){throw new IllegalStateException("Signature verification unavailable",ex);}
 }
}
