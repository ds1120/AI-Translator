package com.aitranslator.line;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.aitranslator.translation.*;
import com.aitranslator.group.GroupMapper;
import com.aitranslator.config.AppConfig.Settings;
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
 public MessageProcessor(EventMapper events,LanguageDetector detector,TranslationService translations,LineClient line,Settings settings,GroupMapper groups,MeterRegistry metrics){
  this.events=events;this.detector=detector;this.translations=translations;this.line=line;this.settings=settings;this.groups=groups;this.metrics=metrics;
 }
 // Webhook thread only persists events. This bounded DB worker is independent of HTTP acknowledgement.
 @Scheduled(fixedDelayString="${app.worker.delay-ms:200}")
 public void poll(){for(var event:events.pending())if(events.claim(event.eventId())==1)process(event);}
 private void process(StoredEvent e){
  long start=System.nanoTime();String state="FAILED";boolean sending=false;
  try {
   String input=e.originalText().strip();
   boolean command=input.equals("/설정") || input.matches("(?s)^/(?:언어|기본언어)(?:\\s+.*)?$");
   String text;
   if(command){
    // Stale commands must not overwrite a conversation's newer language setting.
    if(!fresh(e)){state="EXPIRED";return;}
    if(input.equals("/설정"))text=languageStatus(e.groupId());
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
     else {groups.setPartnerLanguage(e.groupId(),selected.name());text="이 대화의 번역 언어를 변경했습니다.\n"+languageStatus(e.groupId());}
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
    var result=translations.translate(new TranslationService.Request(e.originalText(),source,target,List.of(),Map.of()));
    text=target.flag()+" "+result.translatedText();
   }
   if(settings.mock())text="[MOCK] "+text;
   long now=System.currentTimeMillis();
   // Keep a safety margin; old/redelivered events must not generate costly stale pushes.
   if(fresh(e)){sending=true;line.reply(e.replyToken(),text);}
   else if(settings.push() && now-e.eventTime()<300_000){sending=true;line.push(e.groupId(),text,UUID.nameUUIDFromBytes(e.eventId().getBytes(StandardCharsets.UTF_8)).toString());}
   else {state="EXPIRED";return;}
   state="SENT";
  } catch(Exception ex){
   state=sending?"UNKNOWN":"FAILED";
   // Do not log tokens, raw messages, identifiers, or upstream response bodies.
   log.warn("Translation processing failed stage={} cause={}",sending?"delivery":"translation",ex.getClass().getSimpleName());
  } finally {
   long elapsed=(System.nanoTime()-start)/1_000_000;
   events.complete(e.eventId(),state,elapsed);
   metrics.counter("translation.events","outcome",state).increment();
   metrics.timer("translation.processing").record(java.time.Duration.ofMillis(elapsed));
   log.info("Translation event completed outcome={} elapsedMs={}",state,elapsed);
  }
 }
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
   +"\n\n번역 언어 선택\n1. 한국어 ↔ 태국어\n2. 한국어 ↔ 베트남어\n3. 한국어 ↔ 영어\n4. 한국어 ↔ 일본어"
   +"\n\n/언어 1 처럼 번호를 보내면 이 대화에 적용됩니다.\n/기본언어 한국어 처럼 설정하면 그 외 언어는 기본언어로 번역합니다.\n현재 설정 확인: /설정";
 }
 @Scheduled(fixedDelay=3_600_000,initialDelay=60_000)
 public void cleanup(){
  events.expireProcessing(System.currentTimeMillis()-600_000);
  events.purge(System.currentTimeMillis()-7L*24*60*60*1000);
 }
}
