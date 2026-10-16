package com.aitranslator.ai;
import java.util.Map;
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
  String value=FIXTURES.get(r.text().strip());
  if(value==null) throw new IllegalArgumentException("Mock mode supports documented fixtures only");
  return value;
 }
 public String model(){return "mock-fixtures-v1";}
}
