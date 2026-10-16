package com.aitranslator.common;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import com.aitranslator.admin.AdminErrorLog;
import org.springframework.beans.factory.annotation.Autowired;
@Component
public class JsonHttp {
 private final HttpClient client; private final ObjectMapper json;
 private final AdminErrorLog errors;
 public JsonHttp(HttpClient client,ObjectMapper json){this(client,json,null);}
 @Autowired public JsonHttp(HttpClient client,ObjectMapper json,AdminErrorLog errors){this.client=client;this.json=json;this.errors=errors;}
 public record Response(int status, String body) {}
 public Response post(String url, Map<String,String> headers, Object body, int seconds) {
  try {
   var builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(seconds))
    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));
   headers.forEach(builder::header);
   var response=client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()>=400 && errors!=null)errors.record(url.startsWith("https://api.line.me/")?"LINE":"AI",response.statusCode(),"HTTP_ERROR");
   return new Response(response.statusCode(),response.body());
  } catch(InterruptedException ex){Thread.currentThread().interrupt();if(errors!=null)errors.record("HTTP",null,"INTERRUPTED");throw new IllegalStateException("HTTP interrupted");}
    catch(Exception ex){if(errors!=null)errors.record(url.startsWith("https://api.line.me/")?"LINE":"AI",null,ex.getClass().getSimpleName());throw new IllegalStateException("HTTP transport failed");}
 }
 public JsonNode parse(String body){try{return json.readTree(body);}catch(Exception ex){throw new IllegalStateException("Invalid JSON from upstream");}}
}
