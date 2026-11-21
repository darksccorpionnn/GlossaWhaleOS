// SPDX-License-Identifier: AGPL-3.0-only
package com.metallic.chiaki.glossa;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;

/** Game-screen controls. This panel never launches another APK or pauses PS4. */
public final class GlossaQuickPanel extends ScrollView {
    private final Activity activity;
    private final SharedPreferences prefs;
    private final Runnable close;
    private GlossaTheme theme;
    private LinearLayout root;
    private TextView example;
    private static final String[] COLORS={"Beyaz","Sarı","Turkuaz","Açık yeşil"};
    private static final int[] VALUES={0xFFFFFFFF,0xFFFFEB70,0xFF65E6D6,0xFFB6FFB6};
    private static final String[] BACKGROUNDS={"Koyu siyah","Yarı şeffaf","Şeffaf","Lacivert"};
    private static final int[] BG_VALUES={0xE5081020,0xAA081020,0x00000000,0xE5202642};
    private Spinner fontColor,background;
    private SeekBar textSize;
    private int dp(int n){return GlossaTheme.dp(activity,n);}
    private void gap(int n){root.addView(new View(activity),new LinearLayout.LayoutParams(1,dp(n)));}
    private void section(String header){
        gap(12);
        root.addView(theme.text(activity,header,16,true,theme.accent));
        gap(9);
    }
    private void rule(){
        View v=new View(activity);
        v.setBackgroundColor(0xFF37475A);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));
        p.topMargin=dp(13);root.addView(v,p);
    }
    private TextView small(String s) {
        return theme.text(activity,s,13,false,theme.muted);
    }
    private static int index(int[] opts,int color) {
        for(int i=0;i<opts.length;i++)if(opts[i]==color)return i;
        return 0;
    }
    private Spinner spinner(String[] labels,int selected){
        Spinner v=new Spinner(activity);
        ArrayAdapter<String> a=new ArrayAdapter<>(activity,
            android.R.layout.simple_spinner_dropdown_item,labels);
        v.setAdapter(a);
        v.setSelection(Math.max(0,Math.min(labels.length-1,selected)));
        root.addView(v,new LinearLayout.LayoutParams(-1,dp(52)));
        return v;
    }
    private SeekBar bar(int max,int value){
        SeekBar v=new SeekBar(activity);v.setMax(max);
        v.setProgress(Math.max(0,Math.min(max,value)));
        root.addView(v,new LinearLayout.LayoutParams(-1,dp(42)));
        return v;
    }
    private void watchedSpinner(Spinner sp,Runnable update){
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> p,View v,int index,long id){update.run();}
            @Override public void onNothingSelected(AdapterView<?> p){}
        });
    }
    private void watchedBar(SeekBar bar,Runnable update){
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int v,boolean fromUser){update.run();}
            @Override public void onStartTrackingTouch(SeekBar v){}
            @Override public void onStopTrackingTouch(SeekBar v){}
        });
    }
    public GlossaQuickPanel(Activity activity,Runnable close,Runnable editRoi){
        super(activity);
        this.activity=activity;
        this.close=close;
        prefs=activity.getSharedPreferences(GlossaTheme.PREF,Activity.MODE_PRIVATE);
        theme=GlossaTheme.load(activity);
        setBackground(GlossaTheme.bordered(activity,theme.bg,theme.accent,18,2));
        setFillViewport(false);
        setVerticalScrollBarEnabled(true);
        root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(18),dp(20),dp(20));
        addView(root);
        setVisibility(GONE);
        LinearLayout header=new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=theme.text(activity,"GLOSSA • CANLI AYARLAR",17,true,theme.white);
        header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView exit=theme.button(activity,"✕  Kapat",false);
        header.addView(exit,new LinearLayout.LayoutParams(dp(102),dp(48)));
        exit.setOnClickListener(v->close.run());
        root.addView(header);

        section("OCR OKUMA ALANI");
        root.addView(small("Kutuyu sürüklemek ve boyutlandırmak için oyunda aç."));
        gap(8);
        TextView box=theme.button(activity,"▣  OCR kutusunu düzenle",true);
        root.addView(box,new LinearLayout.LayoutParams(-1,dp(53)));
        box.setOnClickListener(v->editRoi.run());
        rule();

        section("TV ALTYAZI TASARIMI");
        root.addView(small("Yazı boyutu"));
        textSize=bar(36,prefs.getInt("style_size",26)-18);
        root.addView(small("Yatay konum"));
        SeekBar x=bar(100,prefs.getInt("style_x",50));
        root.addView(small("Dikey konum"));
        SeekBar y=bar(100,prefs.getInt("style_y",87));
        root.addView(small("Yazı rengi"));
        fontColor=spinner(COLORS,index(VALUES,prefs.getInt("style_color",VALUES[0])));
        root.addView(small("Arka plan"));
        background=spinner(BACKGROUNDS,index(BG_VALUES,prefs.getInt("style_background",BG_VALUES[0])));
        gap(6);
        example=theme.text(activity,"Buradan çıkmamız gerekiyor!",20,true,VALUES[0]);
        example.setGravity(Gravity.CENTER);
        example.setPadding(dp(14),dp(9),dp(14),dp(9));
        root.addView(example);
        Runnable updateStyle=()->{
            int fg=VALUES[fontColor.getSelectedItemPosition()];
            int bg=BG_VALUES[background.getSelectedItemPosition()];
            int size=18+textSize.getProgress();
            prefs.edit().putInt("style_color",fg).putInt("style_background",bg)
                .putInt("style_size",size).putInt("style_x",x.getProgress())
                .putInt("style_y",y.getProgress()).apply();
            example.setTextSize(Math.max(18,Math.min(size,32)));
            example.setTextColor(fg);
            example.setBackground(GlossaTheme.shape(activity,bg,9));
        };
        watchedBar(textSize,updateStyle);watchedBar(x,updateStyle);watchedBar(y,updateStyle);
        watchedSpinner(fontColor,updateStyle);watchedSpinner(background,updateStyle);
        updateStyle.run();
        rule();

        section("ÇEVİRİ KALİTESİ");
        root.addView(small("OCR doğrulama hızı"));
        String[] speed={"Hızlı • düşük gecikme","Dengeli • 2 kare doğrula",
            "Titiz • 3 kare doğrula"};
        Spinner mode=spinner(speed,prefs.getInt("quality_mode",0));
        watchedSpinner(mode,()->prefs.edit().putInt("quality_mode",
            mode.getSelectedItemPosition()).apply());
        root.addView(small("Oyun profili"));
        String[] profiles={"Genel","RDR 2"};
        Spinner game=spinner(profiles,"RDR 2".equals(prefs.getString("game_profile","Genel"))?1:0);
        watchedSpinner(game,()->prefs.edit().putString("game_profile",
            profiles[game.getSelectedItemPosition()]).apply());
        root.addView(small("RDR 2 profili, bazı karakter/yer adlarının yanlış çevrilmesini azaltır."));
        gap(7);
        CheckBox repeated=new CheckBox(activity);
        repeated.setText("Aynı cümleyi tekrar göstermeyi azalt");
        repeated.setTextColor(theme.white);
        repeated.setChecked(prefs.getBoolean("block_repeats",true));
        repeated.setOnCheckedChangeListener((v,checked)->
            prefs.edit().putBoolean("block_repeats",checked).apply());
        root.addView(repeated);
        rule();

        section("ARAYÜZ RENKLERİ");
        root.addView(small("Telefonun Glossa ekranlarında kullanılacak vurgu rengini seç."));
        Spinner themes=spinner(GlossaTheme.NAMES,theme.index);
        watchedSpinner(themes,()->{
            GlossaTheme.savePreset(activity,themes.getSelectedItemPosition());
        });
        root.addView(small("Özel RGB renkleri ana ekrandaki Tema bölümünden ayarlanır."));

        gap(16);
        TextView save=theme.button(activity,"✓  Kaydet ve oyuna dön",true);
        save.setOnClickListener(v->close.run());
        root.addView(save,new LinearLayout.LayoutParams(-1,dp(52)));
    }
    public void toggle(){
        setVisibility(getVisibility()==VISIBLE?GONE:VISIBLE);
    }
    public boolean isOpen(){return getVisibility()==VISIBLE;}
    public void dismiss(){setVisibility(GONE);}
}
