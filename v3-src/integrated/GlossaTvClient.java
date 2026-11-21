// SPDX-License-Identifier: AGPL-3.0-only
package com.metallic.chiaki.glossa;

import org.json.JSONObject;
import android.content.SharedPreferences;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Sends only translated text to the paired GlossaWhaleOS TV on the local network. */
public final class GlossaTvClient {
    private GlossaTvClient() {}

    public static boolean validIp(String ip) {
        if(ip==null) return false;
        String[] parts=ip.trim().split("\\.",-1);
        if(parts.length!=4) return false;
        int[] n=new int[4];
        try {
            for(int i=0;i<4;i++) {
                if(parts[i].isEmpty() || parts[i].length()>3) return false;
                n[i]=Integer.parseInt(parts[i]);
                if(n[i]<0 || n[i]>255) return false;
            }
        } catch(Exception e) {return false;}
        return n[0]==10 || n[0]==192 && n[1]==168 ||
            n[0]==172 && n[1]>=16 && n[1]<=31;
    }

    public static String request(String ip,String pin,String text) {
        return request(ip,pin,text,null);
    }

    public static String request(String ip,String pin,String text,SharedPreferences settings) {
        if(!validIp(ip) || pin==null || !pin.matches("[0-9]{6}"))
            return "TV IP veya 6 haneli kod geçersiz";
        HttpURLConnection c=null;
        try {
            String route=text==null?"/health?pin=":"/subtitle?pin=";
            c=(HttpURLConnection)new URL("http://"+ip+":8799"+route+pin).openConnection();
            c.setConnectTimeout(1500);
            c.setReadTimeout(1500);
            c.setUseCaches(false);
            if(text==null) {
                c.setRequestMethod("GET");
            } else {
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type","application/json; charset=utf-8");
                JSONObject payload=new JSONObject().put("text",text).put("duration",3600);
                if(settings!=null) {
                    JSONObject style=new JSONObject()
                        .put("size",settings.getInt("style_size",26))
                        .put("color",settings.getInt("style_color",0xFFFFFFFF))
                        .put("background",settings.getInt("style_background",0xE5081020))
                        .put("x",settings.getInt("style_x",50))
                        .put("y",settings.getInt("style_y",87));
                    payload.put("style",style);
                }
                byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream out=c.getOutputStream()) {out.write(bytes);}
            }
            int code=c.getResponseCode();
            if(code==200) return "OK";
            if(code==403) return "TV PIN eşleşmiyor";
            return "TV HTTP "+code;
        } catch(Exception e) {
            return "TV'ye ulaşılamadı; alıcı açık mı?";
        } finally {
            if(c!=null) c.disconnect();
        }
    }
}
