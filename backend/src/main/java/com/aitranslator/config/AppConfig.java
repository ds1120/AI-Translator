package com.aitranslator.config;
import java.time.Duration;
import java.util.Set;
import java.net.URI;
import java.util.Locale;
import com.aitranslator.translation.Language;
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
   if(partnerLanguage()==Language.KO) throw new IllegalStateException("Partner language must be TH, VI, EN or JA");
   if (!get("app.mode","mock").matches("mock|live")) throw new IllegalStateException("app.mode must be mock or live");
   if (timeout()<1 || timeout()>20) throw new IllegalStateException("AI timeout must be 1..20 seconds");
   if (!mock()) {
    if (!provider().matches("external|local-qwen")) throw new IllegalStateException("Unknown AI provider");
    for (String key : new String[]{"app.line.secret","app.line.token"})
     if (get(key, "").isBlank()) throw new IllegalStateException("Missing required setting: " + key);
    if (provider().equals("external") && get("app.ai.key", "").isBlank()) throw new IllegalStateException("Missing required setting: app.ai.key");
    URI uri=URI.create(aiBase());
    boolean loopback=Set.of("localhost","127.0.0.1","[::1]","::1").contains(uri.getHost()==null?"":uri.getHost());
    boolean secure="https".equals(uri.getScheme());
    if (uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
     || (!secure && !(provider().equals("local-qwen") && loopback && "http".equals(uri.getScheme()))))
     throw new IllegalStateException("AI requires HTTPS; local Qwen may use loopback HTTP");
   }
   if (secret().isBlank()) throw new IllegalStateException("LINE signing secret must not be empty");
  }
  public String get(String key,String fallback){return e.getProperty(key,fallback);}
  public boolean mock(){return get("app.mode","mock").equals("mock");}
  public Language partnerLanguage(){
   try {return Language.valueOf(get("app.translation.partner-language","TH").strip().toUpperCase(Locale.ROOT));}
   catch(IllegalArgumentException ex){throw new IllegalStateException("Partner language must be TH, VI, EN or JA",ex);}
  }
  public String secret(){return get("app.line.secret","");}
  public String token(){return get("app.line.token","");}
  public String aiBase(){return get("app.ai.base-url","https://api.openai.com/v1");}
  public String provider(){return get("app.ai.provider","external");}
  public String model(){return get("app.ai.model","gpt-4.1-mini");}
  public int timeout(){return Integer.parseInt(get("app.ai.timeout-seconds","15"));}
  public boolean push(){return Boolean.parseBoolean(get("app.line.push-enabled","false"));}
  public String botId(){return get("app.line.bot-user-id","");}
 }
}
