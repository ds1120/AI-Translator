package com.aitranslator;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aitranslator.ai.*;
import com.aitranslator.common.JsonHttp;
import com.aitranslator.config.AppConfig.Settings;
import com.aitranslator.translation.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class QwenProviderTest {
 private MockEnvironment env(){return new MockEnvironment().withProperty("app.mode","live").withProperty("app.ai.provider","local-qwen")
  .withProperty("app.line.secret","test-secret").withProperty("app.line.token","test-token")
  .withProperty("app.ai.base-url","https://qwen.example/v1").withProperty("app.ai.model","Qwen3-14B");}
 private TranslationService.Request request(){return new TranslationService.Request("안녕하세요",Language.KO,Language.TH,List.of(),Map.of());}
 private JsonHttp http(String content,String finish)throws Exception{
  var h=mock(JsonHttp.class);var json=new ObjectMapper();
  String body=json.writeValueAsString(Map.of("choices",List.of(Map.of("finish_reason",finish,"message",Map.of("content",content)))));
  when(h.post(anyString(),anyMap(),any(),anyInt())).thenReturn(new JsonHttp.Response(200,body));
  when(h.parse(body)).thenReturn(json.readTree(body));return h;
 }
 @Test void qwenOmitsAuthAndDisablesThinking()throws Exception{
  var h=http("สวัสดีค่ะ","stop");var p=new LocalQwenProvider(h,new Settings(env()));
  assertThat(p.translate(request())).isEqualTo("สวัสดีค่ะ");
  var body=ArgumentCaptor.forClass(Object.class);
  verify(h).post(eq("https://qwen.example/v1/chat/completions"),eq(Map.of()),body.capture(),eq(15));
  var payload=(Map<?,?>)body.getValue();
  assertThat(payload.get("chat_template_kwargs")).isEqualTo(Map.of("enable_thinking",false));
  assertThat(payload.get("max_tokens")).isEqualTo(512);
  assertThat(p.model()).isEqualTo("Qwen3-14B");
 }
 @Test void hidesReasoningAndRejectsIncompleteReasoning()throws Exception{
  assertThat(new LocalQwenProvider(http("<think>private reasoning</think>\nสวัสดีค่ะ","stop"),new Settings(env())).translate(request())).isEqualTo("สวัสดีค่ะ");
  assertThatThrownBy(()->new LocalQwenProvider(http("<think>unfinished","stop"),new Settings(env())).translate(request())).hasMessageContaining("reasoning");
 }
 @Test void rejectsTruncatedTranslation()throws Exception{
  var p=new LocalQwenProvider(http("unfinished text","length"),new Settings(env()));
  assertThatThrownBy(()->p.translate(request())).hasMessageContaining("truncated");
 }
 @Test void permitsLoopbackHttpButRequiresHttpsRemotely(){
  assertThatCode(()->new Settings(env().withProperty("app.ai.base-url","http://127.0.0.1:1234/v1"))).doesNotThrowAnyException();
  assertThatThrownBy(()->new Settings(env().withProperty("app.ai.base-url","http://public.example/v1"))).hasMessageContaining("HTTPS");
  assertThatThrownBy(()->new Settings(env().withProperty("app.ai.provider","external"))).hasMessageContaining("app.ai.key");
 }
 @Test void selectsExactlyOneQwenProvider(){
  new ApplicationContextRunner().withUserConfiguration(ExternalLlmProvider.class,LocalQwenProvider.class)
   .withBean(Settings.class,()->new Settings(env())).withBean(JsonHttp.class,()->mock(JsonHttp.class))
   .withPropertyValues("app.mode=live","app.ai.provider=local-qwen")
   .run(context->{assertThat(context).hasSingleBean(TranslationProvider.class);assertThat(context.getBean(TranslationProvider.class)).isInstanceOf(LocalQwenProvider.class);});
 }
}
