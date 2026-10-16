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
   var language=detector.detect(e.originalText());
   if(language.isEmpty() || e.originalText().startsWith("/")){state="IGNORED";return;}
   if(language.get()!=Language.KO && language.get()!=settings.partnerLanguage()){state="IGNORED";return;}
   Boolean enabled=groups.enabled(e.groupId());
   if(enabled==null){try{groups.insert(e.groupId());}catch(DuplicateKeyException ignored){}enabled=groups.enabled(e.groupId());}
   if(!Boolean.TRUE.equals(enabled)){state="IGNORED";return;}
   Language target=language.get()==Language.KO?settings.partnerLanguage():Language.KO;
   var result=translations.translate(new TranslationService.Request(e.originalText(),language.get(),target,List.of(),Map.of()));
   String text=target.flag()+" "+result.translatedText();
   if(settings.mock())text="[MOCK] "+text;
   long now=System.currentTimeMillis();
   // Keep a safety margin; old/redelivered events must not generate costly stale pushes.
   boolean fresh=now-e.receivedAt()<45_000 && now-e.eventTime()<45_000;
   if(fresh){sending=true;line.reply(e.replyToken(),text);}
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
 @Scheduled(fixedDelay=3_600_000,initialDelay=60_000)
 public void cleanup(){
  events.expireProcessing(System.currentTimeMillis()-600_000);
  events.purge(System.currentTimeMillis()-7L*24*60*60*1000);
 }
}
