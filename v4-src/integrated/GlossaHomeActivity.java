// SPDX-License-Identifier: AGPL-3.0-only
package com.metallic.chiaki.glossa;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.metallic.chiaki.main.MainActivity;
import com.metallic.chiaki.regist.RegistActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Genuine unified entry point. The same APK hosts Chiaki Remote Play,
 * registration, OCR, translation, LAN TV pairing, and theme controls.
 * No external Chiaki or old Glossa companion is launched.
 */
public final class GlossaHomeActivity extends Activity {
    private GlossaTheme theme;
    private LinearLayout root;
    private TextView tvState,lastState,accountState;
    private ExecutorService worker=Executors.newSingleThreadExecutor();
    private int dp(int x){return GlossaTheme.dp(this,x);}
    private void gap(LinearLayout p,int n){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private void add(LinearLayout p,View v,int bottom){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(bottom);
        p.addView(v,lp);
    }
    private LinearLayout card(String title,String subtitle){
        LinearLayout c=new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20),dp(20),dp(20),dp(20));
        c.setBackground(GlossaTheme.shape(this,theme.panel,20));
        add(c,theme.text(this,title,19,true,theme.white),8);
        add(c,theme.text(this,subtitle,13,false,theme.muted),5);
        return c;
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        createUI();
    }
    private void createUI(){
        theme=GlossaTheme.load(this);
        getWindow().setStatusBarColor(theme.bg);
        getWindow().setNavigationBarColor(theme.bg);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(theme.bg);
        scroll.setVerticalScrollBarEnabled(false);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22),dp(28),dp(22),dp(35));
        scroll.addView(root);
        add(root,theme.text(this,"DARK SCORPION  /  GLOSSA LAB",12,true,theme.accent),10);
        add(root,theme.text(this,"GlossaWhaleOS",31,true,theme.white),6);
        add(root,theme.text(this,"PS4 Canlı Türkçe Çeviri  •  4.0",15,false,theme.muted),22);

        LinearLayout statusCard=card("Bağlantı merkezi",
            "PS4 bağlantısı, TV alıcısı ve çeviri tek telefon uygulamasında.");
        TextView engine=theme.text(this,"● PS4 motoru hazır  ·  Chiaki dahili",15,true,theme.accent);
        add(statusCard,engine,11);
        String savedPsn=getSharedPreferences("glossa_psn",MODE_PRIVATE).getString("account_id","");
        accountState=theme.text(this,savedPsn.isEmpty()?
            "○ PSN Account ID eklenmedi":"● PSN Account ID kayıtlı",14,true,
            savedPsn.isEmpty()?theme.muted:theme.accent);
        add(statusCard,accountState,11);
        SharedPreferences p=getSharedPreferences(GlossaTheme.PREF,MODE_PRIVATE);
        String ip=p.getString("tv_ip","");
        tvState=theme.text(this,ip.isEmpty()?
            "○ TV henüz eşleştirilmedi":"◌ TV bağlantısı kontrol ediliyor…",15,true,theme.white);
        add(statusCard,tvState,10);
        lastState=theme.text(this,p.getString("last_state","Çeviri beklemede"),13,false,theme.muted);
        add(statusCard,lastState,0);
        add(root,statusCard,17);

        TextView start=theme.button(this,"▶  PS4'e bağlan ve oyunu aç",true);
        start.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));
        add(root,start,12);

        LinearLayout tools=card("Çeviri stüdyosu",
            "Altyazının nasıl okunacağını ve TV'de nasıl görüneceğini yönet.");
        TextView translation=theme.button(this,"⚙  OCR ve altyazı ayarları",false);
        translation.setOnClickListener(v->startActivity(new Intent(this,GlossaSettingsActivity.class)));
        add(tools,translation,11);
        TextView themes=theme.button(this,"◈  Renk temaları ve özel renk",false);
        themes.setOnClickListener(v->startActivity(new Intent(this,GlossaThemeActivity.class)));
        add(tools,themes,11);
        TextView psn=theme.button(this,"♙  PSN Hesabım / Account ID Al",true);
        psn.setOnClickListener(v->startActivity(new Intent(this,PsnAccountActivity.class)));
        add(tools,psn,11);
        TextView register=theme.button(this,"＋  PS4'e yeni hesap eşleştir",false);
        register.setOnClickListener(v->startActivity(new Intent(this,RegistActivity.class)));
        add(tools,register,0);
        add(root,tools,16);

        LinearLayout info=card("Nasıl çalışır?",
            "1. İkinci PSN hesabını PS4'e eşleştir.\n"+
            "2. TV'nin IP ve 6 haneli kodunu OCR ayarlarında kaydet.\n"+
            "3. PS4'e bağlan; ekranda OCR kutusunu konumlandır.\n"+
            "4. Çeviri otomatik olarak TV'ye gönderilir.\n\n"+
            "İlk kurulumda dil modeli indirilir. Canlı çeviri telefonda yapılır.");
        add(root,info,4);
        setContentView(scroll);
        refreshTv();
    }
    private void refreshTv(){
        SharedPreferences p=getSharedPreferences(GlossaTheme.PREF,MODE_PRIVATE);
        final String ip=p.getString("tv_ip","");
        final String code=p.getString("tv_pin","");
        if(!GlossaTvClient.validIp(ip)||!code.matches("[0-9]{6}"))return;
        worker.execute(()->{
            String result=GlossaTvClient.request(ip,code,null);
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||tvState==null)return;
                tvState.setText(result.equals("OK")?
                    "● TV bağlantısı hazır  ·  "+ip:"○ TV: "+result);
            });
        });
    }
    @Override protected void onResume(){
        super.onResume();
        if(root!=null && theme!=null){
            GlossaTheme newTheme=GlossaTheme.load(this);
            if(newTheme.index!=theme.index||newTheme.accent!=theme.accent)createUI();
            else {
                SharedPreferences p=getSharedPreferences(GlossaTheme.PREF,MODE_PRIVATE);
                if(lastState!=null) lastState.setText(p.getString("last_state","Çeviri beklemede"));
                if(accountState!=null){
                    boolean saved=!getSharedPreferences("glossa_psn",MODE_PRIVATE)
                        .getString("account_id","").isEmpty();
                    accountState.setText(saved?"● PSN Account ID kayıtlı":"○ PSN Account ID eklenmedi");
                    accountState.setTextColor(saved?theme.accent:theme.muted);
                }
                refreshTv();
            }
        }
    }
    @Override protected void onDestroy(){
        worker.shutdownNow();
        super.onDestroy();
    }
}
