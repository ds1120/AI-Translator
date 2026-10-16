package com.aitranslator.ai;
import com.aitranslator.common.JsonHttp;
import java.util.*;
public final class GemmaApiClient {
 private GemmaApiClient(){}
 public static String complete(JsonHttp http,String model,String key,String rules,String data,int timeout){
  if(!model.matches("gemma-[A-Za-z0-9.-]+"))throw new IllegalArgumentException("Invalid Gemma model ID");
  var body=Map.of("systemInstruction",Map.of("parts",List.of(Map.of("text",rules))),"contents",List.of(Map.of("role","user","parts",List.of(Map.of("text",data)))),"generationConfig",Map.of("maxOutputTokens",4096,"thinkingConfig",Map.of("thinkingLevel","minimal")));
  var response=http.post("https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent",Map.of("x-goog-api-key",key),body,timeout);
  if(response.status()!=200)throw new IllegalStateException("Gemma HTTP status "+response.status());
  var candidate=http.parse(response.body()).path("candidates").path(0);
  if(!candidate.path("finishReason").asText().equals("STOP"))throw new IllegalStateException("Incomplete Gemma response");
  var text=new StringBuilder();for(var part:candidate.path("content").path("parts")){if(!part.path("thought").asBoolean() && part.path("text").isTextual())text.append(part.path("text").asText());}
  if(text.isEmpty() || text.length()>20000)throw new IllegalStateException("Invalid Gemma response");return text.toString().strip();
 }
}
