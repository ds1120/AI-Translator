package com.aitranslator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.nio.file.Path;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;
class GroupLanguagePersistenceTest {
 @TempDir Path directory;
 @Test void upgradesExistingDatabaseAndKeepsSettingsAfterReopening()throws Exception {
  String url="jdbc:h2:file:"+directory.resolve("translator").toString().replace('\\','/')+";MODE=MariaDB";
  try(var connection=DriverManager.getConnection(url,"sa","");var statement=connection.createStatement()) {
   statement.execute("CREATE TABLE chat_groups(group_id VARCHAR(128) PRIMARY KEY,translation_enabled BOOLEAN NOT NULL DEFAULT TRUE)");
   statement.execute("INSERT INTO chat_groups(group_id) VALUES('existing-group')");
   ScriptUtils.executeSqlScript(connection,new ClassPathResource("schema.sql"));
   statement.execute("INSERT INTO chat_group_languages(group_id,partner_language) VALUES('existing-group','JA')");
   statement.execute("INSERT INTO chat_default_languages(conversation_id,default_language) VALUES('existing-group','TH')");
  }
  try(var connection=DriverManager.getConnection(url,"sa","");var statement=connection.createStatement()) {
   ScriptUtils.executeSqlScript(connection,new ClassPathResource("schema.sql"));
   try(var result=statement.executeQuery("SELECT partner_language FROM chat_group_languages WHERE group_id='existing-group'")) {
    assertThat(result.next()).isTrue();assertThat(result.getString(1)).isEqualTo("JA");
   }
   try(var result=statement.executeQuery("SELECT translation_enabled FROM chat_groups WHERE group_id='existing-group'")) {
    assertThat(result.next()).isTrue();assertThat(result.getBoolean(1)).isTrue();
   }
   try(var result=statement.executeQuery("SELECT default_language FROM chat_default_languages WHERE conversation_id='existing-group'")) {
    assertThat(result.next()).isTrue();assertThat(result.getString(1)).isEqualTo("TH");
   }
  }
 }
}
