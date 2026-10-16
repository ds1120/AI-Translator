package com.aitranslator.config;
import java.time.Duration;
import java.net.URI;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class AppConfig {
 @Bean HttpClient httpClient() { return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(); }
 @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
  return http.csrf(c -> c.ignoringRequestMatchers("/webhooks/line"))
   .authorizeHttpRequests(a -> a.requestMatchers("/webhooks/line", "/actuator/health").permitAll().anyRequest().denyAll())
   .sessionManagement(s -> s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
   .build();
 }
 @Bean Settings settings(Environment e) { return new Settings(e); }
 public static class Settings {
  private final Environment e;
  public Settings(Environment e) {
   this.e=e;
   if (!get("app.mode","mock").matches("mock|live")) throw new IllegalStateException("app.mode must be mock or live");
   if (timeout()<1 || timeout()>20) throw new IllegalStateException("AI timeout must be 1..20 seconds");
   if (!mock()) {
    for (String key : new String[]{"app.line.secret","app.line.token","app.ai.key"})
     if (get(key, "").isBlank()) throw new IllegalStateException("Missing required setting: " + key);
    if (!URI.create(aiBase()).getScheme().equals("https")) throw new IllegalStateException("External AI requires HTTPS");
   }
   if (secret().isBlank()) throw new IllegalStateException("LINE signing secret must not be empty");
  }
  public String get(String key,String fallback){return e.getProperty(key,fallback);}
  public boolean mock(){return get("app.mode","mock").equals("mock");}
  public String secret(){return get("app.line.secret","");}
  public String token(){return get("app.line.token","");}
  public String aiBase(){return get("app.ai.base-url","https://api.openai.com/v1");}
  public String model(){return get("app.ai.model","gpt-4.1-mini");}
  public int timeout(){return Integer.parseInt(get("app.ai.timeout-seconds","15"));}
  public boolean push(){return Boolean.parseBoolean(get("app.line.push-enabled","false"));}
  public String botId(){return get("app.line.bot-user-id","");}
 }
}
