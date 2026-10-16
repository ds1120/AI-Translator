package com.aitranslator.line;
import java.util.List;
import org.apache.ibatis.annotations.*;
@Mapper
public interface EventMapper {
 @Insert("INSERT INTO webhook_events(event_id,group_id,reply_token,original_text,received_at,event_time,state) VALUES(#{eventId},#{groupId},#{replyToken},#{originalText},#{receivedAt},#{eventTime},'PENDING')")
 void insert(StoredEvent event);
 @Select("SELECT * FROM webhook_events WHERE state='PENDING' ORDER BY received_at LIMIT 8") List<StoredEvent> pending();
 @Update("UPDATE webhook_events SET state='PROCESSING' WHERE event_id=#{id} AND state='PENDING'") int claim(String id);
 @Update("UPDATE webhook_events SET state=#{state},original_text=NULL,reply_token=NULL,group_id=NULL,elapsed_ms=#{elapsed} WHERE event_id=#{id}")
 void complete(@Param("id")String id,@Param("state")String state,@Param("elapsed")long elapsed);
 @Select("SELECT state FROM webhook_events WHERE event_id=#{id}") String state(String id);
 @Update("UPDATE webhook_events SET state='UNKNOWN',original_text=NULL,reply_token=NULL,group_id=NULL WHERE state='PROCESSING' AND received_at < #{before}") int expireProcessing(long before);
 @Delete("DELETE FROM webhook_events WHERE received_at < #{before} AND state NOT IN ('PROCESSING','PENDING')") int purge(long before);
}
