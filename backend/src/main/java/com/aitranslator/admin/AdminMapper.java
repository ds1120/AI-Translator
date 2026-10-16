package com.aitranslator.admin;
import java.util.*;
import org.apache.ibatis.annotations.*;
@Mapper
public interface AdminMapper {
 record Run(String eventId,long processedAt,String outcome,long elapsedMs,String verificationStatus,int attempts){}
 record Setting(String settingKey,String settingValue){}
 @Insert("INSERT INTO translation_runs(event_id,processed_at,outcome,elapsed_ms,verification_status,attempts) VALUES(#{eventId},#{processedAt},#{outcome},#{elapsedMs},#{verificationStatus},#{attempts}) ON DUPLICATE KEY UPDATE outcome=VALUES(outcome)") void record(Run run);
 @Select("SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN outcome='SENT' THEN 1 ELSE 0 END),0) AS sent, COALESCE(SUM(CASE WHEN outcome IN ('FAILED','UNKNOWN','EXPIRED') THEN 1 ELSE 0 END),0) AS failed, COALESCE(AVG(elapsed_ms),0) AS average_ms FROM translation_runs") Map<String,Object> summary();
 @Select("SELECT outcome AS label,COUNT(*) AS total FROM translation_runs GROUP BY outcome") List<Map<String,Object>> outcomes();
 @Select("SELECT verification_status AS label,COUNT(*) AS total FROM translation_runs GROUP BY verification_status") List<Map<String,Object>> verifications();
 @Select("SELECT state AS label,COUNT(*) AS total FROM webhook_events WHERE state IN ('PENDING','PROCESSING') GROUP BY state") List<Map<String,Object>> queue();
 @Select("SELECT * FROM translation_runs ORDER BY processed_at DESC LIMIT 12") List<Run> recentRuns();
 @Select("SELECT * FROM admin_ai_settings") List<Setting> settings();
 @Insert("INSERT INTO admin_ai_settings(setting_key,setting_value) VALUES(#{key},#{value}) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value)") void saveSetting(@Param("key")String key,@Param("value")String value);
 @Select("SELECT ids.id,COALESCE(g.translation_enabled,TRUE) AS enabled,l.partner_language AS partner,q.verification_display AS display,q.context_limit AS context_size,d.default_language AS fallback FROM (SELECT group_id AS id FROM chat_groups UNION SELECT group_id AS id FROM chat_group_languages UNION SELECT conversation_id AS id FROM chat_quality_settings UNION SELECT conversation_id AS id FROM chat_default_languages) ids LEFT JOIN chat_groups g ON g.group_id=ids.id LEFT JOIN chat_group_languages l ON l.group_id=ids.id LEFT JOIN chat_quality_settings q ON q.conversation_id=ids.id LEFT JOIN chat_default_languages d ON d.conversation_id=ids.id ORDER BY ids.id LIMIT 200") List<Map<String,Object>> conversations();
 @Select("SELECT COUNT(*) FROM (SELECT group_id AS id FROM chat_groups UNION SELECT group_id AS id FROM chat_group_languages UNION SELECT conversation_id AS id FROM chat_quality_settings UNION SELECT conversation_id AS id FROM chat_default_languages) ids") long conversationCount();
 @Delete("DELETE FROM translation_runs WHERE processed_at < #{before}") void purge(long before);
}
