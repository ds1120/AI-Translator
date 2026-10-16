package com.aitranslator.context;
import java.time.Clock;
import java.util.*;
import com.aitranslator.config.AppConfig.Settings;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
@Component
public class InMemoryConversationContextStore implements ConversationContextStore {
 private record Message(String eventId,String text,long time){}
 private final Map<String,Deque<Message>> conversations=new LinkedHashMap<>(16,0.75f,true);
 private final Clock clock;private final long ttlMillis;private final int maxConversations;
 @Autowired public InMemoryConversationContextStore(Settings settings){this(Clock.systemUTC(),settings.contextTtlSeconds()*1000L,1000);}
 public InMemoryConversationContextStore(Clock clock,long ttlMillis,int maxConversations){this.clock=clock;this.ttlMillis=ttlMillis;this.maxConversations=maxConversations;}
 public synchronized List<String> recent(String id,int limit){
  if(limit<=0)return List.of();cleanup();var queue=conversations.get(id);if(queue==null)return List.of();
  var reversed=new ArrayList<>(queue);Collections.reverse(reversed);var selected=new ArrayList<String>();int chars=0;
  for(var message:reversed){if(selected.size()>=Math.min(10,limit))break;if(chars+message.text().length()>4000)break;selected.add(message.text());chars+=message.text().length();}
  Collections.reverse(selected);return List.copyOf(selected);
 }
 public synchronized void remember(String id,String eventId,String original){
  cleanup();var queue=conversations.computeIfAbsent(id,key->new ArrayDeque<>());
  if(queue.stream().anyMatch(message->message.eventId().equals(eventId)))return;
  // Long messages stay in the active job only; avoid retaining oversized context.
  if(original.length()>4000){if(queue.isEmpty())conversations.remove(id);return;}
  queue.addLast(new Message(eventId,original,clock.millis()));while(queue.size()>10)queue.removeFirst();
  while(conversations.size()>maxConversations)conversations.remove(conversations.keySet().iterator().next());
 }
 public synchronized void clear(String id){conversations.remove(id);}
 @org.springframework.scheduling.annotation.Scheduled(fixedDelay=30000)
 public synchronized void cleanup(){
  long cutoff=clock.millis()-ttlMillis;var iterator=conversations.values().iterator();
  while(iterator.hasNext()){var queue=iterator.next();queue.removeIf(message->message.time()<=cutoff);if(queue.isEmpty())iterator.remove();}
 }
}
