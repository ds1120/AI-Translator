package com.aitranslator;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aitranslator.common.JsonHttp;
import com.aitranslator.config.AppConfig.Settings;
import com.aitranslator.ai.ExternalLlmProvider;
import com.aitranslator.line.LiveLineClient;
import com.aitranslator.translation.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ProviderAndDeliveryTest {
 @Test void translatesThroughRealHttpWithSeparatedSystemInstructions()throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  var captured=new AtomicReference<String>();
  server.createContext("/v1/chat/completions",exchange->{
   captured.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
   byte[] bytes="{\"choices\":[{\"message\":{\"content\":\"สวัสดีค่ะ\"}}]}".getBytes(StandardCharsets.UTF_8);
   exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
  });server.start();
  try {
   var settings=new Settings(new MockEnvironment().withProperty("app.line.secret","test").withProperty("app.ai.base-url","http://127.0.0.1:"+server.getAddress().getPort()+"/v1"));
   var provider=new ExternalLlmProvider(new JsonHttp(HttpClient.newHttpClient(),new ObjectMapper()),settings);
   assertThat(provider.translate(new TranslationService.Request("안녕하세요",Language.KO,Language.TH,List.of(),Map.of()))).isEqualTo("สวัสดีค่ะ");
   var payload=new ObjectMapper().readTree(captured.get());
   assertThat(payload.path("messages").get(0).path("role").asText()).isEqualTo("system");
   assertThat(payload.path("messages").get(1).path("content").asText()).isEqualTo("안녕하세요");
  } finally {server.stop(0);}
 }
 @Test void upstreamErrorsAndMalformedResponsesFailClosed(){
  var http=mock(JsonHttp.class);var settings=new Settings(new MockEnvironment().withProperty("app.line.secret","test"));
  var provider=new ExternalLlmProvider(http,settings);
  var request=new TranslationService.Request("안녕",Language.KO,Language.TH,List.of(),Map.of());
  when(http.post(anyString(),anyMap(),any(),anyInt())).thenReturn(new JsonHttp.Response(429,"secret-body"));
  assertThatThrownBy(()->provider.translate(request)).hasMessage("AI HTTP status 429");
 }
 @Test void lineUsesOfficialApiAndCountsAttemptsWithoutBlindRetry(){
  var http=mock(JsonHttp.class);var metrics=new SimpleMeterRegistry();
  var settings=new Settings(new MockEnvironment().withProperty("app.line.secret","test").withProperty("app.line.token","token"));
  var line=new LiveLineClient(http,settings,metrics);
  when(http.post(anyString(),anyMap(),any(),anyInt())).thenReturn(new JsonHttp.Response(400,"invalid token"));
  assertThatThrownBy(()->line.reply("reply-token","번역")).hasMessage("LINE HTTP status 400");
  verify(http,times(1)).post(eq("https://api.line.me/v2/bot/message/reply"),eq(Map.of("Authorization","Bearer token")),eq(Map.of("replyToken","reply-token","messages",List.of(Map.of("type","text","text","번역")))),eq(5));
  assertThat(metrics.counter("line.api.calls","method","reply").count()).isEqualTo(1);
  assertThatThrownBy(()->line.push("g","t",UUID.randomUUID().toString())).hasMessage("Push disabled");
 }
 @Test void liveModeRefusesMissingCredentials(){
  assertThatThrownBy(()->new Settings(new MockEnvironment().withProperty("app.mode","live"))).hasMessageContaining("Missing required setting");
 }
}
