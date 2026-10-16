package com.aitranslator.ai;
import com.aitranslator.common.JsonHttp;
import com.aitranslator.verification.VerificationReport;
import java.util.*;
public record IntegratedTranslation(String translation,VerificationReport report) {
 public static final Set<String> ISSUES=Set.of("NUMBER","TIME_DATE","MONEY","NAME_PLACE","NEGATION","INTENT","PARTICIPANT","OMISSION","ADDITION","UNCERTAIN");
 public static IntegratedTranslation parse(JsonHttp http,String response,String model){
  String clean=response.strip().replaceFirst("(?s)^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");var node=http.parse(clean);
  if(!node.isObject() || node.size()!=5 || !node.path("translation").isTextual() || !node.path("backTranslation").isTextual() || !node.path("status").isTextual() || !node.path("dangerous").isBoolean() || !node.path("issues").isArray() || node.path("issues").size()>10)throw new IllegalStateException("Invalid integrated translation schema");
  String text=node.path("translation").asText().strip(),reverse=node.path("backTranslation").asText().strip();
  if(text.isBlank() || text.length()>4900 || reverse.isBlank() || reverse.length()>4900)throw new IllegalStateException("Invalid integrated translation content");
  VerificationReport.Status status;try{status=VerificationReport.Status.valueOf(node.path("status").asText());}catch(IllegalArgumentException ex){throw new IllegalStateException("Invalid integrated status");}
  var issues=new ArrayList<String>();for(var issue:node.path("issues")){if(!issue.isTextual() || !ISSUES.contains(issue.asText()))throw new IllegalStateException("Invalid integrated issue");issues.add(issue.asText());}
  boolean dangerous=node.path("dangerous").asBoolean();
  if((status==VerificationReport.Status.PASS && (!issues.isEmpty() || dangerous)) || (status==VerificationReport.Status.FAIL && issues.isEmpty()) || (dangerous && status!=VerificationReport.Status.FAIL))throw new IllegalStateException("Inconsistent integrated assessment");
  if(status==VerificationReport.Status.WARNING && issues.isEmpty())issues.add("UNCERTAIN");
  return new IntegratedTranslation(text,new VerificationReport(status,reverse,issues,dangerous,model,true));
 }
}
