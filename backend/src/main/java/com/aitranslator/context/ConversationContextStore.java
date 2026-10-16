package com.aitranslator.context;
import java.util.List;
public interface ConversationContextStore {
 List<String> recent(String conversationId,int limit);
 void remember(String conversationId,String eventId,String original);
 void clear(String conversationId);
 void cleanup();
}
