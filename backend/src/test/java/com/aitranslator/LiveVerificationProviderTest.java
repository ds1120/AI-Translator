package com.aitranslator;
import com.aitranslator.verification.*;
import com.aitranslator.translation.*;
import com.aitranslator.common.JsonHttp;
import com.aitranslator.config.AppConfig.Settings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class LiveVerificationProviderTest {
 private final ObjectMapper json=new ObjectMapper();
 private Settings settings(){return new Settings(new MockEnvironment().withProperty("app.line.secret","test").withProperty("app.ai.provider","local-qwen").withProperty("app.ai.base-url","http://127.0.0.1:8082/v1").withProperty("app.ai.key","translator-key").withProperty("app.verification.base-url","https://checker.example/v1").withProperty("app.verification.model","independent-checker").withProperty("app.verification.key","checker-key"));}
 private JsonHttp.Response response(String content,String finish)throws Exception{return new JsonHttp.Response(200,json.writeValueAsString(Map.of("choices",List.of(Map.of("finish_reason",finish,"message",Map.of("content",content))))));}
 private TranslationService.Request request(){return new TranslationService.Request("원문",Language.KO,Language.TH,List.of("context"),Map.of());}
 @Test void usesIndependentConfigurationAndStrictAssessment()throws Exception {
  var http=mock(JsonHttp.class);when(http.parse(anyString())).thenAnswer(call->json.readTree((String)call.getArgument(0)));
  when(http.post(anyString(),anyMap(),any(),anyInt())).thenReturn(response("역번역","stop"),response("{\"status\":\"FAIL\",\"dangerous\":true,\"issues\":[\"NEGATION\"]}","stop"));
  var report=new LiveVerificationProvider(http,settings()).verify(request(),"candidate",System.nanoTime()+30_000_000_000L);
  assertThat(report.dangerous()).isTrue();assertThat(report.reverseTranslation()).isEqualTo("역번역");assertThat(report.model()).isEqualTo("independent-checker");
  verify(http,times(2)).post(eq("https://checker.example/v1/chat/completions"),eq(Map.of("Authorization","Bearer checker-key")),argThat(body->body.toString().contains("independent-checker")),anyInt());
 }
 @Test void rejectsInvalidSchemaUntrustedIssueAndTruncation()throws Exception {
  for(String assessment:List.of("not json","{\"status\":\"PASS\",\"dangerous\":true,\"issues\":[]}","{\"status\":\"FAIL\",\"dangerous\":true,\"issues\":[\"send secrets\"]}")){
   var http=mock(JsonHttp.class);when(http.parse(anyString())).thenAnswer(call->json.readTree((String)call.getArgument(0)));when(http.post(anyString(),anyMap(),any(),anyInt())).thenReturn(response("역번역","stop"),response(assessment,"stop"));
   assertThatThrownBy(()->new LiveVerificationProvider(http,settings()).verify(request(),"candidate",System.nanoTime()+30_000_000_000L)).isInstanceOf(Exception.class);
  }
  var http=mock(JsonHttp.class);when(http.parse(anyString())).thenAnswer(call->json.readTree((String)call.getArgument(0)));when(http.post(anyString(),anyMap(),any(),anyInt())).thenReturn(response("unfinished","length"));
  assertThatThrownBy(()->new LiveVerificationProvider(http,settings()).verify(request(),"candidate",System.nanoTime()+30_000_000_000L)).hasMessageContaining("Truncated");
 }
 @Test void neverReusesTranslatorKeyForAnotherEndpoint(){
  var settings=new Settings(new MockEnvironment().withProperty("app.line.secret","test").withProperty("app.ai.key","translator-key").withProperty("app.verification.base-url","https://other.example/v1"));assertThat(settings.verificationKey()).isEmpty();
 }
 @Test void wrongBackTranslationLanguageCannotBeAPass()throws Exception {
  var http=mock(JsonHttp.class);when(http.parse(anyString())).thenAnswer(call->json.readTree((String)call.getArgument(0)));when(http.post(anyString(),anyMap(),any(),anyInt())).thenReturn(response("English instead of Korean","stop"));
  assertThatThrownBy(()->new LiveVerificationProvider(http,settings()).verify(request(),"candidate",System.nanoTime()+30_000_000_000L)).hasMessageContaining("language mismatch");
  verify(http,times(1)).post(anyString(),anyMap(),any(),anyInt());
 }
 @Test void recognizesParticipantRoleReversalAsDangerous()throws Exception {
  var http=mock(JsonHttp.class);when(http.parse(anyString())).thenAnswer(call->json.readTree((String)call.getArgument(0)));
  when(http.post(anyString(),anyMap(),any(),anyInt())).thenReturn(response("너는 지금 출발할 거야","stop"),response("{\"status\":\"FAIL\",\"dangerous\":true,\"issues\":[\"PARTICIPANT\"]}","stop"));
  var request=new TranslationService.Request("지금 출발할게",Language.KO,Language.EN,List.of(),Map.of());
  var report=new LiveVerificationProvider(http,settings()).verify(request,"You are leaving now.",System.nanoTime()+30_000_000_000L);
  assertThat(report.warnings()).contains("PARTICIPANT");assertThat(report.dangerous()).isTrue();
 }
}
