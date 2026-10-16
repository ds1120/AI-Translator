package com.aitranslator.ai;
import com.aitranslator.translation.TranslationService.Request;
public interface TranslationProvider { String translate(Request request); String model(); }
