package com.aitranslator.translation;
public enum Language { KO, TH;
 public Language opposite(){return this==KO ? TH : KO;}
}
