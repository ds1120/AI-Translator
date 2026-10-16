package com.aitranslator.verification;
import com.aitranslator.translation.TranslationService.Request;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component
@ConditionalOnProperty(name="app.mode",havingValue="mock")
public class MockVerificationProvider implements VerificationProvider {
 public String model(){return "mock-verification-fixtures";}
 // Mock simulates a successful check; it is not a translation-quality evaluation.
 public VerificationReport verify(Request original,String translation,long deadlineNanos){return new VerificationReport(VerificationReport.Status.PASS,original.text(),List.of(),false,model(),true);}
}
