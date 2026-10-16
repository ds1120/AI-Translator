package com.aitranslator.admin;
import com.aitranslator.config.AppConfig.Settings;
import org.springframework.core.env.*;
import org.springframework.stereotype.Service;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
@Service @DependsOnDatabaseInitialization
public class AdminAiSettings {
 public record Update(String provider,String model,String baseUrl,int timeoutSeconds){}
 private final ConfigurableEnvironment environment;private final AdminMapper mapper;private final Settings settings;private final TransactionTemplate transactions;
 public AdminAiSettings(ConfigurableEnvironment environment,AdminMapper mapper,Settings settings,PlatformTransactionManager manager){
  this.environment=environment;this.mapper=mapper;this.settings=settings;this.transactions=new TransactionTemplate(manager);
  var saved=new HashMap<String,Object>();for(var item:mapper.settings())if(Set.of("app.ai.provider","app.ai.model","app.ai.base-url","app.ai.timeout-seconds").contains(item.settingKey()))saved.put(item.settingKey(),item.settingValue());
  if(!saved.isEmpty()){validate(saved);install(saved);}
 }
 public Map<String,Object> current(){return Map.of("provider",settings.provider(),"model",settings.model(),"baseUrl",settings.aiBase(),"timeoutSeconds",settings.timeout(),"verificationModel",settings.verificationModel(),"verificationEnabled",settings.verificationEnabled(),"mode",settings.mock()?"mock":"live");}
 public synchronized Map<String,Object> update(Update update){
  var lock=settings.configurationLock().writeLock();
  if(!lock.tryLock())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"번역 처리 중입니다. 완료 후 다시 저장해 주세요.");
  try {
  if(update.provider()==null || update.model()==null || update.model().isBlank() || update.model().length()>128 || update.baseUrl()==null || update.baseUrl().length()>1024)throw new IllegalArgumentException("Invalid AI settings");
  var values=new HashMap<String,Object>();values.put("app.ai.provider",update.provider());values.put("app.ai.model",update.model().strip());values.put("app.ai.base-url",update.baseUrl().strip());values.put("app.ai.timeout-seconds",Integer.toString(update.timeoutSeconds()));
  if(!Set.of("local-qwen","external").contains(update.provider()))throw new IllegalArgumentException("Invalid provider");
  validate(values);transactions.executeWithoutResult(status->values.forEach((key,value)->mapper.saveSetting(key,value.toString())));install(values);return current();
  }finally{lock.unlock();}
 }
 private void validate(Map<String,Object> values){
  var candidate=new StandardEnvironment();candidate.merge(environment);candidate.getPropertySources().addFirst(new MapPropertySource("candidate",values));
  // Validate live endpoint/key constraints even when editing from a mock preview.
  var validation=new HashMap<>(values);validation.put("app.mode","live");validation.put("app.line.secret","validation-only");validation.put("app.line.token","validation-only");
  candidate.getPropertySources().replace("candidate",new MapPropertySource("candidate",validation));
  try{new Settings(candidate);}catch(RuntimeException ex){throw new IllegalArgumentException("AI settings are invalid; check URL, timeout and configured API keys");}
 }
 private void install(Map<String,Object> values){environment.getPropertySources().addFirst(new MapPropertySource("admin-runtime-ai",Map.copyOf(values)));}
}
