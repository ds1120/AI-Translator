package com.aitranslator.admin;
import com.aitranslator.config.AppConfig.Settings;
import com.aitranslator.group.GroupMapper;
import com.aitranslator.context.ConversationContextStore;
import com.aitranslator.translation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@RestController @RequestMapping("/api/admin")
public class AdminController {
 private final AdminMapper mapper;private final GroupMapper groups;private final Settings settings;private final AdminAiSettings ai;private final TranslationService translations;private final ConversationContextStore context;private final AdminErrorLog errors;
 public AdminController(AdminMapper mapper,GroupMapper groups,Settings settings,AdminAiSettings ai,TranslationService translations,ConversationContextStore context,AdminErrorLog errors){this.mapper=mapper;this.groups=groups;this.settings=settings;this.ai=ai;this.translations=translations;this.context=context;this.errors=errors;}
 @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
 @GetMapping("/session") public Map<String,Object> session(Authentication authentication){return Map.of("username",authentication.getName(),"mode",settings.mock()?"mock":"live");}
 @GetMapping("/dashboard") public Map<String,Object> dashboard(){return Map.of("summary",normalize(mapper.summary()),"outcomes",normalizeAll(mapper.outcomes()),"verifications",normalizeAll(mapper.verifications()),"queue",normalizeAll(mapper.queue()),"recentRuns",mapper.recentRuns(),"conversations",mapper.conversationCount(),"model",settings.model(),"verificationModel",settings.verificationModel(),"errorCount",errors.recent().size());}
 @GetMapping("/conversations") public List<Map<String,Object>> conversations(){
  return mapper.conversations().stream().map(row->{var item=normalize(row);String id=item.get("id").toString();item.put("kind",id.startsWith("C")?"group":"direct");item.put("partner",groups.partnerLanguage(id)==null?settings.partnerLanguage().name():groups.partnerLanguage(id));item.put("display",groups.verificationDisplay(id)==null?"hidden":groups.verificationDisplay(id));item.put("context_size",groups.contextLimit(id)==null?settings.contextLimit():groups.contextLimit(id));return item;}).toList();
 }
 public record ConversationUpdate(boolean enabled,String partnerLanguage,String defaultLanguage,String verificationDisplay,int contextLimit){}
 @PutMapping("/conversations/{id}") @Transactional public Map<String,Boolean> updateConversation(@PathVariable String id,@RequestBody ConversationUpdate update){
  if(id.isBlank() || id.length()>128 || !Set.of("TH","VI","EN","JA").contains(Objects.toString(update.partnerLanguage(),"")) || !Set.of("", "KO","TH","VI","EN","JA").contains(Objects.toString(update.defaultLanguage(),"")) || !Set.of("all","warnings","hidden").contains(Objects.toString(update.verificationDisplay(),"")) || !Set.of(0,5,10).contains(update.contextLimit()))throw new IllegalArgumentException("Invalid conversation settings");
  if(groups.enabled(id)==null){
   if(groups.partnerLanguage(id)==null && groups.defaultLanguage(id)==null && groups.verificationDisplay(id)==null && groups.contextLimit(id)==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
   groups.insert(id);
  }
  groups.setEnabled(id,update.enabled());groups.setPartnerLanguage(id,update.partnerLanguage());groups.setVerificationDisplay(id,update.verificationDisplay());groups.setContextLimit(id,update.contextLimit());
  if(update.defaultLanguage()==null || update.defaultLanguage().isBlank())groups.clearDefaultLanguage(id);else groups.setDefaultLanguage(id,update.defaultLanguage());
  context.clear(id);return Map.of("saved",true);
 }
 @DeleteMapping("/conversations/{id}/context") public Map<String,Boolean> clearContext(@PathVariable String id){context.clear(id);return Map.of("cleared",true);}
 @GetMapping("/ai") public Map<String,Object> ai(){return ai.current();}
 @PutMapping("/ai") public Map<String,Object> ai(@RequestBody AdminAiSettings.Update update){return ai.update(update);}
 public record TestRequest(String text,String sourceLanguage,String targetLanguage){}
 @PostMapping("/test") public TranslationService.Result test(@RequestBody TestRequest request){
  Language source=Language.valueOf(Objects.toString(request.sourceLanguage(),""));Language target=Language.valueOf(Objects.toString(request.targetLanguage(),""));
  return translations.translate(new TranslationService.Request(request.text(),source,target,List.of(),Map.of()));
 }
 @GetMapping("/errors") public List<AdminErrorLog.Entry> errors(){return errors.recent();}
 private Map<String,Object> normalize(Map<String,Object> source){var result=new LinkedHashMap<String,Object>();source.forEach((key,value)->result.put(key.toLowerCase(Locale.ROOT),value));return result;}
 private List<Map<String,Object>> normalizeAll(List<Map<String,Object>> source){return source.stream().map(this::normalize).toList();}
}
