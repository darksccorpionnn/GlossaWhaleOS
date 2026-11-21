package com.darkscorpion.glossasub.phone;

import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class RelayClient {
    static boolean validLocalIp(String host) {
        if(host==null) return false;
        String[] a=host.trim().split("\\.",-1);
        if(a.length!=4) return false;
        int[] n=new int[4];
        try {
            for(int i=0;i<4;i++) {
                if(a[i].length()==0 || a[i].length()>3) return false;
                n[i]=Integer.parseInt(a[i]);
                if(n[i]<0 || n[i]>255) return false;
            }
        } catch(Exception e) { return false; }
        return n[0]==10 || (n[0]==172 && n[1]>=16 && n[1]<=31)
            || (n[0]==192 && n[1]==168);
    }


    /** Lightweight authenticated LAN check; never creates an on-screen subtitle. */
    static String ping(String host,String pin) {
        if(!validLocalIp(host) || pin==null || !pin.matches("[0-9]{6}"))
            return "TV keşfedildi, ancak eşleştirme kodu eksik.";
        HttpURLConnection connection=null;
        try {
            connection=(HttpURLConnection)new URL("http://"+host+":8799/health?pin="+pin)
                .openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(2500);
            int code=connection.getResponseCode();
            if(code==200) return "✓  TV'ye otomatik bağlandı • Altyazı alıcısı hazır";
            if(code==403) return "TV bulundu • Eşleştirme kodunu güncelle.";
            return "TV bulundu ancak alıcı yanıtı: HTTP "+code;
        } catch(Exception error) {
            return "TV bulundu ancak alıcıya erişilemiyor. TV uygulamasını aç.";
        } finally {
            if(connection!=null) connection.disconnect();
        }
    }

    static String send(String host,String pin,String text) {
        if(!validLocalIp(host) || pin==null || !pin.matches("[0-9]{6}"))
            return "Bağlantı bilgileri geçersiz.";
        HttpURLConnection connection=null;
        try {
            URL url=new URL("http://"+host+":8799/subtitle?pin="+pin);
            connection=(HttpURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(3500);
            connection.setReadTimeout(3500);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
            byte[] body=new JSONObject().put("text",text)
                .put("duration",6200).toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try(OutputStream out=connection.getOutputStream()) { out.write(body); }
            int code=connection.getResponseCode();
            if(code==200) return "TV bağlantısı başarılı • Altyazı gönderildi";
            if(code==403) return "TV eşleştirme kodu yanlış.";
            return "TV yanıtı: HTTP "+code;
        } catch(Exception e) {
            return "TV'ye bağlanılamadı • IP, Wi-Fi ve TV alıcısını kontrol et";
        } finally {
            if(connection!=null) connection.disconnect();
        }
    }
}
