package com.aitranslator;
import com.aitranslator.context.InMemoryConversationContextStore;
import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ConversationContextTest {
 private final AtomicLong now=new AtomicLong(1000);
 private final Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return Instant.ofEpochMilli(now.get());}};
 @Test void isolatesConversationsDeduplicatesAndBoundsHistory(){
  var store=new InMemoryConversationContextStore(clock,60000,10);
  for(int i=0;i<12;i++)store.remember("alice","event-"+i,"message-"+i);
  store.remember("alice","event-11","duplicate");store.remember("bob","other","separate");
  assertThat(store.recent("alice",10)).hasSize(10).startsWith("message-2").endsWith("message-11");assertThat(store.recent("alice",5)).hasSize(5);assertThat(store.recent("bob",10)).containsExactly("separate");
 }
 @Test void expiresAndClearsContext(){
  var store=new InMemoryConversationContextStore(clock,1000,10);store.remember("a","1","original");assertThat(store.recent("a",0)).isEmpty();
  now.addAndGet(1000);store.cleanup();assertThat(store.recent("a",10)).isEmpty();store.remember("a","2","new");store.clear("a");assertThat(store.recent("a",10)).isEmpty();
 }
 @Test void capsCharactersAndEvictsOldConversations(){
  var store=new InMemoryConversationContextStore(clock,60000,2);store.remember("a","1","a".repeat(3000));store.remember("a","2","b".repeat(2000));assertThat(store.recent("a",10)).containsExactly("b".repeat(2000));
  store.remember("b","1","b");store.remember("c","1","c");assertThat(store.recent("a",10)).isEmpty();
 }
}
