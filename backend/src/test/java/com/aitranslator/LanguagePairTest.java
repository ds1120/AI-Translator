package com.aitranslator;
import com.aitranslator.config.AppConfig.Settings;
import com.aitranslator.ai.MockTranslationProvider;
import com.aitranslator.translation.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class LanguagePairTest {
 @Test void validatesPartnerSetting(){
  var env=new MockEnvironment().withProperty("app.line.secret","test");
  assertThat(new Settings(env).partnerLanguage()).isEqualTo(Language.TH);
  for(String value:List.of("en","JA","TH","VI"))
   assertThat(new Settings(env.withProperty("app.translation.partner-language",value)).partnerLanguage().name()).isEqualTo(value.toUpperCase(Locale.ROOT));
  for(String value:List.of("KO","AUTO","XX",""))
   assertThatThrownBy(()->new Settings(env.withProperty("app.translation.partner-language",value))).hasMessageContaining("TH, VI, EN or JA");
 }
 @Test void translatesSelectedPairsAndRejectsInvalidDirection(){
  var service=new TranslationService(new MockTranslationProvider());
  for(var pair:Map.of(Language.EN,"Hello",Language.JA,"こんにちは",Language.VI,"Xin chào").entrySet()) {
   assertThat(service.translate(new TranslationService.Request("안녕하세요",Language.KO,pair.getKey(),List.of(),Map.of())).translatedText()).isEqualTo(pair.getValue());
   assertThat(service.translate(new TranslationService.Request(pair.getValue(),pair.getKey(),Language.KO,List.of(),Map.of())).translatedText()).isEqualTo("안녕하세요");
  }
  assertThat(service.translate(new TranslationService.Request("Hello",Language.EN,Language.TH,List.of(),Map.of())).translatedText()).isEqualTo("สวัสดีค่ะ");
  assertThat(service.translate(new TranslationService.Request("你好",Language.AUTO,Language.KO,List.of(),Map.of())).translatedText()).isEqualTo("안녕하세요");
  assertThatThrownBy(()->service.translate(new TranslationService.Request("Hello",Language.EN,Language.EN,List.of(),Map.of()))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.translate(new TranslationService.Request("Hello",Language.EN,Language.AUTO,List.of(),Map.of()))).isInstanceOf(IllegalArgumentException.class);
 }
}
