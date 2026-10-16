package com.aitranslator.ai;
import com.aitranslator.common.JsonHttp;
import com.aitranslator.config.AppConfig.Settings;
import com.aitranslator.translation.TranslationService.Request;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component @Primary
@ConditionalOnProperty(name="app.mode",havingValue="live")
public class RoutingTranslationProvider implements TranslationProvider {
 private final JsonHttp http;private final Settings settings;
 public RoutingTranslationProvider(JsonHttp http,Settings settings){this.http=http;this.settings=settings;}
 public String translate(Request request){return settings.provider().equals("local-qwen")?new LocalQwenProvider(http,settings).translate(request):new ExternalLlmProvider(http,settings).translate(request);}
 public String model(){return settings.model();}
 public boolean supportsIntegrated(){return true;}
 public IntegratedTranslation translateIntegrated(Request request){return new OpenAiChatClient(http,settings).translateIntegrated(request,settings.provider().equals("local-qwen"));}
}
