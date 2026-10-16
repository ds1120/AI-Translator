package com.aitranslator;
import com.aitranslator.config.AppConfig.Settings;
import com.aitranslator.group.GroupMapper;
import com.aitranslator.line.*;
import com.aitranslator.translation.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class MessageLanguageRoutingTest {
 @Test void routesEachPairBothWaysAndSkipsUnselectedLanguage(){
  for(var pair:Map.of(Language.TH,"สวัสดีค่ะ",Language.EN,"Hello",Language.JA,"こんにちは").entrySet()) {
   var events=mock(EventMapper.class);var groups=mock(GroupMapper.class);var service=mock(TranslationService.class);var line=mock(LineClient.class);
   long now=System.currentTimeMillis();
   when(events.pending()).thenReturn(List.of(
    new StoredEvent("out","group","reply-out","안녕하세요",now,now,"PENDING"),
    new StoredEvent("in","group","reply-in",pair.getValue(),now,now,"PENDING"),
    new StoredEvent("skip","group","reply-skip",pair.getKey()==Language.EN?"こんにちは":"Hello",now,now,"PENDING")));
   when(events.claim(anyString())).thenReturn(1);when(groups.enabled("group")).thenReturn(true);
   when(service.translate(any())).thenReturn(new TranslationService.Result("translated","test",0));
   var settings=new Settings(new MockEnvironment().withProperty("app.line.secret","test").withProperty("app.translation.partner-language",pair.getKey().name()));
   new MessageProcessor(events,new LanguageDetector(),service,line,settings,groups,new SimpleMeterRegistry()).poll();
   verify(service).translate(argThat(r->r.source()==Language.KO && r.target()==pair.getKey()));
   verify(service).translate(argThat(r->r.source()==pair.getKey() && r.target()==Language.KO));
   verify(line).reply("reply-out","[MOCK] "+pair.getKey().flag()+" translated");
   verify(line).reply("reply-in","[MOCK] 🇰🇷 translated");
   verify(events).complete(eq("skip"),eq("IGNORED"),anyLong());
   verifyNoMoreInteractions(service,line);
  }
 }
}
