package com.aitranslator.line;
import com.aitranslator.config.AppConfig.Settings;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@RestController
public class WebhookController {
 private final SignatureVerifier verifier; private final ObjectMapper json; private final EventMapper events; private final Settings settings;
 public WebhookController(SignatureVerifier verifier,ObjectMapper json,EventMapper events,Settings settings){this.verifier=verifier;this.json=json;this.events=events;this.settings=settings;}
 @PostMapping("/webhooks/line")
 @Transactional
 public ResponseEntity<Void> receive(@RequestBody byte[] body,@RequestHeader(value="X-Line-Signature",required=false)String signature){
  if(body.length>1_000_000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
  if(!verifier.valid(body,signature)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  JsonNode root;
  try {root=json.readTree(body);} catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST);}
  if(root==null || !root.path("events").isArray()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  List<StoredEvent> accepted=new ArrayList<>();
  for(JsonNode e:root.path("events")) {
   String sourceType=e.path("source").path("type").asText();
   if(!e.path("type").asText().equals("message") || !e.path("mode").asText("active").equals("active")
    || !(sourceType.equals("group") || sourceType.equals("user")) || !e.path("message").path("type").asText().equals("text")) continue;
   if(!settings.botId().isBlank() && e.path("source").path("userId").asText().equals(settings.botId())) continue;
   // Existing group_id columns store the conversation destination (groupId or userId).
   String id=e.path("webhookEventId").asText(), group=e.path("source").path(sourceType.equals("user")?"userId":"groupId").asText();
   String token=e.path("replyToken").asText(), text=e.path("message").path("text").asText();
   long timestamp=e.path("timestamp").asLong();
   if(id.isBlank()||id.length()>128||group.isBlank()||group.length()>128||token.isBlank()||token.length()>256||text.isBlank()||text.length()>5000||timestamp<=0)
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
   accepted.add(new StoredEvent(id,group,token,text,System.currentTimeMillis(),timestamp,"PENDING"));
  }
  for(var event:accepted) try {events.insert(event);}catch(DuplicateKeyException ignored) { /* Persistent event ID is the idempotency key. */ }
  return ResponseEntity.ok().build();
 }
}
