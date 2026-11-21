package com.darkscorpion.glossasub.tv;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;

public final class TvActivity extends Activity {
    private TextView statusView;
    private TextView permissionView;
    private TextView ipView;
    private TextView pinView;
    private String pin;
    private boolean receiverRegistered=false;
    private final BroadcastReceiver events=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent intent) {
            if(statusView!=null) {
                String state=intent.getStringExtra("status");
                if(state!=null) statusView.setText(state);
            }
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(GlossaUI.BG);
        getWindow().setNavigationBarColor(GlossaUI.BG);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        pin=getSharedPreferences("glossa_v2_tv",MODE_PRIVATE).getString("pin","");
        if(!pin.matches("[0-9]{6}")) {
            pin=String.format(Locale.US,"%06d",new SecureRandom().nextInt(1000000));
            getSharedPreferences("glossa_v2_tv",MODE_PRIVATE).edit().putString("pin",pin).apply();
        }
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(GlossaUI.BG);
        LinearLayout root=GlossaUI.column(this);
        GlossaUI.pad(root,this,45,34,45,38);
        scroll.addView(root);

        LinearLayout top=GlossaUI.row(this);
        LinearLayout branding=GlossaUI.column(this);
        GlossaUI.add(branding,GlossaUI.text(this,"DARK SCORPION  /  GLOSSA LAB",13,GlossaUI.TEAL,true));
        GlossaUI.gap(branding,this,10);
        GlossaUI.add(branding,GlossaUI.text(this,"GlossaWhaleOS",38,GlossaUI.WHITE,true));
        GlossaUI.add(branding,GlossaUI.text(this,"TV STUDIO  ·  V2.0",16,GlossaUI.MUTED,false));
        top.addView(branding,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView ready=GlossaUI.text(this,"●  HAFİF TV UYGULAMASI",14,GlossaUI.TEAL,true);
        ready.setBackground(GlossaUI.solid(0xFF173E47,15,this));
        GlossaUI.pad(ready,this,17,13,17,13);
        top.addView(ready);
        root.addView(top,GlossaUI.spaced(this,22));

        LinearLayout hero=GlossaUI.column(this);
        hero.setBackground(GlossaUI.gradient(this,0xFF23586B,0xFF343071,26));
        GlossaUI.pad(hero,this,26,25,26,25);
        GlossaUI.add(hero,GlossaUI.text(this,"Oyunun ritmini bozma.",29,GlossaUI.WHITE,true));
        GlossaUI.gap(hero,this,9);
        GlossaUI.add(hero,GlossaUI.text(this,"Türkçe altyazılar, HDMI görüntüsünün üzerinde. Ağır video çözme işi televizyona yüklenmez.",16,GlossaUI.WHITE,false));
        GlossaUI.gap(hero,this,14);
        statusView=GlossaUI.text(this,"Alıcı başlatılmayı bekliyor",15,GlossaUI.TEAL,true);
        GlossaUI.add(hero,statusView);
        root.addView(hero,GlossaUI.spaced(this,22));

        LinearLayout two=GlossaUI.row(this);
        LinearLayout connection=GlossaUI.card(this,"01  ·  Telefon eşleştirme",
            "Telefon uygulamasına aşağıdaki yerel ağ bilgilerini gir.");
        ipView=GlossaUI.text(this,findLanAddress(),28,GlossaUI.WHITE,true);
        GlossaUI.add(connection,GlossaUI.text(this,"TV IP ADRESİ",12,GlossaUI.MUTED,true));
        GlossaUI.gap(connection,this,5);
        GlossaUI.add(connection,ipView);
        GlossaUI.gap(connection,this,20);
        GlossaUI.add(connection,GlossaUI.text(this,"ALTİ HANELİ KOD",12,GlossaUI.MUTED,true));
        GlossaUI.gap(connection,this,5);
        pinView=GlossaUI.text(this,pin,40,GlossaUI.TEAL,true);
        GlossaUI.add(connection,pinView);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
        left.rightMargin=GlossaUI.dp(this,18);
        two.addView(connection,left);

        LinearLayout controls=GlossaUI.card(this,"02  ·  HDMI altyazı sistemi",
            "Şeffaf altyazı katmanını etkinleştir, ardından kumandayla HDMI girişine dönebilirsin.");
        TextView enable=GlossaUI.button(this,"▶   Alıcı ve şeffaf katmanı başlat",true);
        enable.setOnClickListener(v -> startReceiver());
        controls.addView(enable,GlossaUI.spaced(this,11));
        TextView hdmi=GlossaUI.button(this,"HDMI'yi uygulama içinde açmayı dene",false);
        hdmi.setOnClickListener(v -> startActivity(new Intent(this,HdmiActivity.class)));
        controls.addView(hdmi,GlossaUI.spaced(this,11));
        TextView demo=GlossaUI.button(this,"Örnek Türkçe altyazı göster",false);
        demo.setOnClickListener(v -> startService(new Intent(this,SubtitleReceiverService.class)
            .setAction(SubtitleReceiverService.TEST)));
        controls.addView(demo,GlossaUI.spaced(this,11));
        TextView stop=GlossaUI.button(this,"■   Alıcıyı durdur",false);
        stop.setOnClickListener(v -> {
            stopService(new Intent(this,SubtitleReceiverService.class));
            statusView.setText("Alıcı durduruldu");
        });
        GlossaUI.add(controls,stop);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
        two.addView(controls,right);
        root.addView(two,GlossaUI.spaced(this,20));

        LinearLayout foot=GlossaUI.card(this,"03  ·  Sistem durumu",
            "TV yalnızca eşleştirilen telefondan gelen Türkçe altyazıyı alır. PS4 fiziksel HDMI kablosuyla bağlı kalır; TV üzerinde Remote Play/Chiaki çalıştırılmaz.");
        permissionView=GlossaUI.text(this,"İzin kontrol ediliyor…",15,GlossaUI.TEAL,true);
        GlossaUI.add(foot,permissionView);
        GlossaUI.gap(foot,this,11);
        GlossaUI.add(foot,GlossaUI.text(this,
            "Not: Bazı televizyonların HDMI katmanı üçüncü taraf şeffaf pencerelerinin üzerinde kalabilir. Böyle bir durumda HDMI önizleme seçeneğini dene.",
            13,GlossaUI.MUTED,false));
        GlossaUI.add(root,foot);
        setContentView(scroll);
    }

    private void startReceiver() {
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)) {
            statusView.setText("Önce ekranın üzerinde gösterim iznini aç.");
            try {
                Intent permission=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:"+getPackageName()));
                startActivity(permission);
            } catch(Exception e) {
                statusView.setText("Bu TV'de şeffaf pencere izni açılamadı.");
            }
            return;
        }
        Intent intent=new Intent(this,SubtitleReceiverService.class);
        intent.setAction(SubtitleReceiverService.START);
        if(Build.VERSION.SDK_INT>=26) startForegroundService(intent); else startService(intent);
        statusView.setText("Alıcı başlatılıyor · TCP 8799");
    }

    @Override protected void onResume() {
        super.onResume();
        if(permissionView!=null) {
            boolean permitted=Build.VERSION.SDK_INT<23 || Settings.canDrawOverlays(this);
            permissionView.setText(permitted ? "✓  Ekran üzerine yazı izni verildi" :
                "⚠  Ekran üzerine yazı izni gerekiyor");
        }
        if(ipView!=null) ipView.setText(findLanAddress());
        if(!receiverRegistered) {
            registerReceiver(events,new IntentFilter(SubtitleReceiverService.STATUS));
            receiverRegistered=true;
        }
    }
    @Override protected void onPause() {
        if(receiverRegistered) {unregisterReceiver(events);receiverRegistered=false;}
        super.onPause();
    }

    static String findLanAddress() {
        try {
            Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
            while(interfaces.hasMoreElements()) {
                NetworkInterface ni=interfaces.nextElement();
                if(!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addresses=ni.getInetAddresses();
                while(addresses.hasMoreElements()) {
                    InetAddress a=addresses.nextElement();
                    if(a instanceof Inet4Address && a.isSiteLocalAddress())
                        return a.getHostAddress();
                }
            }
        } catch(Exception ignored) {}
        return "Wi-Fi / Ethernet bağlantısı yok";
    }
}
