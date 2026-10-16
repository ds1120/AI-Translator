package com.aitranslator.line;
public record StoredEvent(String eventId,String groupId,String replyToken,String originalText,long receivedAt,long eventTime,String state) {}
