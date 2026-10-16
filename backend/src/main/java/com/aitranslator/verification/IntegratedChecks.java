package com.aitranslator.verification;
import com.aitranslator.translation.*;
import java.util.*;
import java.util.regex.Pattern;
import java.math.BigDecimal;
public final class IntegratedChecks {
 private IntegratedChecks(){}
 public static VerificationReport inspect(TranslationService.Request original,String candidate,VerificationReport report){
  var warnings=new LinkedHashSet<>(report.warnings());boolean dangerous=report.dangerous();
  var source=numbers(original.text());
  for(String text:List.of(candidate,report.reverseTranslation())){
   var actual=numbers(text);if(source.equals(actual))continue;warnings.add("NUMBER");
   if(!source.isEmpty() && !actual.isEmpty())dangerous=true;
  }
  var detector=new LanguageDetector();
  if(!languageMatches(detector,candidate,original.target()) || (original.source()!=Language.AUTO && !report.reverseTranslation().isBlank() && !languageMatches(detector,report.reverseTranslation(),original.source())))warnings.add("UNCERTAIN");
  var status=dangerous?VerificationReport.Status.FAIL:!warnings.isEmpty() && report.status()==VerificationReport.Status.PASS?VerificationReport.Status.WARNING:report.status();
  return new VerificationReport(status,report.reverseTranslation(),List.copyOf(warnings),dangerous,report.model(),report.completed());
 }
 private static boolean languageMatches(LanguageDetector detector,String text,Language expected){
  if(text.codePoints().noneMatch(Character::isLetter))return true;
  if(expected==Language.JA && text.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN))return true;
  return detector.detect(text,expected).orElse(Language.AUTO)==expected;
 }
 private static List<String> numbers(String text){
  var result=new ArrayList<String>();var matcher=Pattern.compile("\\p{Nd}+(?:[.,]\\p{Nd}+)*").matcher(text);
  while(matcher.find()){
   var value=new StringBuilder();matcher.group().codePoints().forEach(c->{if(Character.isDigit(c))value.append(Character.digit(c,10));else value.appendCodePoint(c);});
   String normalized=value.toString().replaceAll(",(?=\\d{3}(?:\\D|$))","");try{normalized=new BigDecimal(normalized).stripTrailingZeros().toPlainString();}catch(NumberFormatException ignored){}result.add(normalized);
  }return result;
 }
}
