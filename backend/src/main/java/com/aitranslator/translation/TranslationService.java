package com.aitranslator.translation;
import java.util.List;
import java.util.Map;
import com.aitranslator.ai.TranslationProvider;
import org.springframework.stereotype.Service;
@Service
public class TranslationService {
 private final TranslationProvider provider;
 public TranslationService(TranslationProvider provider){this.provider=provider;}
 public record Request(String text, Language source, Language target, List<String> context, Map<String,String> options) {}
 public record Result(String translatedText, String model, long processingMillis) {}
 public Result translate(Request request) {
  if(request.text()==null || request.text().isBlank() || request.text().length()>5000 || request.source()==null || request.target()!=request.source().opposite())
   throw new IllegalArgumentException("Invalid translation request");
  long start=System.nanoTime();
  String translated=provider.translate(request);
  if(translated==null || translated.isBlank() || translated.length()>4900) throw new IllegalStateException("Invalid AI translation response");
  return new Result(translated.strip(),provider.model(),(System.nanoTime()-start)/1_000_000);
 }
}
