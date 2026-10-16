package com.aitranslator.line;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component
@ConditionalOnProperty(name="app.mode",havingValue="mock")
public class MockLineClient implements LineClient {
 public record Sent(String method,String text) {}
 private final CopyOnWriteArrayList<Sent> sent=new CopyOnWriteArrayList<>();
 private void add(String method,String text){if(sent.size()>=100)sent.removeFirst();sent.add(new Sent(method,text));}
 public void reply(String token,String text){add("reply",text);}
 public void push(String group,String text,String retryKey){add("push",text);}
 public List<Sent> messages(){return List.copyOf(sent);}
 public void clear(){sent.clear();}
}
