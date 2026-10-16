package com.aitranslator.verification;
import java.util.List;
public record VerificationReport(Status status,String reverseTranslation,List<String> warnings,boolean dangerous,String model,boolean completed) {
 public enum Status { PASS, WARNING, FAIL }
 public VerificationReport { warnings=List.copyOf(warnings); }
 public static VerificationReport unavailable(String model){return new VerificationReport(Status.WARNING,"",List.of("UNAVAILABLE"),false,model,false);}
}
