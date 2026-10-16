package com.aitranslator.translation;
import java.util.List;
import java.util.Map;
import com.aitranslator.ai.TranslationProvider;
import com.aitranslator.config.AppConfig.Settings;
import com.aitranslator.verification.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
@Service
public class TranslationService {
 private final TranslationProvider provider;
 private final ReverseTranslationChecker checker;private final Settings settings;
 public TranslationService(TranslationProvider provider){this.provider=provider;this.checker=null;this.settings=null;}
 @Autowired public TranslationService(TranslationProvider provider,ReverseTranslationChecker checker,Settings settings){this.provider=provider;this.checker=checker;this.settings=settings;}
 public record Request(String text, Language source, Language target, List<String> context, Map<String,String> options) {}
 public record Result(String translatedText,String model,long processingMillis,String reverseTranslation,VerificationReport.Status verificationStatus,List<String> warnings,String verificationModel,int attempts,boolean blocked,boolean verified) {
  public Result(String text,String model,long millis){this(text,model,millis,"",VerificationReport.Status.PASS,List.of(),"",1,false,false);}
 }
 public Result translate(Request request) {
  if(request.text()==null || request.text().isBlank() || request.text().length()>5000 || request.source()==null || request.target()==null
   || request.source()==request.target() || request.target()==Language.AUTO)
   throw new IllegalArgumentException("Invalid translation request");
  long start=System.nanoTime();
  if(checker==null)return new Result(candidate(request),provider.model(),(System.nanoTime()-start)/1_000_000);
  int budget=settings.qualityBudgetMillis();
  if(request.options()!=null && request.options().containsKey("budget-ms"))budget=Math.min(budget,Integer.parseInt(request.options().get("budget-ms")));
  if(budget<1000)throw new IllegalStateException("Translation budget exhausted");
  long deadline=start+budget*1_000_000L;
  var options=new java.util.HashMap<String,String>(request.options()==null?Map.of():request.options());options.put("deadline-nanos",Long.toString(deadline));
  Request attempt=new Request(request.text(),request.source(),request.target(),request.context(),Map.copyOf(options));
  if(!settings.verificationEnabled())return new Result(candidate(attempt),provider.model(),(System.nanoTime()-start)/1_000_000);
  String translated=candidate(attempt);int attempts=1;
  VerificationReport report=checker.check(request,translated,deadline);boolean knownDanger=report.dangerous();
  if(report.status()==VerificationReport.Status.FAIL && (deadline-System.nanoTime())>3_000_000_000L){
   attempts=2;options.put("repair-issues",String.join(",",report.warnings()));
   try {
    translated=candidate(new Request(request.text(),request.source(),request.target(),request.context(),Map.copyOf(options)));
    report=checker.check(request,translated,deadline);
    if(report.completed() && report.status()==VerificationReport.Status.PASS)knownDanger=false;
    else knownDanger=knownDanger || report.dangerous();
   }catch(RuntimeException ex){report=VerificationReport.unavailable(report.model());}
  }
  boolean blocked=report.dangerous() || (knownDanger && report.status()!=VerificationReport.Status.PASS);
  return new Result(translated,provider.model(),(System.nanoTime()-start)/1_000_000,report.reverseTranslation(),report.status(),report.warnings(),report.model(),attempts,blocked,report.completed());
 }
 private String candidate(Request request){
  String translated=provider.translate(request);
  if(translated==null || translated.isBlank() || translated.length()>4900)throw new IllegalStateException("Invalid AI translation response");
  return translated.strip();
 }
}
