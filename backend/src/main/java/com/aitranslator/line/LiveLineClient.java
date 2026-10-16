package com.aitranslator.line;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import com.aitranslator.common.JsonHttp;
import com.aitranslator.config.AppConfig.Settings;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component
@ConditionalOnProperty(name="app.mode",havingValue="live")
public class LiveLineClient implements LineClient {
 private final JsonHttp http; private final Settings settings; private final MeterRegistry metrics;
 public LiveLineClient(JsonHttp http,Settings settings,MeterRegistry metrics){this.http=http;this.settings=settings;this.metrics=metrics;}
 private void send(String method,Map<String,Object> body,String retryKey){
  var headers=new HashMap<String,String>();headers.put("Authorization","Bearer "+settings.token());
  if(retryKey!=null)headers.put("X-Line-Retry-Key",retryKey);
  metrics.counter("line.api.calls","method",method).increment();
  var response=http.post("https://api.line.me/v2/bot/message/"+method,headers,body,5);
  if(response.status()!=200 && !(method.equals("push")&&response.status()==409)) {
   metrics.counter("line.api.errors","method",method,"status",String.valueOf(response.status())).increment();
   throw new IllegalStateException("LINE HTTP status "+response.status());
  }
 }
 public void reply(String token,String text){send("reply",Map.of("replyToken",token,"messages",List.of(Map.of("type","text","text",text))),null);}
 public void push(String group,String text,String retryKey){
  if(!settings.push())throw new IllegalStateException("Push disabled");
  send("push",Map.of("to",group,"messages",List.of(Map.of("type","text","text",text))),retryKey);
 }
}
