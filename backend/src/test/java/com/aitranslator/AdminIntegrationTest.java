package com.aitranslator;
import com.aitranslator.admin.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={"spring.config.location=classpath:/","spring.datasource.url=jdbc:h2:mem:admin-tests;MODE=MariaDB;DB_CLOSE_DELAY=-1","app.admin.password=test-admin-password","app.worker.delay-ms=10000","app.ai.gemini.key=gemini-test-fixture","app.ai.openai.key=openai-test-fixture"})
@AutoConfigureMockMvc @ActiveProfiles("mock")
class AdminIntegrationTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate db;@Autowired ConfigurableEnvironment environment;
 @Autowired AdminMapper mapper;@Autowired AdminAiSettings ai;@Autowired com.aitranslator.config.AppConfig.Settings settings;@Autowired PlatformTransactionManager manager;@Autowired AdminErrorLog errors;
 private record Session(MockHttpSession session,String token,String header){}
 @BeforeEach void clear(){for(String table:List.of("translation_runs","webhook_events","chat_quality_settings","chat_default_languages","chat_group_languages","chat_groups","admin_ai_settings"))db.update("DELETE FROM "+table);environment.getPropertySources().remove("admin-runtime-ai");}
 private Session csrf(MockHttpSession session)throws Exception {
  var request=get("/api/admin/csrf");if(session!=null)request.session(session);
  var response=mvc.perform(request).andExpect(status().isOk()).andReturn();var node=json.readTree(response.getResponse().getContentAsString());return new Session((MockHttpSession)response.getRequest().getSession(),node.path("token").asText(),node.path("headerName").asText());
 }
 private Session login()throws Exception {
  var before=csrf(null);var response=mvc.perform(post("/api/admin/login").session(before.session()).header(before.header(),before.token()).param("username","admin").param("password","test-admin-password")).andExpect(status().isOk()).andReturn();return csrf((MockHttpSession)response.getRequest().getSession());
 }
 @Test void anonymousRequestsCannotReadOrChangeAdminData()throws Exception {
  mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
  mvc.perform(put("/api/admin/ai").contentType("application/json").content("{}")).andExpect(status().isForbidden());
  var session=csrf(null);mvc.perform(post("/api/admin/login").session(session.session()).header(session.header(),session.token()).param("username","admin").param("password","wrong-password")).andExpect(status().isUnauthorized());
 }
 @Test void loginSessionCsrfAndLogoutWork()throws Exception {
  var session=login();mvc.perform(get("/api/admin/session").session(session.session())).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("admin"));
  mvc.perform(put("/api/admin/ai").session(session.session()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/logout").session(session.session()).header(session.header(),session.token())).andExpect(status().isNoContent());
  mvc.perform(get("/api/admin/session")).andExpect(status().isUnauthorized());
 }
 @Test void dashboardShowsOnlyTranslationAuditNotCommandsOrText()throws Exception {
  mapper.record(new AdminMapper.Run("fixture-event",1,"SENT",1200,"PASS",1));var session=login();
  mvc.perform(get("/api/admin/dashboard").session(session.session())).andExpect(status().isOk()).andExpect(jsonPath("$.summary.total").value(1)).andExpect(jsonPath("$.summary.sent").value(1)).andExpect(jsonPath("$.summary.average_ms").value(1200));
 }
 @Test void conversationSettingsSaveWithoutTouchingOtherConversations()throws Exception {
  db.update("INSERT INTO chat_groups(group_id) VALUES('Ualice'),('Cbob')");var session=login();
  String body=json.writeValueAsString(Map.of("enabled",false,"partnerLanguage","VI","defaultLanguage","KO","verificationDisplay","all","contextLimit",5));
  mvc.perform(put("/api/admin/conversations/Ualice").session(session.session()).header(session.header(),session.token()).contentType("application/json").content(body)).andExpect(status().isOk());
  mvc.perform(get("/api/admin/conversations").session(session.session())).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("Cbob")).andExpect(jsonPath("$[1].partner").value("VI"));
  assertThat(db.queryForObject("SELECT translation_enabled FROM chat_groups WHERE group_id='Ualice'",Boolean.class)).isFalse();assertThat(db.queryForObject("SELECT translation_enabled FROM chat_groups WHERE group_id='Cbob'",Boolean.class)).isTrue();
 }
 @Test void savesAndReloadsValidatedAiSettingsWithoutExposingSecrets()throws Exception {
  var session=login();String body=json.writeValueAsString(Map.of("provider","local-qwen","model","new-model","baseUrl","http://127.0.0.1:8082/v1","timeoutSeconds",10));
  var response=mvc.perform(put("/api/admin/ai").session(session.session()).header(session.header(),session.token()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.model").value("new-model")).andReturn();
  assertThat(response.getResponse().getContentAsString()).doesNotContain("app.line.secret","app.line.token","test-admin-password");
  environment.getPropertySources().remove("admin-runtime-ai");new AdminAiSettings(environment,mapper,settings,manager);assertThat(settings.model()).isEqualTo("new-model");
  mvc.perform(put("/api/admin/ai").session(session.session()).header(session.header(),session.token()).contentType("application/json").content(body.replace("127.0.0.1","public.example"))).andExpect(status().isBadRequest());
  assertThat(settings.aiBase()).isEqualTo("http://127.0.0.1:8082/v1");
 }
 @Test void testTranslationDoesNotSendLineMessagesOrCreateAudit()throws Exception {
  var session=login();mvc.perform(post("/api/admin/test").session(session.session()).header(session.header(),session.token()).contentType("application/json").content("{\"text\":\"안녕하세요\",\"sourceLanguage\":\"KO\",\"targetLanguage\":\"TH\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.translatedText").value("สวัสดีค่ะ")).andExpect(jsonPath("$.verificationStatus").value("PASS"));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM translation_runs",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM webhook_events",Integer.class)).isZero();
 }
 @Test void errorLogContainsOnlySafeMetadata()throws Exception {
  errors.record("AI",503,"HTTP_ERROR");var session=login();var response=mvc.perform(get("/api/admin/errors").session(session.session())).andExpect(status().isOk()).andReturn();
  assertThat(response.getResponse().getContentAsString()).contains("HTTP_ERROR","503").doesNotContain("Authorization","replyToken","originalText");
 }
 @Test void cloudSettingsPersistWithoutReturningOrSavingApiKeys()throws Exception {
  var session=login();
  for(String provider:List.of("gemini","openai")){
   String body=json.writeValueAsString(Map.of("provider",provider,"model",com.aitranslator.config.AppConfig.Settings.defaultModel(provider),"baseUrl",com.aitranslator.config.AppConfig.Settings.defaultBase(provider),"timeoutSeconds",20));
   var response=mvc.perform(put("/api/admin/ai").session(session.session()).header(session.header(),session.token()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.provider").value(provider)).andExpect(jsonPath("$.geminiKeyConfigured").value(true)).andExpect(jsonPath("$.openaiKeyConfigured").value(true)).andReturn();
   assertThat(response.getResponse().getContentAsString()).doesNotContain("gemini-test-fixture","openai-test-fixture");
  }
  assertThat(mapper.settings()).hasSize(4);assertThat(mapper.settings().toString()).doesNotContain("gemini-test-fixture","openai-test-fixture");
 }
}
