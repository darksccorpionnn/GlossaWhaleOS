// SPDX-License-Identifier: AGPL-3.0-only
package com.darkscorpion.glossasub.tv;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.Locale;

/**
 * Lightweight remote-first WhaleOS UI.
 * No animated layers, card shadows, blur, gradients, embedded browser,
 * fullscreen framebuffer, or active polling. Every command has a strong
 * D-pad selection state; focus is restored after returning from HDMI.
 */
public final class TvActivity extends Activity {
    private static final int BG=0xFF091528;
    private static final int PANEL=0xFF15243B;
    private static final int BUTTON=0xFF20344F;
    private static final int EDGE=0xFF36546C;
    private static final int CYAN=0xFF63F0DF;
    private static final int WHITE=0xFFF6FAFF;
    private static final int MUTED=0xFFACBFD4;
    private static final int GOLD=0xFFF7D778;

    private TextView statusView,permissionView,ipView,focusHint;
    private TextView btnHdmi,btnStart,btnDemo,btnStop;
    private String pin;
    private boolean receiverRegistered=false,manualStopped=false,autoStarted=false;

    private final BroadcastReceiver events=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            String state=intent.getStringExtra("status");
            if(state!=null && statusView!=null)statusView.setText(state);
        }
    };

    private int dp(float n) {
        return (int)(getResources().getDisplayMetrics().density*n+.5f);
    }

    private GradientDrawable shape(int color,int border,int radius,int borderWidth) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(borderWidth),border);
        return d;
    }

    private TextView text(String s,int size,int color,boolean strong) {
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }

    private void gap(LinearLayout parent,int height) {
        parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));
    }

    private LinearLayout vertical() {
        LinearLayout view=new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout panel(int color) {
        LinearLayout p=vertical();
        p.setPadding(dp(20),dp(20),dp(20),dp(20));
        p.setBackground(shape(color,color,16,0));
        return p;
    }

    private TextView action(String title,String hint) {
        TextView btn=text(title,19,WHITE,true);
        btn.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        btn.setPadding(dp(22),dp(13),dp(20),dp(13));
        btn.setMinHeight(dp(69));
        btn.setFocusable(true);
        btn.setFocusableInTouchMode(false);
        btn.setClickable(true);

        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
            shape(CYAN,WHITE,11,3));
        states.addState(new int[]{android.R.attr.state_pressed},
            shape(0xFFC2FFF7,WHITE,11,3));
        states.addState(new int[]{},
            shape(BUTTON,EDGE,11,1));
        btn.setBackground(states);
        int[][] colorStates=new int[][] {
            new int[]{android.R.attr.state_focused},
            new int[]{android.R.attr.state_pressed},
            new int[]{}
        };
        btn.setTextColor(new ColorStateList(colorStates,
            new int[]{BG,BG,WHITE}));
        btn.setContentDescription(title+" • "+hint);
        btn.setOnFocusChangeListener((view,hasFocus) -> {
            if(hasFocus && focusHint!=null)
                focusHint.setText("SEÇİLİ: "+hint+"    •    OK tuşuyla aç");
        });
        btn.setId(View.generateViewId());
        return btn;
    }

    private void addAction(LinearLayout parent,TextView action) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,dp(69));
        p.bottomMargin=dp(10);
        parent.addView(action,p);
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        SharedPreferences prefs=getSharedPreferences("glossa_v2_tv",MODE_PRIVATE);
        pin=prefs.getString("pin","");
        if(!pin.matches("[0-9]{6}")) {
            pin=String.format(Locale.US,"%06d",new SecureRandom().nextInt(1000000));
            prefs.edit().putString("pin",pin).apply();
        }
        DisplayMetrics display=getResources().getDisplayMetrics();
        boolean wide=display.widthPixels/display.density>=710;

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root=vertical();
        root.setPadding(dp(28),dp(22),dp(28),dp(16));
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("GLOSSAWHALE OS   /   TV",31,WHITE,true);
        header.addView(brand,new LinearLayout.LayoutParams(0,dp(47),1));
        TextView chip=text("●  HAFİF MOD  ·  3.2",14,CYAN,true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12),dp(9),dp(12),dp(9));
        chip.setBackground(shape(PANEL,EDGE,8,1));
        header.addView(chip);
        root.addView(header);
        TextView subtitle=text("PS4 CANLI TÜRKÇE ALTYAZI  •  KUMANDAYLA KONTROL",14,MUTED,false);
        root.addView(subtitle);
        gap(root,18);

        LinearLayout content=new LinearLayout(this);
        content.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        root.addView(content);

        LinearLayout pairing=panel(PANEL);
        pairing.addView(text("TELEFON BAĞLANTISI",16,CYAN,true));
        gap(pairing,14);
        pairing.addView(text("TV IP ADRESİ",12,MUTED,true));
        gap(pairing,4);
        ipView=text(findLanAddress(),26,WHITE,true);
        pairing.addView(ipView);
        gap(pairing,15);
        pairing.addView(text("6 HANELİ EŞLEŞTİRME KODU",12,MUTED,true));
        gap(pairing,4);
        TextView code=text(pin,40,GOLD,true);
        pairing.addView(code);
        gap(pairing,17);
        pairing.addView(text("Telefon uygulamasında bu IP ve kodu gir.",14,MUTED,false));
        gap(pairing,14);
        permissionView=text("Altyazı izni kontrol ediliyor…",14,CYAN,true);
        pairing.addView(permissionView);
        gap(pairing,12);
        statusView=text("TV alıcısı başlatılıyor…",14,WHITE,false);
        statusView.setMaxLines(3);
        pairing.addView(statusView);

        LinearLayout controls=panel(PANEL);
        controls.addView(text("NE YAPMAK İSTİYORSUN?",16,CYAN,true));
        gap(controls,7);
        focusHint=text("Kumandadan yukarı/aşağı ile seç, OK ile aç.",13,MUTED,false);
        focusHint.setMinHeight(dp(32));
        controls.addView(focusHint);
        gap(controls,10);
        btnHdmi=action("▶   HDMI GÖRÜNTÜSÜNÜ AÇ","HDMI görüntüsü");
        btnStart=action("●   ALTYAZI ALICISINI BAŞLAT","Altyazı alıcısını başlat");
        btnDemo=action("▣   ÖRNEK ALTYAZI GÖSTER","Örnek Türkçe altyazı");
        btnStop=action("■   ALICIYI DURDUR","Alıcıyı durdur");
        addAction(controls,btnHdmi);
        addAction(controls,btnStart);
        addAction(controls,btnDemo);
        addAction(controls,btnStop);

        if(wide) {
            LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,43);
            left.rightMargin=dp(14);
            content.addView(pairing,left);
            content.addView(controls,new LinearLayout.LayoutParams(0,-2,57));
        } else {
            content.addView(pairing,new LinearLayout.LayoutParams(-1,-2));
            gap(content,14);
            content.addView(controls,new LinearLayout.LayoutParams(-1,-2));
        }

        // Explicit up/down links ensure deterministic D-pad behavior even on
        // WhaleOS builds that ignore Android's auto-nearest focus algorithm.
        TextView[] actions={btnHdmi,btnStart,btnDemo,btnStop};
        for(int i=0;i<actions.length;i++) {
            actions[i].setNextFocusUpId(actions[Math.max(0,i-1)].getId());
            actions[i].setNextFocusDownId(actions[Math.min(i+1,actions.length-1)].getId());
            actions[i].setNextFocusLeftId(actions[i].getId());
            actions[i].setNextFocusRightId(actions[i].getId());
        }

        btnHdmi.setOnClickListener(v -> startActivity(new Intent(this,HdmiActivity.class)));
        btnStart.setOnClickListener(v -> {
            manualStopped=false;
            startReceiver();
        });
        btnDemo.setOnClickListener(v -> {
            Intent test=new Intent(this,SubtitleReceiverService.class);
            test.setAction(SubtitleReceiverService.TEST);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(test); else startService(test);
        });
        btnStop.setOnClickListener(v -> {
            manualStopped=true;
            autoStarted=false;
            stopService(new Intent(this,SubtitleReceiverService.class));
            statusView.setText("Alıcı durduruldu • Başlat ile tekrar açabilirsin");
        });

        gap(root,16);
        TextView footer=text("▲ ▼  SEÇİM    •    OK  ONAY    •    GERİ  ÖNCEKİ EKRAN",13,MUTED,false);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(footer);
        setContentView(scroll);

        // Android TVs can open an unfocused view; make the primary action
        // visibly selected immediately rather than making the user guess.
        btnHdmi.post(() -> btnHdmi.requestFocus());
    }

    private void startReceiver() {
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)) {
            statusView.setText("Altyazı izni gerekiyor • Lütfen izin ver");
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:"+getPackageName())));
            } catch(Exception e) {
                statusView.setText("Bu TV izin ekranını açmıyor. Sistem ayarlarından aç.");
            }
            return;
        }
        Intent start=new Intent(this,SubtitleReceiverService.class);
        start.setAction(SubtitleReceiverService.START);
        try {
            if(Build.VERSION.SDK_INT>=26) startForegroundService(start);
            else startService(start);
            autoStarted=true;
            statusView.setText("✓ Altyazı alıcısı açılıyor • Ağ portu 8799");
        } catch(Exception e) {
            statusView.setText("Alıcı başlatılamadı: "+e.getClass().getSimpleName());
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if(permissionView!=null)
            permissionView.setText(Build.VERSION.SDK_INT<23||Settings.canDrawOverlays(this)
                ?"✓ Altyazı katmanı izni açık"
                :"⚠ Altyazı katmanına izin gerekli");
        if(ipView!=null) ipView.setText(findLanAddress());
        if(!manualStopped&&!autoStarted &&
                (Build.VERSION.SDK_INT<23||Settings.canDrawOverlays(this)))startReceiver();
        if(!receiverRegistered) {
            registerReceiver(events,new IntentFilter(SubtitleReceiverService.STATUS));
            receiverRegistered=true;
        }
    }

    @Override protected void onPause() {
        if(receiverRegistered) {
            unregisterReceiver(events);
            receiverRegistered=false;
        }
        super.onPause();
    }

    static String findLanAddress() {
        try {
            Enumeration<NetworkInterface> list=NetworkInterface.getNetworkInterfaces();
            while(list.hasMoreElements()) {
                NetworkInterface iface=list.nextElement();
                if(!iface.isUp()||iface.isLoopback())continue;
                Enumeration<InetAddress> ips=iface.getInetAddresses();
                while(ips.hasMoreElements()) {
                    InetAddress ip=ips.nextElement();
                    if(ip instanceof Inet4Address&&ip.isSiteLocalAddress())
                        return ip.getHostAddress();
                }
            }
        } catch(Exception ignored) {}
        return "Wi-Fi bağlantısı yok";
    }
}
