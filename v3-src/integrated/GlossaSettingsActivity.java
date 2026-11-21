// SPDX-License-Identifier: AGPL-3.0-only
package com.metallic.chiaki.glossa;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Built into the Chiaki activity stack; no companion PS4 app is needed. */
public final class GlossaSettingsActivity extends Activity {
    public static final String PREF="glossa_live_v3";
    private static final int BG=0xFF0C162C;
    private static final int WHITE=0xFFF3F8FF;
    private static final int MUTED=0xFFA6BBCD;
    private static final int CYAN=0xFF65E6D6;
    private static final int PANEL=0xFF172A43;
    private EditText ip, pin;
    private CheckBox active;
    private TextView state,preview,sizeLabel,xLabel,yLabel;
    private SeekBar sizeBar,xBar,yBar;
    private Spinner colorBox,backgroundBox;
    private static final int[] COLORS={0xFFFFFFFF,0xFFFFEB70,0xFF65E6D6,0xFFB6FFB6};
    private static final int[] BACKGROUNDS={0xE5081020,0xAA081020,0x00000000,0xE5202642};
    private static final String[] COLOR_NAMES={"Beyaz","Sarı","Turkuaz","Açık yeşil"};
    private static final String[] BG_NAMES={"Koyu siyah","Yarı şeffaf","Arka plan yok","Lacivert"};
    private ExecutorService worker=Executors.newSingleThreadExecutor();

    private int dp(int x) {return Math.round(getResources().getDisplayMetrics().density*x);}
    private GradientDrawable background(int color,int radius) {
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }
    private TextView txt(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        if(bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void pad(View v,int h,int vDp) {v.setPadding(dp(h),dp(vDp),dp(h),dp(vDp));}
    private void gap(LinearLayout l,int x) {
        View v=new View(this);
        l.addView(v,new LinearLayout.LayoutParams(1,dp(x)));
    }
    private EditText input(String hint,String value) {
        EditText field=new EditText(this);
        field.setText(value);
        field.setHint(hint);
        field.setHintTextColor(0xFF93A9BC);
        field.setTextColor(WHITE);
        field.setSingleLine(true);
        field.setSelectAllOnFocus(true);
        field.setBackground(background(0xFF203652,10));
        pad(field,15,14);
        field.setTextSize(16);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        return field;
    }
    private static int optionIndex(int[] options,int value) {
        for(int i=0;i<options.length;i++) if(options[i]==value)return i;
        return 0;
    }
    private Spinner choice(String[] options,int initial) {
        Spinner spinner=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(
            this,android.R.layout.simple_spinner_dropdown_item,options);
        spinner.setAdapter(adapter);
        spinner.setSelection(initial);
        return spinner;
    }
    private SeekBar slider(int max,int value) {
        SeekBar bar=new SeekBar(this);
        bar.setMax(max);bar.setProgress(Math.max(0,Math.min(max,value)));
        return bar;
    }
    private void updatePreview() {
        if(preview==null)return;
        int size=18+sizeBar.getProgress();
        sizeLabel.setText("Yazı büyüklüğü: "+size+" sp");
        xLabel.setText("Yatay konum: %"+xBar.getProgress());
        yLabel.setText("Dikey konum: %"+yBar.getProgress());
        preview.setTextSize(size);
        preview.setTextColor(COLORS[colorBox.getSelectedItemPosition()]);
        preview.setBackground(background(BACKGROUNDS[backgroundBox.getSelectedItemPosition()],12));
    }
    private Button button(String caption) {
        Button b=new Button(this);
        b.setText(caption);
        b.setAllCaps(false);
        b.setTextColor(BG);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(background(CYAN,12));
        return b;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        SharedPreferences prefs=getSharedPreferences(PREF,MODE_PRIVATE);
        ScrollView scroller=new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        pad(root,22,28);
        scroller.addView(root);

        root.addView(txt("GLOSSAWHALEOS  /  ULTRA LIVE",12,CYAN,true));
        gap(root,14);
        root.addView(txt("PS4 → Türkçe altyazı",27,WHITE,true));
        gap(root,10);
        root.addView(txt("Chiaki Remote Play, görüntüden OCR ve yerel Türkçe çeviri aynı uygulamanın içinde.",15,MUTED,false));
        gap(root,21);

        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(background(PANEL,18));
        pad(panel,18,18);
        panel.addView(txt("TV BAĞLANTISI",13,CYAN,true));
        gap(panel,9);
        panel.addView(txt("TV'de GlossaWhaleOS alıcısını aç; TV IP adresini ve altı haneli kodunu bir kez gir.",14,MUTED,false));
        gap(panel,15);
        ip=input("TV IP (ör. 192.168.1.102)",prefs.getString("tv_ip",""));
        panel.addView(ip,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));
        gap(panel,9);
        pin=input("TV'nin 6 haneli kodu",prefs.getString("tv_pin",""));
        pin.setInputType(InputType.TYPE_CLASS_NUMBER);
        panel.addView(pin,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));
        gap(panel,14);
        active=new CheckBox(this);
        active.setText("Oyun sırasında otomatik Türkçe çeviri");
        active.setTextColor(WHITE);
        active.setChecked(prefs.getBoolean("enabled",false));
        panel.addView(active);
        gap(panel,7);
        Button save=button("Kaydet ve çeviriyi etkinleştir");
        panel.addView(save,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));
        save.setOnClickListener(v->save(true));
        gap(panel,10);
        Button test=button("TV'ye test altyazısı gönder");
        panel.addView(test,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));
        test.setOnClickListener(v -> {
            if(!save(false)) return;
            state.setText("TV'ye bağlanılıyor…");
            String host=ip.getText().toString().trim(), code=pin.getText().toString().trim();
            worker.execute(() -> {
                String response=GlossaTvClient.request(host,code,
                    "GlossaWhaleOS 3.1 • Yazı rengi ve konumu testi.",
                    getSharedPreferences(PREF,MODE_PRIVATE));
                runOnUiThread(() -> {if(!isFinishing()&&!isDestroyed()) state.setText(response.equals("OK")?
                    "✓ TV altyazı alıcısı çalışıyor":response);});
            });
        });
        gap(panel,13);
        state=txt(prefs.getString("last_state","Çeviri kapalı."),14,WHITE,false);
        panel.addView(state);
        root.addView(panel);
        gap(root,17);

        LinearLayout style=new LinearLayout(this);
        style.setOrientation(LinearLayout.VERTICAL);
        style.setBackground(background(PANEL,18));
        pad(style,18,19);
        style.addView(txt("TV ALTYAZI GÖRÜNÜMÜ",14,CYAN,true));
        gap(style,10);
        style.addView(txt("Ayarlar sonraki altyazı geldiğinde TV'ye uygulanır. "
                +"Yatay ve dikey konum yüzdeleri ekranın kenarından hesaplanır.",
            13,MUTED,false));
        gap(style,10);

        sizeLabel=txt("",15,WHITE,true);
        style.addView(sizeLabel);
        sizeBar=slider(34,prefs.getInt("style_size",26)-18);
        style.addView(sizeBar);
        xLabel=txt("",15,WHITE,true);
        style.addView(xLabel);
        xBar=slider(100,prefs.getInt("style_x",50));
        style.addView(xBar);
        yLabel=txt("",15,WHITE,true);
        style.addView(yLabel);
        yBar=slider(100,prefs.getInt("style_y",87));
        style.addView(yBar);
        gap(style,10);
        style.addView(txt("Yazı rengi",14,WHITE,true));
        colorBox=choice(COLOR_NAMES,optionIndex(COLORS,prefs.getInt("style_color",0xFFFFFFFF)));
        style.addView(colorBox);
        gap(style,8);
        style.addView(txt("Yazı arka planı",14,WHITE,true));
        backgroundBox=choice(BG_NAMES,
            optionIndex(BACKGROUNDS,prefs.getInt("style_background",0xE5081020)));
        style.addView(backgroundBox);
        gap(style,12);
        style.addView(txt("Canlı yazı örneği",13,MUTED,false));
        gap(style,8);
        preview=txt("Buradan çıkmamız gerekiyor!",24,WHITE,true);
        preview.setGravity(android.view.Gravity.CENTER);
        pad(preview,16,12);
        style.addView(preview,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        SeekBar.OnSeekBarChangeListener move=new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean user) {updatePreview();}
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        };
        sizeBar.setOnSeekBarChangeListener(move);
        xBar.setOnSeekBarChangeListener(move);
        yBar.setOnSeekBarChangeListener(move);
        AdapterView.OnItemSelectedListener choose=new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> a,View v,int position,long id) {updatePreview();}
            @Override public void onNothingSelected(AdapterView<?> a) {}
        };
        colorBox.setOnItemSelectedListener(choose);
        backgroundBox.setOnItemSelectedListener(choose);
        updatePreview();
        root.addView(style);
        gap(root,17);

        LinearLayout notes=new LinearLayout(this);
        notes.setOrientation(LinearLayout.VERTICAL);
        notes.setBackground(background(PANEL,16));
        pad(notes,18,18);
        notes.addView(txt("NASIL ÇALIŞIR?",14,CYAN,true));
        gap(notes,10);
        notes.addView(txt("1. Kaydet ve çeviriyi etkinleştir.\n"+
            "2. Chiaki ana ekranında PS4'ü aç.\n"+
            "3. Oyun görüntüsü gelince altyazı otomatik algılanır.\n"+
            "4. İngilizce metin telefonda çevrilir ve TV'ye gönderilir.\n\n"+
            "İlk kullanımda İngilizce–Türkçe çeviri modeli internetten indirilir. "+
            "Bağlantı ve çeviri süreleri gerçek cihazda ölçülür; sıfır gecikme vaat edilmez.",
            14,MUTED,false));
        root.addView(notes);
        gap(root,25);
        Button back=button("PS4 ekranına dön");
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));
        back.setOnClickListener(v->finish());
        setContentView(scroller);
    }

    private boolean save(boolean enable) {
        String host=ip.getText().toString().trim();
        String code=pin.getText().toString().trim();
        if(!GlossaTvClient.validIp(host)||!code.matches("[0-9]{6}")) {
            state.setText("TV IP'sini ve 6 haneli kodu kontrol et.");
            return false;
        }
        getSharedPreferences(PREF,MODE_PRIVATE).edit()
            .putString("tv_ip",host)
            .putString("tv_pin",code)
            .putInt("style_size",18+sizeBar.getProgress())
            .putInt("style_x",xBar.getProgress())
            .putInt("style_y",yBar.getProgress())
            .putInt("style_color",COLORS[colorBox.getSelectedItemPosition()])
            .putInt("style_background",BACKGROUNDS[backgroundBox.getSelectedItemPosition()])
            .putBoolean("enabled",enable && active.isChecked()
                || !enable && getSharedPreferences(PREF,MODE_PRIVATE).getBoolean("enabled",false))
            .apply();
        if(enable)
            state.setText(active.isChecked()?"✓ Hazır. PS4 görüntüsünü açınca canlı çeviri başlar.":"Çeviri kapatıldı.");
        return true;
    }

    @Override protected void onDestroy() {worker.shutdownNow();super.onDestroy();}
}
