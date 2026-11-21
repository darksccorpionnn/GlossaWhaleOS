// SPDX-License-Identifier: AGPL-3.0-only
package com.metallic.chiaki.glossa;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Functional, persistently saved five-preset + custom RGB theme editor. */
public final class GlossaThemeActivity extends Activity {
    private LinearLayout page,palette;
    private TextView sample,redValue,greenValue,blueValue;
    private SeekBar red,green,blue;
    private GlossaTheme theme;
    private int dp(int n){return GlossaTheme.dp(this,n);}
    private void gap(LinearLayout p,int n){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private TextView label(String txt,int size,boolean strong,int color){
        return theme.text(this,txt,size,strong,color);
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        render();
    }
    private void render(){
        theme=GlossaTheme.load(this);
        getWindow().setStatusBarColor(theme.bg);
        getWindow().setNavigationBarColor(theme.bg);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(theme.bg);scroll.setFillViewport(true);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24),dp(30),dp(24),dp(35));
        scroll.addView(page);
        page.addView(label("GLOSSA  /  GÖRÜNÜM",12,true,theme.accent));
        gap(page,12);
        page.addView(label("Arayüz renkleri",28,true,theme.white));
        gap(page,8);
        page.addView(label("Temanı seç. Renkler kaydedilir ve Glossa ekranlarına uygulanır.",
            14,false,theme.muted));
        gap(page,20);
        palette=new LinearLayout(this);palette.setOrientation(LinearLayout.VERTICAL);
        page.addView(palette);
        for(int i=0;i<GlossaTheme.NAMES.length;i++){
            final int index=i;
            GlossaTheme previewTheme=GlossaTheme.load(this);
            TextView option=theme.button(this,
                (theme.index==i&&!theme.custom?"✓  ":"◈  ")+GlossaTheme.NAMES[i],false);
            if(theme.index==i&&!theme.custom) option.setTextColor(theme.accent);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(51));
            lp.bottomMargin=dp(9);
            palette.addView(option,lp);
            option.setOnClickListener(v->{
                GlossaTheme.savePreset(this,index);
                render();
            });
        }
        gap(page,12);
        page.addView(label("Özel vurgu rengini oluştur",18,true,theme.white));
        gap(page,9);
        page.addView(label("Kırmızı, yeşil ve mavi kanallarını ayarla.",13,false,theme.muted));
        gap(page,12);
        red=slider("Kırmızı",theme.accent>>16&255); gap(page,8);
        green=slider("Yeşil",theme.accent>>8&255); gap(page,8);
        blue=slider("Mavi",theme.accent&255); gap(page,12);
        sample=label("●  GlossaWhaleOS  •  Oyunu başlat",18,true,theme.bg);
        sample.setGravity(Gravity.CENTER);
        sample.setMinHeight(dp(65));
        page.addView(sample);
        SeekBar.OnSeekBarChangeListener changes=new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar v,int p,boolean user){preview();}
            @Override public void onStartTrackingTouch(SeekBar v){}
            @Override public void onStopTrackingTouch(SeekBar v){}
        };
        red.setOnSeekBarChangeListener(changes);
        green.setOnSeekBarChangeListener(changes);
        blue.setOnSeekBarChangeListener(changes);
        preview();
        gap(page,13);
        TextView save=theme.button(this,"✓  Özel rengi kaydet",true);
        save.setOnClickListener(v->{
            GlossaTheme.saveCustom(this,red.getProgress(),green.getProgress(),blue.getProgress());
            render();
        });
        page.addView(save);
        gap(page,12);
        TextView back=theme.button(this,"←  Ana ekrana dön",false);
        back.setOnClickListener(v->finish());
        page.addView(back);
        setContentView(scroll);
    }
    private SeekBar slider(String title,int start){
        page.addView(label(title,14,true,theme.white));
        SeekBar s=new SeekBar(this);
        s.setMax(255);s.setProgress(start);
        page.addView(s);
        return s;
    }
    private void preview(){
        if(sample==null||red==null||green==null||blue==null)return;
        int col=Color.rgb(red.getProgress(),green.getProgress(),blue.getProgress());
        sample.setBackground(GlossaTheme.shape(this,col,14));
        double luminance=(.2126*Color.red(col)+.7152*Color.green(col)+.0722*Color.blue(col));
        sample.setTextColor(luminance>145?0xFF081528:0xFFFFFFFF);
    }
}
