package com.aitranslator;
import com.aitranslator.ai.TranslationProvider;
import com.aitranslator.config.AppConfig.Settings;
import com.aitranslator.translation.*;
import com.aitranslator.verification.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class TranslationQualityTest {
 private final TranslationProvider provider=mock(TranslationProvider.class);
 private final ReverseTranslationChecker checker=mock(ReverseTranslationChecker.class);
 private final Settings settings=new Settings(new MockEnvironment().withProperty("app.line.secret","test"));
 private TranslationService.Request request(){return new TranslationService.Request("내일 9시에 만나자",Language.KO,Language.TH,List.of("일정 이야기"),Map.of());}
 private VerificationReport report(VerificationReport.Status status,boolean dangerous){return new VerificationReport(status,"내일 9시에 만나요",status==VerificationReport.Status.PASS?List.of():List.of("NEGATION"),dangerous,"checker",true);}
 private TranslationService service(){when(provider.model()).thenReturn("translator");return new TranslationService(provider,checker,settings);}
 @Test void successfulVerificationReturnsMetadataAndReverseTranslation(){
  when(provider.translate(any())).thenReturn("translation");when(checker.check(any(),anyString(),anyLong())).thenReturn(report(VerificationReport.Status.PASS,false));
  var result=service().translate(request());
  assertThat(result.verified()).isTrue();assertThat(result.attempts()).isEqualTo(1);assertThat(result.reverseTranslation()).contains("9");assertThat(result.verificationModel()).isEqualTo("checker");
  verify(provider).translate(argThat(r->r.context().contains("일정 이야기") && r.options().containsKey("deadline-nanos")));
 }
 @Test void failureRetriesExactlyOnceAndCanRecover(){
  when(provider.translate(any())).thenReturn("bad","corrected");when(checker.check(any(),anyString(),anyLong())).thenReturn(report(VerificationReport.Status.FAIL,true),report(VerificationReport.Status.PASS,false));
  var result=service().translate(request());assertThat(result.translatedText()).isEqualTo("corrected");assertThat(result.blocked()).isFalse();assertThat(result.attempts()).isEqualTo(2);
  verify(provider,times(2)).translate(any());verify(provider).translate(argThat(r->"NEGATION".equals(r.options().get("repair-issues"))));
 }
 @Test void persistentDangerBlocksCandidateEvenWhenDisplayHidden(){
  when(provider.translate(any())).thenReturn("unsafe-candidate");when(checker.check(any(),anyString(),anyLong())).thenReturn(report(VerificationReport.Status.FAIL,true));
  var result=service().translate(request());assertThat(result.blocked()).isTrue();assertThat(result.attempts()).isEqualTo(2);
  assertThat(VerificationFormatter.format(Language.TH,result,"hidden")).contains("표시하지").doesNotContain("unsafe-candidate");verify(provider,times(2)).translate(any());
 }
 @Test void unknownRetryVerificationDoesNotClearKnownDanger(){
  when(provider.translate(any())).thenReturn("unsafe");when(checker.check(any(),anyString(),anyLong())).thenReturn(report(VerificationReport.Status.FAIL,true),VerificationReport.unavailable("checker"));
  assertThat(service().translate(request()).blocked()).isTrue();
 }
 @Test void uncertainRetryDoesNotClearPreviouslyIdentifiedDanger(){
  when(provider.translate(any())).thenReturn("unsafe");when(checker.check(any(),anyString(),anyLong())).thenReturn(report(VerificationReport.Status.FAIL,true),report(VerificationReport.Status.WARNING,false));
  assertThat(service().translate(request()).blocked()).isTrue();
 }
 @Test void nonDangerousFailAlwaysShowsWarningAndNoFurtherRetry(){
  when(provider.translate(any())).thenReturn("candidate");when(checker.check(any(),anyString(),anyLong())).thenReturn(report(VerificationReport.Status.FAIL,false));
  var result=service().translate(request());assertThat(result.blocked()).isFalse();assertThat(VerificationFormatter.format(Language.TH,result,"hidden")).contains("candidate","FAIL","긍정·부정");verify(provider,times(2)).translate(any());
 }
 @Test void unavailableVerifierNeverClaimsPass(){
  when(provider.translate(any())).thenReturn("candidate");when(checker.check(any(),anyString(),anyLong())).thenReturn(VerificationReport.unavailable("checker"));
  var result=service().translate(request());assertThat(result.verified()).isFalse();assertThat(result.verificationStatus()).isEqualTo(VerificationReport.Status.WARNING);
  assertThat(VerificationFormatter.format(Language.TH,result,"hidden")).contains("검증을 완료하지 못한");verify(provider).translate(any());
 }
 @Test void displayModesAffectPresentationOnly(){
  var result=new TranslationService.Result("candidate","model",10,"역번역",VerificationReport.Status.PASS,List.of(),"checker",1,false,true);
  assertThat(VerificationFormatter.format(Language.TH,result,"all")).contains("역번역");assertThat(VerificationFormatter.format(Language.TH,result,"warnings")).doesNotContain("역번역");assertThat(VerificationFormatter.format(Language.TH,result,"hidden")).doesNotContain("검증:");
 }
 @Test void shortRemainingBudgetSkipsRetry(){
  when(provider.translate(any())).thenReturn("candidate");when(checker.check(any(),anyString(),anyLong())).thenReturn(report(VerificationReport.Status.FAIL,true));
  var request=new TranslationService.Request("원문",Language.KO,Language.TH,List.of(),Map.of("budget-ms","1000"));
  var result=service().translate(request);assertThat(result.blocked()).isTrue();assertThat(result.attempts()).isEqualTo(1);verify(provider).translate(any());
 }
}
