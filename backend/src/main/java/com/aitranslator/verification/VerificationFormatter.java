package com.aitranslator.verification;
import com.aitranslator.translation.TranslationService.Result;
import com.aitranslator.translation.Language;
import java.util.Map;
public final class VerificationFormatter {
 private VerificationFormatter(){}
 private static final Map<String,String> LABELS=Map.ofEntries(Map.entry("NUMBER","숫자"),Map.entry("TIME_DATE","날짜·시간"),Map.entry("MONEY","금액"),Map.entry("NAME_PLACE","이름·장소"),Map.entry("NEGATION","긍정·부정"),Map.entry("INTENT","질문·명령 의도"),Map.entry("OMISSION","의미 누락"),Map.entry("ADDITION","정보 추가"),Map.entry("UNCERTAIN","의미 확인 필요"),Map.entry("UNAVAILABLE","검증 응답 없음"));
 public static String format(Language target,Result result,String display){
  if(result.blocked())return "❌ 의미 왜곡 가능성이 확인되어 번역문을 표시하지 않았습니다. 원문을 나눠 다시 보내거나 직접 확인해 주세요.";
  String text=target.flag()+" "+result.translatedText();
  boolean show=result.verified() && ("all".equals(display) || ("warnings".equals(display) && result.verificationStatus()!=VerificationReport.Status.PASS));
  boolean failure=result.verificationStatus()==VerificationReport.Status.FAIL || result.warnings().contains("UNAVAILABLE");
  if(show || failure){
   text+="\n\n"+switch(result.verificationStatus()){case PASS -> "✅";case WARNING -> "⚠️";case FAIL -> "❌";};
   if(show && !result.reverseTranslation().isBlank()){
    String reverse=result.reverseTranslation();if(reverse.length()>800)reverse=reverse.substring(0,800)+"…";
    text+=" "+reverse;
   }
   if(!result.warnings().isEmpty())text+="\n"+String.join(", ",result.warnings().stream().distinct().map(code->LABELS.getOrDefault(code,"확인 필요")).toList());
   if(!result.verified())text+="\n검증을 완료하지 못한 번역입니다.";
  }
  if(text.length()>4900)return "⚠️ 검증 정보를 포함한 번역문이 너무 깁니다. 원문을 나눠 다시 보내주세요.";
  return text;
 }
}
