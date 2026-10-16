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
 public record Result(String translatedText,String model,long processingMillis,String reverseTranslation,VerificationReport.Status verificationStatus,List<String> warnings,String verificationModel,int attempts,boolean blocked,boolean verified,String verificationMethod) {
  public Result(String text,String model,long millis,String reverse,VerificationReport.Status status,List<String> warnings,String verifier,int attempts,boolean blocked,boolean verified){this(text,model,millis,reverse,status,warnings,verifier,attempts,blocked,verified,"separate");}
  public Result(String text,String model,long millis){this(text,model,millis,"",VerificationReport.Status.PASS,List.of(),"",1,false,false);}
 }
 public Result translate(Request request) {
  if(settings==null)return translateInternal(request);
  var lock=settings.configurationLock().readLock();lock.lock();try{return translateInternal(request);}finally{lock.unlock();}
 }
 private Result translateInternal(Request request) {
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
  if(settings.verificationStrategy().equals("adaptive") && provider.supportsIntegrated())return adaptive(request,attempt,options,start,deadline);
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
 private Result adaptive(Request original,Request attempt,java.util.Map<String,String> options,long start,long deadline){
  var initial=provider.translateIntegrated(attempt);String translated=initial.translation();
  var report=IntegratedChecks.inspect(original,translated,initial.report());boolean knownDanger=report.dangerous();int attempts=1;boolean reviewed=false;
  if(report.status()!=VerificationReport.Status.PASS){report=review(original,translated,report,deadline);knownDanger|=report.dangerous();reviewed=true;}
  if(report.status()==VerificationReport.Status.FAIL && deadline-System.nanoTime()>3_000_000_000L){
   attempts=2;options.put("repair-issues",String.join(",",report.warnings()));
   try{
    var repaired=provider.translateIntegrated(new Request(original.text(),original.source(),original.target(),original.context(),Map.copyOf(options)));translated=repaired.translation();
    report=IntegratedChecks.inspect(original,translated,repaired.report());
    if(knownDanger || report.status()!=VerificationReport.Status.PASS){report=review(original,translated,report,deadline);reviewed=true;}
    if(report.completed() && report.status()==VerificationReport.Status.PASS)knownDanger=false;else knownDanger|=report.dangerous();
   }catch(RuntimeException ex){report=VerificationReport.unavailable(report.model());}
  }
  boolean blocked=report.dangerous() || (knownDanger && report.status()!=VerificationReport.Status.PASS);
  return new Result(translated,provider.model(),(System.nanoTime()-start)/1_000_000,report.reverseTranslation(),report.status(),report.warnings(),report.model(),attempts,blocked,report.completed(),reviewed?"adaptive-reviewed":"adaptive-self");
 }
 private VerificationReport review(Request original,String translated,VerificationReport previous,long deadline){
  var checked=deadline-System.nanoTime()>1_000_000_000L?checker.check(original,translated,deadline):VerificationReport.unavailable(previous.model());
  checked=IntegratedChecks.inspect(original,translated,checked);
  if(previous.dangerous() && checked.status()!=VerificationReport.Status.FAIL){
   var warnings=new java.util.LinkedHashSet<>(previous.warnings());warnings.addAll(checked.warnings());
   return new VerificationReport(VerificationReport.Status.FAIL,checked.reverseTranslation().isBlank()?previous.reverseTranslation():checked.reverseTranslation(),List.copyOf(warnings),true,checked.model(),checked.completed());
  }
  return checked;
 }
 private String candidate(Request request){
  String translated=provider.translate(request);
  if(translated==null || translated.isBlank() || translated.length()>4900)throw new IllegalStateException("Invalid AI translation response");
  return translated.strip();
 }
}
