package com.aitranslator.ai;
import java.util.Map;
import java.util.Set;
import com.aitranslator.translation.Language;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.aitranslator.translation.TranslationService.Request;
@Component
@ConditionalOnProperty(name="app.mode",havingValue="mock")
public class MockTranslationProvider implements TranslationProvider {
 private static final Map<String,String> FIXTURES=Map.of(
  "안녕하세요", "สวัสดีค่ะ",
  "สวัสดีค่ะ", "안녕하세요",
  "내일 아침 9시에 출발할게.", "พรุ่งนี้ฉันจะออกเดินทางตอนเก้าโมงเช้า",
  "พรุ่งนี้เจอกันตอนสิบโมงนะ", "내일 10시에 만나자.");
 public String translate(Request r){
  if(Set.of("안녕하세요","สวัสดีค่ะ","Xin chào","Hello","こんにちは","你好").contains(r.text().strip()))
   return switch(r.target()){case KO -> "안녕하세요";case TH -> "สวัสดีค่ะ";case VI -> "Xin chào";case EN -> "Hello";case JA -> "こんにちは";default -> throw new IllegalArgumentException("Invalid target language");};
  if(r.source()!=Language.TH && r.target()!=Language.TH)
   throw new IllegalArgumentException("Mock mode supports documented fixtures only");
  String value=FIXTURES.get(r.text().strip());
  if(value==null) throw new IllegalArgumentException("Mock mode supports documented fixtures only");
  return value;
 }
 public String model(){return "mock-fixtures-v1";}
}
