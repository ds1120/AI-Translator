package com.aitranslator.ai;
import com.aitranslator.translation.TranslationService.Request;
public interface TranslationProvider {
 String translate(Request request); String model();
 default boolean supportsIntegrated(){return false;}
 default IntegratedTranslation translateIntegrated(Request request){throw new UnsupportedOperationException("Integrated translation unavailable");}
}
