package com.aitranslator;
import com.aitranslator.ai.*;
import com.aitranslator.common.JsonHttp;
import com.aitranslator.config.AppConfig.Settings;
import com.aitranslator.translation.*;
import com.aitranslator.verification.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class AdaptiveTranslationTest {
 private final TranslationProvider provider=mock(TranslationProvider.class);private final ReverseTranslationChecker checker=mock(ReverseTranslationChecker.class);
 private final Settings settings=new Settings(new MockEnvironment().withProperty("app.line.secret","fixture"));
 private TranslationService.Request request(){return new TranslationService.Request("내일 9시에 출발할게",Language.KO,Language.TH,List.of(),Map.of());}
 private VerificationReport pass(){return new VerificationReport(VerificationReport.Status.PASS,"내일 9시에 출발할게",List.of(),false,"gemma",true);}
 private IntegratedTranslation candidate(){return new IntegratedTranslation("พรุ่งนี้จะออกเดินทางตอน 9 โมง",pass());}
 private TranslationService service(){when(provider.supportsIntegrated()).thenReturn(true);when(provider.model()).thenReturn("gemma");return new TranslationService(provider,checker,settings);}
 @Test void normalPassUsesOnlyOneIntegratedRequest(){
  when(provider.translateIntegrated(any())).thenReturn(candidate());var result=service().translate(request());
  assertThat(result.verificationStatus()).isEqualTo(VerificationReport.Status.PASS);assertThat(result.verificationMethod()).isEqualTo("adaptive-self");verify(provider,times(1)).translateIntegrated(any());verify(provider,never()).translate(any());verifyNoInteractions(checker);
 }
 @Test void warningTriggersAdditionalReviewWithoutRetranslation(){
  var warning=new VerificationReport(VerificationReport.Status.WARNING,pass().reverseTranslation(),List.of("PARTICIPANT"),false,"gemma",true);
  when(provider.translateIntegrated(any())).thenReturn(new IntegratedTranslation(candidate().translation(),warning));when(checker.check(any(),anyString(),anyLong())).thenReturn(pass());
  var result=service().translate(request());assertThat(result.verificationStatus()).isEqualTo(VerificationReport.Status.PASS);assertThat(result.verificationMethod()).isEqualTo("adaptive-reviewed");verify(provider,times(1)).translateIntegrated(any());verify(checker,times(1)).check(any(),anyString(),anyLong());
 }
 @Test void codeGuardDetectsWrongCandidateEvenWhenBackTranslationAndSelfAssessmentHideIt(){
  when(provider.translateIntegrated(any())).thenReturn(new IntegratedTranslation("พรุ่งนี้จะออกเดินทางตอน 10 โมง",pass()),candidate());when(checker.check(any(),anyString(),anyLong())).thenReturn(pass());
  var result=service().translate(request());assertThat(result.attempts()).isEqualTo(2);assertThat(result.blocked()).isFalse();assertThat(result.translatedText()).contains("9");verify(provider,times(2)).translateIntegrated(any());verify(checker,times(2)).check(any(),anyString(),anyLong());
 }
 @Test void unavailableExtraReviewNeverClearsKnownDanger(){
  var danger=new VerificationReport(VerificationReport.Status.FAIL,pass().reverseTranslation(),List.of("NEGATION"),true,"gemma",true);
  when(provider.translateIntegrated(any())).thenReturn(new IntegratedTranslation(candidate().translation(),danger),candidate());when(checker.check(any(),anyString(),anyLong())).thenReturn(VerificationReport.unavailable("reviewer"));
  var result=service().translate(request());assertThat(result.blocked()).isTrue();assertThat(result.verified()).isFalse();assertThat(result.warnings()).contains("UNAVAILABLE");verify(provider,times(2)).translateIntegrated(any());
 }
 @Test void rejectsMalformedOrContradictoryIntegratedAssessment(){
  var http=new JsonHttp(java.net.http.HttpClient.newHttpClient(),new ObjectMapper());
  for(String value:List.of("not json","{\"translation\":\"hello\"}","{\"translation\":\"hello\",\"backTranslation\":\"안녕\",\"status\":\"PASS\",\"dangerous\":true,\"issues\":[]}","{\"translation\":\"hello\",\"backTranslation\":\"안녕\",\"status\":\"WARNING\",\"dangerous\":false,\"issues\":[\"UNTRUSTED\"]}"))assertThatThrownBy(()->IntegratedTranslation.parse(http,value,"gemma")).isInstanceOf(IllegalStateException.class);
 }
 @Test void gemmaIntegratedCallUsesNativeApiAndDiscardsThoughtParts()throws Exception {
  var env=new MockEnvironment().withProperty("app.mode","live").withProperty("app.line.secret","fixture").withProperty("app.line.token","fixture").withProperty("app.ai.provider","gemini").withProperty("app.ai.model","gemma-4-31b-it").withProperty("app.ai.gemini.key","gemini-fixture");var settings=new Settings(env);var http=mock(JsonHttp.class);var json=new ObjectMapper();
  String answer=json.writeValueAsString(Map.of("translation",candidate().translation(),"backTranslation",pass().reverseTranslation(),"status","PASS","dangerous",false,"issues",List.of()));
  String response=json.writeValueAsString(Map.of("candidates",List.of(Map.of("finishReason","STOP","content",Map.of("parts",List.of(Map.of("thought",true,"text","hidden reasoning"),Map.of("text",answer)))))));
  when(http.post(anyString(),anyMap(),any(),anyInt())).thenReturn(new JsonHttp.Response(200,response));when(http.parse(anyString())).thenAnswer(call->json.readTree((String)call.getArgument(0)));
  var routing=new RoutingTranslationProvider(http,settings);var result=new TranslationService(routing,new ReverseTranslationChecker(new LiveVerificationProvider(http,settings)),settings).translate(request());
  assertThat(result.verificationStatus()).isEqualTo(VerificationReport.Status.PASS);assertThat(result.translatedText()).doesNotContain("reasoning");
  verify(http,times(1)).post(eq("https://generativelanguage.googleapis.com/v1beta/models/gemma-4-31b-it:generateContent"),eq(Map.of("x-goog-api-key","gemini-fixture")),argThat(body->body.toString().contains("thinkingLevel=minimal") && body.toString().contains("ONE response") && !body.toString().contains("reasoning_effort")),anyInt());
 }
 @Test void gemmaTruncationCannotBecomeAPass()throws Exception {
  var http=mock(JsonHttp.class);when(http.post(anyString(),anyMap(),any(),anyInt())).thenReturn(new JsonHttp.Response(200,"{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}"));when(http.parse(anyString())).thenAnswer(call->new ObjectMapper().readTree((String)call.getArgument(0)));
  assertThatThrownBy(()->GemmaApiClient.complete(http,"gemma-4-31b-it","fixture","rules","data",10)).hasMessageContaining("Incomplete");
 }
}
