package com.aitranslator.admin;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class AdminErrorLog {
 public record Entry(String id,long occurredAt,String stage,Integer httpStatus,String type){}
 private final Deque<Entry> entries=new ArrayDeque<>();
 public synchronized void record(String stage,Integer status,String type){entries.addFirst(new Entry(UUID.randomUUID().toString(),System.currentTimeMillis(),stage,status,type));while(entries.size()>200)entries.removeLast();}
 public synchronized List<Entry> recent(){return List.copyOf(entries);}
}
