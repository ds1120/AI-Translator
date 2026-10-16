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
@SpringBootTest(properties={"spring.config.location=classpath:/","app.worker.delay-ms=30","app.line.bot-user-id=our-bot"})
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class WebhookIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired MockLineClient line;
 @Autowired EventMapper events; @Autowired JdbcTemplate db;
 @BeforeEach void clear(){db.update("DELETE FROM webhook_events");db.update("DELETE FROM chat_group_languages");db.update("DELETE FROM chat_groups");line.clear();}
 private String body(String id,String text,String group,String user,String mode,long timestamp) throws Exception {
  return json.writeValueAsString(Map.of("events",List.of(Map.of("type","message","mode",mode,"webhookEventId",id,"timestamp",timestamp,
    "replyToken","test-token","source",Map.of("type","group","groupId",group,"userId",user),"message",Map.of("type","text","id","m1","text",text)))));
 }
 private String body(String id,String text)throws Exception{return body(id,text,"group-test","user-test","active",System.currentTimeMillis());}
 private String directBody(String id,String user,String text)throws Exception {
  return json.writeValueAsString(Map.of("events",List.of(Map.of("type","message","webhookEventId",id,"timestamp",System.currentTimeMillis(),
   "replyToken","test-token","source",Map.of("type","user","userId",user),"message",Map.of("type","text","text",text)))));
 }
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
  postBody("{\"events\":[{\"type\":\"message\",\"source\":{\"type\":\"room\"},\"message\":{\"type\":\"text\"}},{\"type\":\"message\",\"source\":{\"type\":\"group\"},\"message\":{\"type\":\"image\"}}]}");
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
 @Test void anyoneCanChangeOnlyTheirGroupAndTranslateBothWays()throws Exception {
  postBody(body("set-en","/언어 영어","english-group","member-a","active",System.currentTimeMillis()));finished("set-en","SENT");
  postBody(body("set-ja","/언어 일본어","japanese-group","member-b","active",System.currentTimeMillis()));finished("set-ja","SENT");
  postBody(body("en-out","안녕하세요","english-group","member-c","active",System.currentTimeMillis()));finished("en-out","SENT");
  assertThat(line.messages().getLast().text()).isEqualTo("[MOCK] 🇺🇸 Hello");
  postBody(body("en-in","Hello","english-group","member-d","active",System.currentTimeMillis()));finished("en-in","SENT");
  assertThat(line.messages().getLast().text()).isEqualTo("[MOCK] 🇰🇷 안녕하세요");
  postBody(body("ja-out","안녕하세요","japanese-group","member-c","active",System.currentTimeMillis()));finished("ja-out","SENT");
  assertThat(line.messages().getLast().text()).isEqualTo("[MOCK] 🇯🇵 こんにちは");
  postBody(body("ja-in","こんにちは","japanese-group","member-d","active",System.currentTimeMillis()));finished("ja-in","SENT");
  assertThat(line.messages().getLast().text()).isEqualTo("[MOCK] 🇰🇷 안녕하세요");
  postBody(body("default-th","안녕하세요"));finished("default-th","SENT");
  assertThat(line.messages().getLast().text()).isEqualTo("[MOCK] 🇹🇭 สวัสดีค่ะ");
  postBody(body("change-by-other","/언어 TH","english-group","member-d","active",System.currentTimeMillis()));finished("change-by-other","SENT");
  assertThat(db.queryForObject("SELECT partner_language FROM chat_group_languages WHERE group_id='english-group'",String.class)).isEqualTo("TH");
  assertThat(db.queryForObject("SELECT partner_language FROM chat_group_languages WHERE group_id='japanese-group'",String.class)).isEqualTo("JA");
 }
 @Test void settingQueryInvalidCommandAndUnselectedLanguage()throws Exception {
  postBody(body("query-default","/설정"));finished("query-default","SENT");
  assertThat(line.messages().getLast().text()).contains("한국어 ↔ 🇹🇭 태국어");
  postBody(body("set-alias","/언어 en"));finished("set-alias","SENT");
  postBody(body("invalid-language","/언어 중국어"));finished("invalid-language","SENT");
  assertThat(line.messages().getLast().text()).contains("사용법:");
  postBody(body("query-saved","/설정"));finished("query-saved","SENT");
  assertThat(line.messages().getLast().text()).contains("한국어 ↔ 🇺🇸 영어");
  postBody(body("unselected","こんにちは"));finished("unselected","IGNORED");
  assertThat(db.queryForObject("SELECT partner_language FROM chat_group_languages WHERE group_id='group-test'",String.class)).isEqualTo("EN");
 }
 @Test void duplicateCommandCannotOverwriteLaterSetting()throws Exception {
  String first=body("first-command","/언어 영어");postBody(first);finished("first-command","SENT");
  postBody(body("second-command","/언어 일본어"));finished("second-command","SENT");
  postBody(first);
  assertThat(line.messages()).hasSize(2);
  assertThat(db.queryForObject("SELECT partner_language FROM chat_group_languages WHERE group_id='group-test'",String.class)).isEqualTo("JA");
 }
 @Test void staleCommandDoesNotChangeSettingsOrReply()throws Exception {
  postBody(body("stale-command","/언어 영어","group-test","member","active",System.currentTimeMillis()-120_000));finished("stale-command","EXPIRED");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM chat_group_languages",Integer.class)).isZero();
  assertThat(line.messages()).isEmpty();
 }
 @Test void directChatShowsNumberedMenuAndSupportsAllFourPairs()throws Exception {
  postBody(directBody("dm-menu","user-a","/설정"));finished("dm-menu","SENT");
  assertThat(line.messages().getLast().text()).contains("1. 한국어 ↔ 태국어","2. 한국어 ↔ 베트남어","3. 한국어 ↔ 영어","4. 한국어 ↔ 일본어","/언어 1");
  String[] expected={"สวัสดีค่ะ","Xin chào","Hello","こんにちは"};
  String[] codes={"TH","VI","EN","JA"};
  for(int number=1;number<=4;number++) {
   String setId="dm-set-"+number;
   postBody(directBody(setId,"user-a","/언어 "+number));finished(setId,"SENT");
   assertThat(db.queryForObject("SELECT partner_language FROM chat_group_languages WHERE group_id='user-a'",String.class)).isEqualTo(codes[number-1]);
   postBody(directBody("dm-out-"+number,"user-a","안녕하세요"));finished("dm-out-"+number,"SENT");
   assertThat(line.messages().getLast().text()).endsWith(expected[number-1]);
   postBody(directBody("dm-in-"+number,"user-a",expected[number-1]));finished("dm-in-"+number,"SENT");
   assertThat(line.messages().getLast().text()).isEqualTo("[MOCK] 🇰🇷 안녕하세요");
  }
 }
 @Test void directChatSettingsAreIsolatedFromOtherUsersAndGroups()throws Exception {
  postBody(directBody("alice-set","user-alice","/언어 2"));finished("alice-set","SENT");
  postBody(directBody("bob-set","user-bob","/언어 3"));finished("bob-set","SENT");
  postBody(directBody("alice-query","user-alice","/설정"));finished("alice-query","SENT");
  assertThat(line.messages().getLast().text()).contains("한국어 ↔ 🇻🇳 베트남어");
  postBody(directBody("bob-query","user-bob","/설정"));finished("bob-query","SENT");
  assertThat(line.messages().getLast().text()).contains("한국어 ↔ 🇺🇸 영어");
  postBody(body("group-query","/설정"));finished("group-query","SENT");
  assertThat(line.messages().getLast().text()).contains("한국어 ↔ 🇹🇭 태국어");
  postBody(directBody("invalid-number","user-alice","/언어 5"));finished("invalid-number","SENT");
  assertThat(line.messages().getLast().text()).contains("사용법:","한국어 ↔ 🇻🇳 베트남어");
 }
 @Test void directChatRequiresUserId()throws Exception {
  String missing=directBody("missing-user","","/설정");
  mvc.perform(post("/webhooks/line").contentType("application/json").content(missing).header("X-Line-Signature",signature(missing))).andExpect(status().isBadRequest());
  assertThat(events.pending()).isEmpty();
 }
}
