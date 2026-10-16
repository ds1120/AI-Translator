package com.aitranslator.verification;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import com.aitranslator.translation.TranslationService.Request;
import org.springframework.stereotype.Component;
@Component
public class ReverseTranslationChecker {
 private final VerificationProvider provider;
 public ReverseTranslationChecker(VerificationProvider provider){this.provider=provider;}
 public VerificationReport check(Request original,String translation,long deadlineNanos){
  VerificationReport report;
  try {report=provider.verify(original,translation,deadlineNanos);}
  catch(RuntimeException ex){return VerificationReport.unavailable(provider.model());}
  if(!report.completed())return report;
  var source=numbers(original.text());var reverse=numbers(report.reverseTranslation());
  if(source.equals(reverse))return report;
  var warnings=new ArrayList<>(report.warnings());warnings.add("NUMBER");
  // Missing digits can be spelled-out numbers; do not declare that a proven distortion.
  if(!source.isEmpty() && !reverse.isEmpty())return new VerificationReport(VerificationReport.Status.FAIL,report.reverseTranslation(),warnings,true,report.model(),true);
  return new VerificationReport(report.status()==VerificationReport.Status.PASS?VerificationReport.Status.WARNING:report.status(),report.reverseTranslation(),warnings,report.dangerous(),report.model(),true);
 }
 private List<String> numbers(String text){
  var result=new ArrayList<String>();var matcher=Pattern.compile("\\p{Nd}+(?:[.,]\\p{Nd}+)*").matcher(text);
  while(matcher.find()){
   var value=new StringBuilder();matcher.group().codePoints().forEach(c->{if(Character.isDigit(c))value.append(Character.digit(c,10));else value.appendCodePoint(c);});
   String normalized=value.toString().replaceAll(",(?=\\d{3}(?:\\D|$))","");
   try {normalized=new BigDecimal(normalized).stripTrailingZeros().toPlainString();}catch(NumberFormatException ignored){}
   result.add(normalized);
  }
  return result;
 }
}
