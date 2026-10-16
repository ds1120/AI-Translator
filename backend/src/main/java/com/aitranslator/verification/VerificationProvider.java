package com.aitranslator.verification;
import com.aitranslator.translation.TranslationService.Request;
public interface VerificationProvider {
 VerificationReport verify(Request original,String translation,long deadlineNanos);
 String model();
}
