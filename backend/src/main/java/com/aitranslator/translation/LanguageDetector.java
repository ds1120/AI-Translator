package com.aitranslator.translation;
import java.util.Optional;
import org.springframework.stereotype.Component;
@Component
public class LanguageDetector {
 public Optional<Language> detect(String text) {
  boolean ko=text.codePoints().anyMatch(c -> (c>=0xAC00&&c<=0xD7A3)||(c>=0x1100&&c<=0x11FF)||(c>=0x3131&&c<=0x318E));
  boolean th=text.codePoints().anyMatch(c -> c>=0x0E01&&c<=0x0E5B && Character.isLetter(c));
  boolean ja=text.codePoints().anyMatch(c -> Character.UnicodeScript.of(c)==Character.UnicodeScript.HIRAGANA
   || Character.UnicodeScript.of(c)==Character.UnicodeScript.KATAKANA);
  boolean han=text.codePoints().anyMatch(c -> Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN);
  // Mixed Korean/Thai/Japanese scripts are ambiguous: skip rather than choose a wrong direction.
  if((ko?1:0)+(th?1:0)+(ja?1:0)>1 || (han && !ja))return Optional.empty();
  if(ko)return Optional.of(Language.KO);
  if(th)return Optional.of(Language.TH);
  if(ja)return Optional.of(Language.JA);
  boolean latin=text.codePoints().anyMatch(c -> Character.isLetter(c) && Character.UnicodeScript.of(c)==Character.UnicodeScript.LATIN);
  boolean other=text.codePoints().anyMatch(c -> Character.isLetter(c) && Character.UnicodeScript.of(c)!=Character.UnicodeScript.LATIN);
  return latin && !other ? Optional.of(Language.EN) : Optional.empty();
 }
}
