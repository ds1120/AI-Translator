package com.aitranslator.admin;
import org.springframework.stereotype.Component;
@Component
public class TranslationAudit {
 private final AdminMapper mapper;private final AdminErrorLog errors;
 public TranslationAudit(AdminMapper mapper,AdminErrorLog errors){this.mapper=mapper;this.errors=errors;}
 public void record(String id,String outcome,long elapsed,String verification,int attempts){
  try{mapper.record(new AdminMapper.Run(id,System.currentTimeMillis(),outcome,elapsed,verification,attempts));}
  catch(RuntimeException ex){errors.record("AUDIT",null,ex.getClass().getSimpleName());}
 }
 @org.springframework.scheduling.annotation.Scheduled(fixedDelay=3600000,initialDelay=60000)
 public void cleanup(){mapper.purge(System.currentTimeMillis()-7L*24*60*60*1000);}
}
