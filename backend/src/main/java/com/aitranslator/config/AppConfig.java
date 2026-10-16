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
 @Bean @org.springframework.core.annotation.Order(2) SecurityFilterChain security(HttpSecurity http) throws Exception {
  return http.csrf(c -> c.ignoringRequestMatchers("/webhooks/line"))
   .authorizeHttpRequests(a -> a.requestMatchers("/webhooks/line", "/actuator/health").permitAll().anyRequest().denyAll())
   .sessionManagement(s -> s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
   .build();
 }
 @Bean Settings settings(Environment e) { return new Settings(e); }
 public static class Settings {
  private final Environment e;
  private final java.util.concurrent.locks.ReentrantReadWriteLock configurationLock=new java.util.concurrent.locks.ReentrantReadWriteLock();
  public Settings(Environment e) {
   this.e=e;
   if(!Set.of(Language.TH,Language.VI,Language.EN,Language.JA).contains(partnerLanguage())) throw new IllegalStateException("Partner language must be TH, VI, EN or JA");
   if (!get("app.mode","mock").matches("mock|live")) throw new IllegalStateException("app.mode must be mock or live");
   if (timeout()<1 || timeout()>20) throw new IllegalStateException("AI timeout must be 1..20 seconds");
   if(qualityBudgetMillis()<1000 || qualityBudgetMillis()>35000)throw new IllegalStateException("Quality budget must be 1000..35000 ms");
   if(!Set.of(0,5,10).contains(contextLimit()) || contextTtlSeconds()<1 || contextTtlSeconds()>3600)throw new IllegalStateException("Invalid context settings");
   if (!mock()) {
    if (!provider().matches("external|local-qwen|openai|gemini")) throw new IllegalStateException("Unknown AI provider");
    for (String key : new String[]{"app.line.secret","app.line.token"})
     if (get(key, "").isBlank()) throw new IllegalStateException("Missing required setting: " + key);
    if (provider().equals("external") && get("app.ai.key", "").isBlank()) throw new IllegalStateException("Missing required setting: app.ai.key");
    if (Set.of("openai","gemini").contains(provider()) && aiKey().isBlank()) throw new IllegalStateException("Missing required setting: app.ai."+provider()+".key");
    validateNamedEndpoint(provider(),aiBase());
    URI uri=URI.create(aiBase());
    boolean loopback=Set.of("localhost","127.0.0.1","[::1]","::1").contains(uri.getHost()==null?"":uri.getHost());
    boolean secure="https".equals(uri.getScheme());
    if (uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
     || (!secure && !(provider().equals("local-qwen") && loopback && "http".equals(uri.getScheme()))))
     throw new IllegalStateException("AI requires HTTPS; local Qwen may use loopback HTTP");
    if(verificationEnabled()){
     if(!verificationProvider().matches("external|local-qwen|openai|gemini"))throw new IllegalStateException("Unknown verification provider");
     validateNamedEndpoint(verificationProvider(),verificationBase());
     URI verification=URI.create(verificationBase());
     boolean local=Set.of("localhost","127.0.0.1","[::1]","::1").contains(verification.getHost()==null?"":verification.getHost());
     if(verification.getHost()==null || verification.getUserInfo()!=null || verification.getQuery()!=null || verification.getFragment()!=null
      || !("https".equals(verification.getScheme()) || (verificationQwen() && local && "http".equals(verification.getScheme()))))throw new IllegalStateException("Verification requires HTTPS or local Qwen loopback HTTP");
     if(!verificationQwen() && verificationKey().isBlank())throw new IllegalStateException("Missing verification API key");
    }
   }
   if (secret().isBlank()) throw new IllegalStateException("LINE signing secret must not be empty");
  }
  public String get(String key,String fallback){return e.getProperty(key,fallback);}
  public java.util.concurrent.locks.ReentrantReadWriteLock configurationLock(){return configurationLock;}
  public boolean mock(){return get("app.mode","mock").equals("mock");}
  public Language partnerLanguage(){
   try {return Language.valueOf(get("app.translation.partner-language","TH").strip().toUpperCase(Locale.ROOT));}
   catch(IllegalArgumentException ex){throw new IllegalStateException("Partner language must be TH, VI, EN or JA",ex);}
  }
  public String secret(){return get("app.line.secret","");}
  public String token(){return get("app.line.token","");}
  public static String defaultBase(String provider){return provider.equals("gemini")?"https://generativelanguage.googleapis.com/v1beta/openai":"https://api.openai.com/v1";}
  public static String defaultModel(String provider){return provider.equals("gemini")?"gemini-3.8-flash":"gpt-4.1-mini";}
  private void validateNamedEndpoint(String provider,String base){if(Set.of("openai","gemini").contains(provider) && !base.replaceAll("/$","").equals(defaultBase(provider)))throw new IllegalStateException("Named provider must use its official API endpoint");}
  public String aiBase(){return get("app.ai.base-url",defaultBase(provider()));}
  public String keyFor(String provider){return Set.of("openai","gemini").contains(provider)?get("app.ai."+provider+".key",""):get("app.ai.key","");}
  public String aiKey(){return keyFor(provider());}
  public String provider(){return get("app.ai.provider","external");}
  public String model(){return get("app.ai.model",defaultModel(provider()));}
  public int timeout(){return Integer.parseInt(get("app.ai.timeout-seconds","15"));}
  public boolean push(){return Boolean.parseBoolean(get("app.line.push-enabled","false"));}
  public String botId(){return get("app.line.bot-user-id","");}
  public boolean verificationEnabled(){return Boolean.parseBoolean(get("app.verification.enabled","true"));}
  public String verificationProvider(){return get("app.verification.provider",provider());}
  public String verificationBase(){return get("app.verification.base-url",verificationProvider().equals(provider())?aiBase():defaultBase(verificationProvider()));}
  public String verificationModel(){return get("app.verification.model",verificationProvider().equals(provider())?model():defaultModel(verificationProvider()));}
  public String verificationKey(){return get("app.verification.key",Set.of("openai","gemini").contains(verificationProvider())?keyFor(verificationProvider()):verificationBase().equals(aiBase()) && verificationProvider().equals(provider())?aiKey():"");}
  public boolean verificationQwen(){return verificationProvider().equals("local-qwen");}
  public int qualityBudgetMillis(){return Integer.parseInt(get("app.verification.budget-ms","30000"));}
  public int contextLimit(){return Integer.parseInt(get("app.context.max-messages","10"));}
  public int contextTtlSeconds(){return Integer.parseInt(get("app.context.ttl-seconds","600"));}
 }
}
