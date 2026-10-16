package com.aitranslator.group;
import org.apache.ibatis.annotations.*;
@Mapper
public interface GroupMapper {
 @Insert("INSERT INTO chat_groups(group_id,translation_enabled) VALUES(#{id},TRUE)") void insert(String id);
 @Select("SELECT translation_enabled FROM chat_groups WHERE group_id=#{id}") Boolean enabled(String id);
 @Select("SELECT partner_language FROM chat_group_languages WHERE group_id=#{id}") String partnerLanguage(String id);
 @Insert("INSERT INTO chat_group_languages(group_id,partner_language) VALUES(#{id},#{language}) ON DUPLICATE KEY UPDATE partner_language=VALUES(partner_language)")
 void setPartnerLanguage(@Param("id")String id,@Param("language")String language);
 @Select("SELECT default_language FROM chat_default_languages WHERE conversation_id=#{id}") String defaultLanguage(String id);
 @Insert("INSERT INTO chat_default_languages(conversation_id,default_language) VALUES(#{id},#{language}) ON DUPLICATE KEY UPDATE default_language=VALUES(default_language)")
 void setDefaultLanguage(@Param("id")String id,@Param("language")String language);
 @Update("UPDATE chat_groups SET translation_enabled=#{enabled} WHERE group_id=#{id}")
 void setEnabled(@Param("id")String id,@Param("enabled")boolean enabled);
 @Select("SELECT verification_display FROM chat_quality_settings WHERE conversation_id=#{id}") String verificationDisplay(String id);
 @Select("SELECT context_limit FROM chat_quality_settings WHERE conversation_id=#{id}") Integer contextLimit(String id);
 @Insert("INSERT INTO chat_quality_settings(conversation_id,verification_display) VALUES(#{id},#{display}) ON DUPLICATE KEY UPDATE verification_display=VALUES(verification_display)")
 void setVerificationDisplay(@Param("id")String id,@Param("display")String display);
 @Insert("INSERT INTO chat_quality_settings(conversation_id,context_limit) VALUES(#{id},#{limit}) ON DUPLICATE KEY UPDATE context_limit=VALUES(context_limit)")
 void setContextLimit(@Param("id")String id,@Param("limit")int limit);
 @Delete("DELETE FROM chat_default_languages WHERE conversation_id=#{id}") void clearDefaultLanguage(String id);
}
