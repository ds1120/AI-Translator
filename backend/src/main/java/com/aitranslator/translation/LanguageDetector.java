package com.aitranslator.translation;
import java.util.Optional;
import org.springframework.stereotype.Component;
@Component
public class LanguageDetector {
 public Optional<Language> detect(String text) {
  boolean ko=text.codePoints().anyMatch(c -> (c>=0xAC00&&c<=0xD7A3)||(c>=0x1100&&c<=0x11FF)||(c>=0x3131&&c<=0x318E));
  boolean th=text.codePoints().anyMatch(c -> c>=0x0E01&&c<=0x0E5B && Character.isLetter(c));
  // Mixed Korean/Thai text is ambiguous: skip rather than choose a wrong direction.
  return ko==th ? Optional.empty() : Optional.of(ko?Language.KO:Language.TH);
 }
}
