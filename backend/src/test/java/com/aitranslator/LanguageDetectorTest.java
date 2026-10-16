package com.aitranslator;
import com.aitranslator.translation.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class LanguageDetectorTest {
 private final LanguageDetector detector=new LanguageDetector();
 @Test void detectsKoreanAndThai(){
  assertThat(detector.detect("내일 9시에 출발해요")).contains(Language.KO);
  assertThat(detector.detect("พรุ่งนี้เจอกันตอนสิบโมงนะ")).contains(Language.TH);
  assertThat(detector.detect("ㅋㅋ")).contains(Language.KO);
 }
 @Test void skipsAmbiguousAndUnsupported(){
  for(String text:new String[]{"hello", "1234", "👍", "안녕 สวัสดี", "๑๒๓"})assertThat(detector.detect(text)).isEmpty();
 }
}
