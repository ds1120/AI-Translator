package com.aitranslator;
import com.aitranslator.ai.LocalQwenProvider;
import com.aitranslator.common.JsonHttp;
import com.aitranslator.config.AppConfig.Settings;
import com.aitranslator.translation.*;
import com.aitranslator.verification.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
@EnabledIfSystemProperty(named="quality.eval",matches="true")
class QwenQualityEvaluationTest {
 private record Case(String id,String text,Language source,Language target){}
 @Test void evaluatesLocalQwenWithoutSendingAnyLineMessages()throws Exception {
  var env=new MockEnvironment().withProperty("app.mode","live").withProperty("app.ai.provider","local-qwen").withProperty("app.line.secret","evaluation-only").withProperty("app.line.token","evaluation-only")
   .withProperty("app.ai.base-url",System.getProperty("quality.base-url","http://127.0.0.1:8082/v1")).withProperty("app.ai.model",System.getProperty("quality.model","Qwen3-14B"));
  var settings=new Settings(env);var json=new ObjectMapper();var http=new JsonHttp(HttpClient.newHttpClient(),json);
  var verification=new LiveVerificationProvider(http,settings);var checker=new ReverseTranslationChecker(verification);var service=new TranslationService(new LocalQwenProvider(http,settings),checker,settings);
  var cases=List.of(new Case("ko-th","내일 오전 9시에 호텔 앞에서 만나요.",Language.KO,Language.TH),new Case("th-ko","พรุ่งนี้เจอกันตอนเก้าโมงเช้า",Language.TH,Language.KO),
   new Case("ko-vi","송금하지 마세요.",Language.KO,Language.VI),new Case("vi-ko","Đừng chuyển tiền.",Language.VI,Language.KO),new Case("ko-en","가격은 12000원입니다.",Language.KO,Language.EN),
   new Case("en-ko","The price is 12000 won.",Language.EN,Language.KO),new Case("ko-ja","오늘 오후 3시에 출발해 주세요.",Language.KO,Language.JA),new Case("ja-ko","今日の午後3時に出発してください。",Language.JA,Language.KO));
  List<Map<String,Object>> results=new ArrayList<>();
  for(var item:cases){
   var result=service.translate(new TranslationService.Request(item.text(),item.source(),item.target(),List.of(),Map.of()));
   results.add(Map.of("id",item.id(),"original",item.text(),"result",result));
   System.out.println("QUALITY_EVAL id="+item.id()+" status="+result.verificationStatus()+" verified="+result.verified()+" blocked="+result.blocked()+" attempts="+result.attempts()+" elapsedMs="+result.processingMillis());
  }
  var corrupted=checker.check(new TranslationService.Request("내일 오전 9시에 만나자.",Language.KO,Language.TH,List.of(),Map.of()),"พรุ่งนี้เจอกันตอนสิบโมงเช้า",System.nanoTime()+30_000_000_000L);
  results.add(Map.of("id","deliberate-time-error","verification",corrupted));
  Path output=Path.of("target","quality-evaluation.json");Files.createDirectories(output.getParent());json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(),results);
  assertThat(results).hasSize(9);assertThat(corrupted.completed()).isTrue();assertThat(corrupted.status()).isEqualTo(VerificationReport.Status.FAIL);assertThat(corrupted.dangerous()).isTrue();
  assertThat(results.subList(0,8)).allSatisfy(row->{var result=(TranslationService.Result)row.get("result");assertThat(result.translatedText()).isNotBlank();});
 }
}
