package com.aitranslator;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.aitranslator.line.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
@SpringBootTest(properties={"app.worker.delay-ms=30","app.line.bot-user-id=our-bot"})
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class WebhookIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired MockLineClient line;
 @Autowired EventMapper events; @Autowired JdbcTemplate db;
 @BeforeEach void clear(){db.update("DELETE FROM webhook_events");db.update("DELETE FROM chat_groups");line.clear();}
 private String body(String id,String text,String group,String user,String mode,long timestamp) throws Exception {
  return json.writeValueAsString(Map.of("events",List.of(Map.of("type","message","mode",mode,"webhookEventId",id,"timestamp",timestamp,
    "replyToken","test-token","source",Map.of("type","group","groupId",group,"userId",user),"message",Map.of("type","text","id","m1","text",text)))));
 }
 private String body(String id,String text)throws Exception{return body(id,text,"group-test","user-test","active",System.currentTimeMillis());}
 private String signature(String body)throws Exception {
  Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("mock-channel-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
  return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
 }
 private void postBody(String body)throws Exception{mvc.perform(post("/webhooks/line").contentType("application/json").content(body).header("X-Line-Signature",signature(body))).andExpect(status().isOk());}
 private void finished(String id,String state){await().atMost(Duration.ofSeconds(5)).untilAsserted(()->assertThat(events.state(id)).isEqualTo(state));}
 @Test void koreanToThaiAndPrivatePayloadRemoved()throws Exception {
  postBody(body("ko-1","내일 아침 9시에 출발할게."));finished("ko-1","SENT");
  assertThat(line.messages()).hasSize(1);
  assertThat(line.messages().getFirst().text()).isEqualTo("[MOCK] 🇹🇭 พรุ่งนี้ฉันจะออกเดินทางตอนเก้าโมงเช้า");
  var row=db.queryForMap("SELECT original_text,reply_token,group_id FROM webhook_events WHERE event_id='ko-1'");
  assertThat(row.values()).allMatch(Objects::isNull);
 }
 @Test void thaiToKorean()throws Exception {
  postBody(body("th-1","พรุ่งนี้เจอกันตอนสิบโมงนะ"));finished("th-1","SENT");
  assertThat(line.messages().getFirst().text()).isEqualTo("[MOCK] 🇰🇷 내일 10시에 만나자.");
 }
 @Test void duplicateEventRepliesOnce()throws Exception {
  String body=body("duplicate","안녕하세요");postBody(body);postBody(body);finished("duplicate","SENT");postBody(body);
  assertThat(line.messages()).hasSize(1);
 }
 @Test void concurrentRedeliveryRepliesOnce()throws Exception {
  String body=body("race","안녕하세요");
  try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
   var tasks=new ArrayList<Future<?>>();for(int i=0;i<8;i++)tasks.add(executor.submit(()->{try{postBody(body);}catch(Exception e){throw new RuntimeException(e);}}));
   for(var task:tasks)task.get(5,TimeUnit.SECONDS);
  }
  finished("race","SENT");assertThat(line.messages()).hasSize(1);
 }
 @Test void invalidSignatureAndTamperingNeverEnqueue()throws Exception {
  String body=body("bad","안녕하세요");
  mvc.perform(post("/webhooks/line").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
  mvc.perform(post("/webhooks/line").contentType("application/json").content(body).header("X-Line-Signature","garbage")).andExpect(status().isUnauthorized());
  mvc.perform(post("/webhooks/line").contentType("application/json").content(body+" ").header("X-Line-Signature",signature(body))).andExpect(status().isUnauthorized());
  assertThat(events.state("bad")).isNull();
 }
 @Test void verificationEmptyEventsAndMalformedSignedJson()throws Exception {
  postBody("{\"events\":[]}");
  mvc.perform(post("/webhooks/line").contentType("application/json").content("{").header("X-Line-Signature",signature("{"))).andExpect(status().isBadRequest());
 }
 @Test void unsupportedTextAndCommandsAreIgnored()throws Exception {
  for(var pair:Map.of("english","hello", "mixed","안녕 สวัสดี", "command","/번역 끄기").entrySet()){
   postBody(body(pair.getKey(),pair.getValue()));finished(pair.getKey(),"IGNORED");
  }
  assertThat(line.messages()).isEmpty();
 }
 @Test void botAndStandbyEventsNotStored()throws Exception {
  postBody(body("bot","안녕하세요","g","our-bot","active",System.currentTimeMillis()));
  postBody(body("standby","안녕하세요","g","user","standby",System.currentTimeMillis()));
  assertThat(events.state("bot")).isNull();assertThat(events.state("standby")).isNull();assertThat(line.messages()).isEmpty();
 }
 @Test void nonGroupAndNonTextAreIgnored()throws Exception {
  postBody("{\"events\":[{\"type\":\"message\",\"source\":{\"type\":\"user\"},\"message\":{\"type\":\"text\"}},{\"type\":\"message\",\"source\":{\"type\":\"group\"},\"message\":{\"type\":\"image\"}}]}");
  assertThat(events.pending()).isEmpty();assertThat(line.messages()).isEmpty();
 }
 @Test void failedProviderIsTerminalAndDoesNotSend()throws Exception {
  String body=body("failure","Mock에 없는 문장");postBody(body);finished("failure","FAILED");postBody(body);
  assertThat(line.messages()).isEmpty();
 }
 @Test void expiredTokenDoesNotPushByDefault()throws Exception {
  postBody(body("old","안녕하세요","g","u","active",System.currentTimeMillis()-120_000));finished("old","EXPIRED");
  assertThat(line.messages()).isEmpty();
 }
 @Test void groupOffIsRespected()throws Exception {
  db.update("INSERT INTO chat_groups(group_id,translation_enabled) VALUES('group-test',FALSE)");
  postBody(body("off","안녕하세요"));finished("off","IGNORED");assertThat(line.messages()).isEmpty();
 }
 @Test void noUnauthenticatedAdminSurface()throws Exception {
  mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  mvc.perform(get("/api/admin/groups")).andExpect(status().isForbidden());
  mvc.perform(get("/actuator/metrics")).andExpect(status().isForbidden());
 }
}
