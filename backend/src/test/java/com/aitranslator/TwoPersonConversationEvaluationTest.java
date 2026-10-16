package com.aitranslator;
import com.aitranslator.ai.LocalQwenProvider;
import com.aitranslator.common.JsonHttp;
import com.aitranslator.config.AppConfig.Settings;
import com.aitranslator.translation.*;
import com.aitranslator.verification.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@EnabledIfSystemProperty(named="quality.pronouns",matches="true")
class TwoPersonConversationEvaluationTest {
 @Test void evaluatesSpeakerListenerAndNamedThirdParty()throws Exception {
  var settings=new Settings(new MockEnvironment().withProperty("app.mode","live").withProperty("app.ai.provider","local-qwen").withProperty("app.ai.base-url","http://127.0.0.1:8082/v1").withProperty("app.ai.model","Qwen3-14B").withProperty("app.ai.timeout-seconds","20").withProperty("app.line.secret","evaluation-only").withProperty("app.line.token","evaluation-only"));
  var json=new ObjectMapper();var http=new JsonHttp(HttpClient.newHttpClient(),json);var verifier=new LiveVerificationProvider(http,settings);var checker=new ReverseTranslationChecker(verifier);var service=new TranslationService(new LocalQwenProvider(http,settings),checker,settings);
  List<Map<String,Object>> results=new ArrayList<>();
  for(String text:List.of("밥 먹었어?","지금 출발할게","보고 싶어","민수는 도착했어?")){
   var result=service.translate(new TranslationService.Request(text,Language.KO,Language.EN,List.of(),Map.of()));results.add(Map.of("original",text,"result",result));System.out.println("TWO_PERSON_EVAL status="+result.verificationStatus()+" verified="+result.verified()+" elapsedMs="+result.processingMillis());
  }
  var wrong=checker.check(new TranslationService.Request("지금 출발할게",Language.KO,Language.EN,List.of(),Map.of()),"You are leaving now.",System.nanoTime()+30_000_000_000L);
  results.add(Map.of("case","deliberate-speaker-listener-reversal","verification",wrong));
  var output=Path.of("target","two-person-evaluation.json");Files.createDirectories(output.getParent());json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(),results);
  assertThat(wrong.completed()).isTrue();assertThat(wrong.status()).isEqualTo(VerificationReport.Status.FAIL);assertThat(wrong.dangerous()).isTrue();assertThat(wrong.warnings()).contains("PARTICIPANT");
 }
}
