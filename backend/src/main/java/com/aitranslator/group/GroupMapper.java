package com.aitranslator.group;
import org.apache.ibatis.annotations.*;
@Mapper
public interface GroupMapper {
 @Insert("INSERT INTO chat_groups(group_id,translation_enabled) VALUES(#{id},TRUE)") void insert(String id);
 @Select("SELECT translation_enabled FROM chat_groups WHERE group_id=#{id}") Boolean enabled(String id);
 @Select("SELECT partner_language FROM chat_group_languages WHERE group_id=#{id}") String partnerLanguage(String id);
 @Insert("INSERT INTO chat_group_languages(group_id,partner_language) VALUES(#{id},#{language}) ON DUPLICATE KEY UPDATE partner_language=VALUES(partner_language)")
 void setPartnerLanguage(@Param("id")String id,@Param("language")String language);
}
