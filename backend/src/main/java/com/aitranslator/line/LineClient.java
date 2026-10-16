package com.aitranslator.line;
public interface LineClient {
 void reply(String token,String text);
 void push(String group,String text,String retryKey);
}
