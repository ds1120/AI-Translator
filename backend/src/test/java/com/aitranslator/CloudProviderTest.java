package com.aitranslator;
import com.aitranslator.ai.RoutingTranslationProvider;
import com.aitranslator.common.JsonHttp;
import com.aitranslator.config.AppConfig.Settings;
import com.aitranslator.translation.*;
import com.aitranslator.verification.LiveVerificationProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CloudProviderTest {
 private MockEnvironment env(String provider){return new MockEnvironment().withProperty("app.mode","live").withProperty("app.line.secret","fixture").withProperty("app.line.token","fixture").withProperty("app.ai.provider",provider).withProperty("app.ai.openai.key","openai-fixture").withProperty("app.ai.gemini.key","gemini-fixture");}
 private TranslationService.Request request(){return new TranslationService.Request("안녕하세요",Language.KO,Language.EN,List.of(),Map.of());}
 @Test void namedProvidersRouteToOfficialEndpointWithSeparateKeys()throws Exception {
  for(String provider:List.of("openai","gemini")){
   var settings=new Settings(env(provider));var http=mock(JsonHttp.class);var json=new ObjectMapper();
   String response="{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Hello\"}}]}";
   when(http.post(anyString(),anyMap(),any(),anyInt())).thenReturn(new JsonHttp.Response(200,response));when(http.parse(response)).thenReturn(json.readTree(response));
   assertThat(new RoutingTranslationProvider(http,settings).translate(request())).isEqualTo("Hello");
   verify(http).post(eq(Settings.defaultBase(provider)+"/chat/completions"),eq(Map.of("Authorization","Bearer "+provider+"-fixture")),argThat(value->{var body=(Map<?,?>)value;return body.get("max_completion_tokens").equals(4096) && !body.containsKey("chat_template_kwargs") && !body.containsKey("temperature") && (provider.equals("openai")?!body.containsKey("reasoning_effort"):body.get("reasoning_effort").equals("low"));}),eq(15));
  }
 }
 @Test void cloudKeysAreNotTakenFromLegacyProviderAndCannotUseAnotherHost(){
  assertThatThrownBy(()->new Settings(env("gemini").withProperty("app.ai.gemini.key","").withProperty("app.ai.key","legacy-fixture"))).hasMessageContaining("app.ai.gemini.key");
  assertThatThrownBy(()->new Settings(env("openai").withProperty("app.ai.base-url",Settings.defaultBase("gemini")))).hasMessageContaining("official API endpoint");
 }
 @Test void independentVerifierSelectsOwnProviderKeyAndModel(){
  var settings=new Settings(env("gemini").withProperty("app.verification.provider","openai"));
  assertThat(settings.aiKey()).isEqualTo("gemini-fixture");assertThat(settings.verificationKey()).isEqualTo("openai-fixture");assertThat(settings.verificationBase()).isEqualTo(Settings.defaultBase("openai"));assertThat(settings.verificationModel()).isEqualTo("gpt-4.1-mini");
 }
 @Test void geminiVerifierUsesCompatiblePayloadAndBearerAuthentication()throws Exception {
  var settings=new Settings(env("gemini"));var http=mock(JsonHttp.class);var json=new ObjectMapper();
  String reverse=json.writeValueAsString(Map.of("choices",List.of(Map.of("message",Map.of("content","안녕하세요")))));
  String assessment=json.writeValueAsString(Map.of("choices",List.of(Map.of("message",Map.of("content","{\"status\":\"PASS\",\"dangerous\":false,\"issues\":[]}")))));
  when(http.post(anyString(),anyMap(),any(),anyInt())).thenReturn(new JsonHttp.Response(200,reverse),new JsonHttp.Response(200,assessment));when(http.parse(anyString())).thenAnswer(call->json.readTree((String)call.getArgument(0)));
  assertThat(new LiveVerificationProvider(http,settings).verify(request(),"Hello",System.nanoTime()+30_000_000_000L).completed()).isTrue();
  verify(http,times(2)).post(eq(Settings.defaultBase("gemini")+"/chat/completions"),eq(Map.of("Authorization","Bearer gemini-fixture")),argThat(value->{var body=(Map<?,?>)value;return body.get("max_completion_tokens").equals(4096) && body.get("reasoning_effort").equals("low") && !body.containsKey("temperature");}),anyInt());
 }
 @Test void cloudFailureIsNotSilentlySentToQwen(){
  var http=mock(JsonHttp.class);when(http.post(anyString(),anyMap(),any(),anyInt())).thenReturn(new JsonHttp.Response(429,"quota exceeded"));
  assertThatThrownBy(()->new RoutingTranslationProvider(http,new Settings(env("gemini"))).translate(request())).hasMessageContaining("429");verify(http,times(1)).post(anyString(),anyMap(),any(),anyInt());
 }
}
