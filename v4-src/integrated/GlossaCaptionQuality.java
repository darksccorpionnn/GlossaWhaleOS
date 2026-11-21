// SPDX-License-Identifier: AGPL-3.0-only
package com.metallic.chiaki.glossa;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative, privacy-preserving OCR cleanup and on-device terminology fixes. */
public final class GlossaCaptionQuality {
    private GlossaCaptionQuality(){}

    public static String cleanOcr(String raw) {
        if(raw==null)return "";
        String result=Normalizer.normalize(raw,Normalizer.Form.NFKC)
            .replace("\u00AD","").replace("\u200B","")
            .replace('\r',' ').replace('\n',' ')
            .replaceAll("[\\u0000-\\u001F]"," ")
            .replaceAll("\\s+"," ").trim();
        if(result.startsWith("• "))result=result.substring(2).trim();
        // Misrecognized duplicate HUD labels should not enter the translator.
        String upper=result.toUpperCase(Locale.ROOT);
        if(upper.matches("(?:MAP|HELP|PROGRESS|PLAYER|STORY|ONLINE|SOCIAL CLUB|SETTINGS|PHOTO MODE)(?:\\s+(?:MAP|HELP|PROGRESS|PLAYER|STORY|ONLINE|SOCIAL CLUB|SETTINGS|PHOTO MODE))*"))
            return "";
        return result;
    }

    public static String polish(String source,String translated,String game) {
        if(translated==null)return "";
        String t=translated.replace('\n',' ').replaceAll("\\s+"," ").trim();
        t=t.replaceAll("\\s+([.,!?;:])","$1");
        t=t.replaceAll("([!?])\\1{2,}","$1");
        if(t.isEmpty())return t;
        if("RDR 2".equals(game)) {
            // Apply contextual name fixes only if the source explicitly names
            // the corresponding proper noun. These are corrections of known
            // accidental common-word translations, not an AI paraphrase.
            if(containsName(source,"Dutch"))
                t=t.replaceAll("(?iu)(?<!\\p{L})(Hollandalı|Flemenk)(?!\\p{L})","Dutch");
            if(containsName(source,"Blackwater"))
                t=t.replaceAll("(?iu)(?<!\\p{L})(Siyah\\s*su)(?!\\p{L})","Blackwater");
            if(containsName(source,"Valentine"))
                t=t.replaceAll("(?iu)(?<!\\p{L})(Sevgililer\\s*Günü)(?!\\p{L})","Valentine");
        }
        return t;
    }

    private static boolean containsName(String text,String name) {
        if(text==null)return false;
        return Pattern.compile("(?i)(?<![A-Za-z])"+Pattern.quote(name)+"(?![A-Za-z])")
            .matcher(text).find();
    }

    public static int minimumStableFrames(int mode,int textLength) {
        // Speed mode lets clear long lines through immediately.
        if(mode==0)return textLength>=27?1:2;
        if(mode==2)return 3;
        return 2;
    }
}
