package com.aitranslator.group;
import org.apache.ibatis.annotations.*;
@Mapper
public interface GroupMapper {
 @Insert("INSERT INTO chat_groups(group_id,translation_enabled) VALUES(#{id},TRUE)") void insert(String id);
 @Select("SELECT translation_enabled FROM chat_groups WHERE group_id=#{id}") Boolean enabled(String id);
}
