package com.aitranslator;
import com.aitranslator.verification.*;
import com.aitranslator.translation.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ReverseTranslationCheckerTest {
 private VerificationReport check(String original,String reverse){
  var provider=mock(VerificationProvider.class);when(provider.verify(any(),anyString(),anyLong())).thenReturn(new VerificationReport(VerificationReport.Status.PASS,reverse,List.of(),false,"checker",true));
  return new ReverseTranslationChecker(provider).check(new TranslationService.Request(original,Language.KO,Language.TH,List.of(),Map.of()),"translation",Long.MAX_VALUE);
 }
 @Test void changedTimeOrAmountOverridesModelPass(){
  assertThat(check("9시에 12000원","10시에 12000원").dangerous()).isTrue();assertThat(check("100원","1000원").status()).isEqualTo(VerificationReport.Status.FAIL);
 }
 @Test void normalizesUnicodeDigitsAndThousandsSeparators(){assertThat(check("9시에 12,000원","๙시에 12000원").status()).isEqualTo(VerificationReport.Status.PASS);}
 @Test void spelledOutNumbersRequireReviewRatherThanProvenFailure(){var result=check("9시에","아홉 시에");assertThat(result.status()).isEqualTo(VerificationReport.Status.WARNING);assertThat(result.dangerous()).isFalse();}
 @Test void semanticEquivalenceDoesNotRequireStringEquality(){assertThat(check("내일 만나요","내일 만나자").status()).isEqualTo(VerificationReport.Status.PASS);}
 @Test void upstreamFailureBecomesUnverifiedWarning(){
  var provider=mock(VerificationProvider.class);when(provider.verify(any(),anyString(),anyLong())).thenThrow(new IllegalStateException("not logged"));when(provider.model()).thenReturn("checker");
  var result=new ReverseTranslationChecker(provider).check(new TranslationService.Request("원문",Language.KO,Language.TH,List.of(),Map.of()),"translation",Long.MAX_VALUE);
  assertThat(result.completed()).isFalse();assertThat(result.status()).isEqualTo(VerificationReport.Status.WARNING);
 }
}
