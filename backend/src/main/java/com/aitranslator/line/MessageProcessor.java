package com.aitranslator.line;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.aitranslator.translation.*;
import com.aitranslator.group.GroupMapper;
import com.aitranslator.config.AppConfig.Settings;
import com.aitranslator.context.*;
import com.aitranslator.verification.VerificationFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
@Component
public class MessageProcessor {
 private static final Logger log=LoggerFactory.getLogger(MessageProcessor.class);
 private final EventMapper events;private final LanguageDetector detector;private final TranslationService translations;
 private final LineClient line;private final Settings settings;private final GroupMapper groups;private final MeterRegistry metrics;
 private final ConversationContextStore context;
 private com.aitranslator.admin.TranslationAudit audit;private com.aitranslator.admin.AdminErrorLog errors;
 @Autowired public void operations(com.aitranslator.admin.TranslationAudit audit,com.aitranslator.admin.AdminErrorLog errors){this.audit=audit;this.errors=errors;}
 public MessageProcessor(EventMapper events,LanguageDetector detector,TranslationService translations,LineClient line,Settings settings,GroupMapper groups,MeterRegistry metrics){
  this(events,detector,translations,line,settings,groups,metrics,new InMemoryConversationContextStore(settings));
 }
 @Autowired public MessageProcessor(EventMapper events,LanguageDetector detector,TranslationService translations,LineClient line,Settings settings,GroupMapper groups,MeterRegistry metrics,ConversationContextStore context){
  this.events=events;this.detector=detector;this.translations=translations;this.line=line;this.settings=settings;this.groups=groups;this.metrics=metrics;
  this.context=context;
 }
 // Webhook thread only persists events. This bounded DB worker is independent of HTTP acknowledgement.
 @Scheduled(fixedDelayString="${app.worker.delay-ms:200}")
 public void poll(){for(var event:events.pending())if(events.claim(event.eventId())==1)process(event);}
 private void process(StoredEvent e){
  long start=System.nanoTime();String state="FAILED";boolean sending=false;boolean remember=false;boolean translationJob=false;String verification="UNVERIFIED";int attempts=0;
  try {
   String input=e.originalText().strip();
   boolean command=Set.of("/설정","/상태","/도움말").contains(input) || input.matches("(?s)^/(?:언어|기본언어|번역|검증|문맥)(?:\\s+.*)?$");
   String text;
   if(command){
    // Stale commands must not overwrite a conversation's newer language setting.
    if(!fresh(e)){state="EXPIRED";return;}
    if(input.equals("/도움말"))text=help();
    else if(input.equals("/설정") || input.equals("/상태"))text=languageStatus(e.groupId());
    else if(input.startsWith("/번역")){
     String argument=input.substring("/번역".length()).strip();
     if(!Set.of("켜기","끄기").contains(argument))text="사용법: /번역 켜기 또는 /번역 끄기";
     else {ensureGroup(e.groupId());boolean enabled=argument.equals("켜기");groups.setEnabled(e.groupId(),enabled);if(!enabled)context.clear(e.groupId());text="이 대화의 번역을 "+(enabled?"켰습니다.":"껐습니다.");}
    }
    else if(input.startsWith("/검증")){
     String display=switch(input.substring("/검증".length()).strip()){case "전체" -> "all";case "경고" -> "warnings";case "숨김" -> "hidden";default -> null;};
     if(display==null)text="사용법: /검증 전체 · /검증 경고 · /검증 숨김";
     else {groups.setVerificationDisplay(e.groupId(),display);text="검증 표시 설정을 저장했습니다. 숨김은 검증 실행을 끄지 않습니다.\n"+languageStatus(e.groupId());}
    }
    else if(input.startsWith("/문맥")){
     String argument=input.substring("/문맥".length()).strip();
     if(!Set.of("0","5","10").contains(argument))text="사용법: /문맥 0 · /문맥 5 · /문맥 10";
     else {groups.setContextLimit(e.groupId(),Integer.parseInt(argument));context.clear(e.groupId());text="문맥을 최근 "+argument+"개로 설정하고 기존 문맥을 비웠습니다.\n"+languageStatus(e.groupId());}
    }
    else if(input.startsWith("/기본언어")) {
     String argument=input.substring("/기본언어".length()).strip().toUpperCase(Locale.ROOT);
     Language selected=switch(argument){case "한국어","한국","KO" -> Language.KO;case "태국어","TH" -> Language.TH;case "베트남","베트남어","VI" -> Language.VI;case "영어","EN" -> Language.EN;case "일본어","JA" -> Language.JA;default -> null;};
     if(selected==null)text="사용법: /기본언어 한국어 · 태국어 · 베트남어 · 영어 · 일본어\n"+languageStatus(e.groupId());
     else {groups.setDefaultLanguage(e.groupId(),selected.name());text="선택 언어 외 메시지를 "+languageName(selected)+"로 번역합니다.\n"+languageStatus(e.groupId());}
    }
    else {
     String argument=input.substring("/언어".length()).strip().toUpperCase(Locale.ROOT);
     Language selected=switch(argument){case "1","태국어","TH" -> Language.TH;case "2","베트남","베트남어","VI" -> Language.VI;case "3","영어","EN" -> Language.EN;case "4","일본어","JA" -> Language.JA;default -> null;};
     if(selected==null)text="사용법: /언어 1 ~ /언어 4\n"+languageStatus(e.groupId());
     else {groups.setPartnerLanguage(e.groupId(),selected.name());context.clear(e.groupId());text="이 대화의 번역 언어를 변경했습니다.\n"+languageStatus(e.groupId());}
    }
   } else {
    if(input.startsWith("/")){state="IGNORED";return;}
    Language partner=partnerLanguage(e.groupId());
    var language=detector.detect(e.originalText(),partner);
    Language target;
    Language source=language.orElse(Language.AUTO);
    if(source==Language.KO || source==partner)target=source==Language.KO?partner:Language.KO;
    else {
     String saved=groups.defaultLanguage(e.groupId());
     if(saved==null || !e.originalText().codePoints().anyMatch(Character::isLetter)){state="IGNORED";return;}
     target=Language.valueOf(saved);
     if(source==target){state="IGNORED";return;}
    }
    Boolean enabled=groups.enabled(e.groupId());
    if(enabled==null){try{groups.insert(e.groupId());}catch(DuplicateKeyException ignored){}enabled=groups.enabled(e.groupId());}
    if(!Boolean.TRUE.equals(enabled)){state="IGNORED";return;}
    if(!fresh(e) && !settings.push()){state="EXPIRED";return;}
    int remaining=settings.qualityBudgetMillis();
    if(fresh(e))remaining=(int)Math.min(remaining,40000-Math.max(System.currentTimeMillis()-e.receivedAt(),System.currentTimeMillis()-e.eventTime()));
    if(remaining<1000){state="EXPIRED";return;}
    int limit=contextLimit(e.groupId());
    translationJob=true;
    var result=translations.translate(new TranslationService.Request(e.originalText(),source,target,context.recent(e.groupId(),limit),Map.of("budget-ms",Integer.toString(remaining))));
    verification=result.verified()?result.verificationStatus().name():"UNVERIFIED";attempts=result.attempts();
    text=VerificationFormatter.format(target,result,verificationDisplay(e.groupId()));
    remember=limit>0 && !result.blocked() && result.verificationStatus()!=com.aitranslator.verification.VerificationReport.Status.FAIL;
    metrics.counter("translation.verification","status",result.verified()?result.verificationStatus().name():"UNVERIFIED").increment();
   }
   if(settings.mock())text="[MOCK] "+text;
   long now=System.currentTimeMillis();
   // Keep a safety margin; old/redelivered events must not generate costly stale pushes.
   if(fresh(e)){sending=true;line.reply(e.replyToken(),text);}
   else if(settings.push() && now-e.eventTime()<300_000){sending=true;line.push(e.groupId(),text,UUID.nameUUIDFromBytes(e.eventId().getBytes(StandardCharsets.UTF_8)).toString());}
   else {state="EXPIRED";return;}
   state="SENT";
   if(remember)context.remember(e.groupId(),e.eventId(),e.originalText());
  } catch(Exception ex){
   state=sending?"UNKNOWN":"FAILED";
   // Do not log tokens, raw messages, identifiers, or upstream response bodies.
   log.warn("Translation processing failed stage={} cause={}",sending?"delivery":"translation",ex.getClass().getSimpleName());
   if(errors!=null)errors.record(sending?"DELIVERY":"TRANSLATION",null,ex.getClass().getSimpleName());
  } finally {
   long elapsed=(System.nanoTime()-start)/1_000_000;
   events.complete(e.eventId(),state,elapsed);
   if(translationJob && audit!=null)audit.record(e.eventId(),state,elapsed,verification,attempts);
   metrics.counter("translation.events","outcome",state).increment();
   metrics.timer("translation.processing").record(java.time.Duration.ofMillis(elapsed));
   log.info("Translation event completed outcome={} elapsedMs={}",state,elapsed);
  }
 }
 private void ensureGroup(String id){if(groups.enabled(id)==null)try{groups.insert(id);}catch(DuplicateKeyException ignored){}}
 private int contextLimit(String id){Integer limit=groups.contextLimit(id);return limit==null?settings.contextLimit():limit;}
 private String verificationDisplay(String id){String display=groups.verificationDisplay(id);return display==null?"hidden":display;}
 private String help(){return "/설정 · /상태: 현재 설정\n/언어 1~4: 번역 쌍 선택\n/기본언어 한국어: 그 외 언어의 번역 대상\n/번역 켜기 · /번역 끄기\n/검증 전체 · /검증 경고 · /검증 숨김\n/문맥 0 · /문맥 5 · /문맥 10\n1:1은 본인 대화, 그룹은 해당 그룹에 적용됩니다.";}
 private boolean fresh(StoredEvent event){
  long now=System.currentTimeMillis();return now-event.receivedAt()<45_000 && now-event.eventTime()<45_000;
 }
 private Language partnerLanguage(String groupId){
  String saved=groups.partnerLanguage(groupId);return saved==null?settings.partnerLanguage():Language.valueOf(saved);
 }
 private String languageName(Language language){
  return switch(language){case KO -> "한국어";case TH -> "태국어";case VI -> "베트남어";case EN -> "영어";case JA -> "일본어";default -> throw new IllegalStateException("Invalid conversation language");};
 }
 private String languageStatus(String conversationId){
  Language partner=partnerLanguage(conversationId);
  String name=languageName(partner);
  String saved=groups.defaultLanguage(conversationId);
  String fallback=saved==null?"미설정 (선택 언어 외 메시지 건너뜀)":languageName(Language.valueOf(saved));
  return "현재 번역: 🇰🇷 한국어 ↔ "+partner.flag()+" "+name
   +"\n기본언어: "+fallback
   +"\n번역: "+(Boolean.FALSE.equals(groups.enabled(conversationId))?"OFF":"ON")
   +"\n검증 실행: "+(settings.verificationEnabled()?"ON":"OFF")+" / 표시: "+switch(verificationDisplay(conversationId)){case "all" -> "전체";case "warnings" -> "경고";default -> "숨김";}
   +"\n문맥: 최근 "+contextLimit(conversationId)+"개 / "+settings.contextTtlSeconds()+"초 만료"
   +"\n\n번역 언어 선택\n1. 한국어 ↔ 태국어\n2. 한국어 ↔ 베트남어\n3. 한국어 ↔ 영어\n4. 한국어 ↔ 일본어"
   +"\n\n/언어 1 처럼 번호를 보내면 이 대화에 적용됩니다.\n/기본언어 한국어 처럼 설정하면 그 외 언어는 기본언어로 번역합니다.\n현재 설정 확인: /설정\n전체 명령어: /도움말";
 }
 @Scheduled(fixedDelay=3_600_000,initialDelay=60_000)
 public void cleanup(){
  context.cleanup();
  events.expireProcessing(System.currentTimeMillis()-600_000);
  events.purge(System.currentTimeMillis()-7L*24*60*60*1000);
 }
}
