package com.aitranslator.translation;
public enum Language {
 KO("Korean", "🇰🇷"), TH("Thai", "🇹🇭"), EN("English", "🇺🇸"), JA("Japanese", "🇯🇵");
 private final String displayName; private final String flag;
 Language(String displayName,String flag){this.displayName=displayName;this.flag=flag;}
 public String displayName(){return displayName;}
 public String flag(){return flag;}
 public Language opposite(){return this==KO ? TH : KO;}
}
