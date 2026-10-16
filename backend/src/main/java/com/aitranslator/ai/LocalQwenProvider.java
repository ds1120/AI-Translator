package com.aitranslator.ai;
import com.aitranslator.common.JsonHttp;
import com.aitranslator.config.AppConfig.Settings;
import com.aitranslator.translation.TranslationService.Request;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
@Component
@ConditionalOnExpression("'${app.mode:mock}' == 'live' and '${app.ai.provider:external}' == 'local-qwen'")
public class LocalQwenProvider implements TranslationProvider {
 private final OpenAiChatClient client;private final Settings settings;
 public LocalQwenProvider(JsonHttp http,Settings settings){this.client=new OpenAiChatClient(http,settings);this.settings=settings;}
 public String model(){return settings.model();}
 public String translate(Request request){return client.translate(request,true);}
}
