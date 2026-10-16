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
  for(String text:new String[]{"1234", "👍", "안녕 สวัสดี", "๑๒๓", "中文", "안녕 こんにちは", "привет"})assertThat(detector.detect(text)).isEmpty();
 }
 @Test void detectsEnglishAndJapanese(){
  assertThat(detector.detect("Hello, see you at 9!")).contains(Language.EN);
  assertThat(detector.detect("こんにちは")).contains(Language.JA);
  assertThat(detector.detect("明日9時に会いましょう")).contains(Language.JA);
  assertThat(detector.detect("내일 meeting 있어요")).contains(Language.KO);
 }
 @Test void interpretsLatinTextUsingSelectedVietnamesePair(){
  assertThat(detector.detect("Xin chào",Language.VI)).contains(Language.VI);
  assertThat(detector.detect("Hen gap lai",Language.VI)).contains(Language.VI);
  assertThat(detector.detect("Xin cha\u0300o",Language.VI)).contains(Language.VI);
  assertThat(detector.detect("안녕하세요",Language.VI)).contains(Language.KO);
  assertThat(detector.detect("1234",Language.VI)).isEmpty();
 }
}
