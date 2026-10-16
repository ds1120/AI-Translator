package com.aitranslator.common;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
@Component
public class JsonHttp {
 private final HttpClient client; private final ObjectMapper json;
 public JsonHttp(HttpClient client,ObjectMapper json){this.client=client;this.json=json;}
 public record Response(int status, String body) {}
 public Response post(String url, Map<String,String> headers, Object body, int seconds) {
  try {
   var builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(seconds))
    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));
   headers.forEach(builder::header);
   var response=client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
   return new Response(response.statusCode(),response.body());
  } catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException("HTTP interrupted");}
    catch(Exception ex){throw new IllegalStateException("HTTP transport failed");}
 }
 public JsonNode parse(String body){try{return json.readTree(body);}catch(Exception ex){throw new IllegalStateException("Invalid JSON from upstream");}}
}
