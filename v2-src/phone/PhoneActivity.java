package com.darkscorpion.glossasub.phone;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.Executors;

public final class PhoneActivity extends Activity {
    private static final int CAPTURE_REQUEST=4102;
    private SharedPreferences prefs;
    private EditText tvIp;
    private EditText pairPin;
    private TextView status;
    private TextView live;
    private boolean registered=false;

    private final BroadcastReceiver events = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            String value=intent.getStringExtra("status");
            if (value!=null) { status.setText(value); prefs.edit().putString("last_status",value).apply(); }
            String line=intent.getStringExtra("subtitle");
            if (line!=null && !line.isEmpty()) live.setText(line);
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(GlossaUI.BG);
        getWindow().setNavigationBarColor(GlossaUI.BG);
        prefs=getSharedPreferences("glossa_v2_phone", MODE_PRIVATE);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(GlossaUI.BG);
        LinearLayout page=GlossaUI.column(this);
        GlossaUI.pad(page,this,22,34,22,44);
        scroll.addView(page,new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView overline=GlossaUI.text(this,"DARK SCORPION  /  GLOSSA LAB",12,GlossaUI.TEAL,true);
        GlossaUI.add(page,overline);
        GlossaUI.gap(page,this,11);
        TextView title=GlossaUI.text(this,"GlossaWhaleOS",32,GlossaUI.WHITE,true);
        GlossaUI.add(page,title);
        GlossaUI.add(page,GlossaUI.text(this,"Türkçe oyun deneyimi, yeni nesil.",16,GlossaUI.MUTED,false));
        GlossaUI.gap(page,this,23);

        LinearLayout hero=GlossaUI.column(this);
        hero.setBackground(GlossaUI.gradient(this,0xFF234F68,0xFF272D70,25));
        GlossaUI.pad(hero,this,23,23,23,24);
        GlossaUI.add(hero,GlossaUI.text(this,"●  TELEFON MERKEZİ    V2.0",13,GlossaUI.TEAL,true));
        GlossaUI.gap(hero,this,17);
        GlossaUI.add(hero,GlossaUI.text(this,"Altyazıyı yakala.\nAnında Türkçeleştir.",25,GlossaUI.WHITE,true));
        GlossaUI.gap(hero,this,13);
        status=GlossaUI.text(this,prefs.getString("last_status","Hazır • Henüz ekran yakalama başlatılmadı"),14,GlossaUI.WHITE,false);
        GlossaUI.add(hero,status);
        page.addView(hero,GlossaUI.spaced(this,19));

        LinearLayout pairing=GlossaUI.card(this,"01  ·  Televizyon bağlantısı",
            "TV'deki IP adresini ve altı haneli eşleştirme kodunu gir.");
        tvIp=GlossaUI.field(this,"TV IP adresi (ör. 192.168.1.40)",prefs.getString("tv_ip",""),false);
        pairing.addView(tvIp,GlossaUI.spaced(this,10));
        pairPin=GlossaUI.field(this,"6 haneli TV kodu",prefs.getString("pin",""),false);
        pairing.addView(pairPin,GlossaUI.spaced(this,12));
        TextView save=GlossaUI.button(this,"Bağlantıyı kaydet",false);
        save.setOnClickListener(v -> {
            String ip=tvIp.getText().toString().trim();
            String pin=pairPin.getText().toString().trim();
            if (!RelayClient.validLocalIp(ip) || !pin.matches("[0-9]{6}")) {
                Toast.makeText(this,"Geçerli yerel ağ IP'si ve 6 haneli kod gir.",Toast.LENGTH_LONG).show();
                return;
            }
            prefs.edit().putString("tv_ip",ip).putString("pin",pin).apply();
            status.setText("TV eşleştirme bilgisi kaydedildi");
        });
        GlossaUI.add(pairing,save);
        page.addView(pairing,GlossaUI.spaced(this,16));

        LinearLayout action=GlossaUI.card(this,"02  ·  Canlı oyun çevirisi",
            "Telefon ekranındaki İngilizce altyazıyı okur; ilk kullanımda İngilizce–Türkçe dil modeli indirilir. PS Remote Play ekranında da yakalama izni verilmesi gerekir.");
        TextView start=GlossaUI.button(this,"▶   Ekran OCR ve çeviriyi başlat",true);
        start.setOnClickListener(v -> beginCapture());
        action.addView(start,GlossaUI.spaced(this,11));
        TextView remote=GlossaUI.button(this,"PS Remote Play uygulamasını aç",false);
        remote.setOnClickListener(v -> openRemotePlay());
        action.addView(remote,GlossaUI.spaced(this,11));
        TextView stop=GlossaUI.button(this,"■   Çeviriyi durdur",false);
        stop.setOnClickListener(v -> {
            Intent intent=new Intent(this,OcrCaptureService.class);
            intent.setAction(OcrCaptureService.STOP);
            startService(intent);
            status.setText("Çeviri durduruldu");
        });
        GlossaUI.add(action,stop);
        page.addView(action,GlossaUI.spaced(this,16));

        LinearLayout preview=GlossaUI.card(this,"03  ·  Canlı altyazı",
            "Çevrilen son cümle burada görüntülenir.");
        live=GlossaUI.text(this,"Henüz altyazı algılanmadı.",20,GlossaUI.TEAL,true);
        GlossaUI.add(preview,live);
        GlossaUI.gap(preview,this,18);
        TextView test=GlossaUI.button(this,"TV'ye test altyazısı gönder",false);
        test.setOnClickListener(v -> testSubtitle());
        GlossaUI.add(preview,test);
        page.addView(preview,GlossaUI.spaced(this,16));

        LinearLayout note=GlossaUI.card(this,"Nasıl çalışır?",
            "PS4 → PS Remote Play → telefonun ekran yakalama izni → OCR → cihazda Türkçe çeviri → yerel Wi-Fi → TV. Bu sürüm, PS Remote Play uygulamasının yerine geçmez. Korunan video ekranları Android tarafından yakalanamayabilir.");
        GlossaUI.add(note,GlossaUI.text(this,
            "Gizlilik: Görüntü kareleri OCR için telefonda işlenir; çevrilen altyazı yerel ağdaki eşleştirilmiş TV'ye gönderilir.",
            13,GlossaUI.MUTED,false));
        GlossaUI.add(page,note);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!registered) {
            registerReceiver(events,new IntentFilter(OcrCaptureService.STATUS));
            registered=true;
        }
    }
    @Override protected void onPause() {
        if (registered) { unregisterReceiver(events); registered=false; }
        super.onPause();
    }
    private void beginCapture() {
        String ip=tvIp.getText().toString().trim();
        String pin=pairPin.getText().toString().trim();
        if(!RelayClient.validLocalIp(ip) || !pin.matches("[0-9]{6}")){
            Toast.makeText(this,"Önce TV IP ve kodunu kaydet.",Toast.LENGTH_LONG).show();
            return;
        }
        prefs.edit().putString("tv_ip",ip).putString("pin",pin).apply();
        MediaProjectionManager manager=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if(manager==null) { status.setText("Bu cihaz ekran yakalamayı desteklemiyor."); return; }
        status.setText("Android ekran yakalama izni bekleniyor…");
        startActivityForResult(manager.createScreenCaptureIntent(),CAPTURE_REQUEST);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==CAPTURE_REQUEST){
            if(resultCode!=RESULT_OK || data==null) { status.setText("Ekran yakalama izni verilmedi."); return; }
            Intent intent=new Intent(this,OcrCaptureService.class);
            intent.setAction(OcrCaptureService.START);
            intent.putExtra("resultCode",resultCode);
            intent.putExtra("resultData",data);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(intent); else startService(intent);
            status.setText("Görüntü işleme başlatılıyor… PS Remote Play'i açabilirsin.");
        }
    }
    private void testSubtitle() {
        String ip=tvIp.getText().toString().trim();
        String pin=pairPin.getText().toString().trim();
        if(!RelayClient.validLocalIp(ip) || !pin.matches("[0-9]{6}")) { status.setText("Önce TV IP ve kodunu gir."); return; }
        status.setText("TV bağlantısı kontrol ediliyor…");
        Executors.newSingleThreadExecutor().execute(() -> {
            String result=RelayClient.send(ip,pin,"GlossaWhaleOS 2.0 hazır! Türkçe altyazı bağlantısı kuruldu.");
            runOnUiThread(() -> status.setText(result));
        });
    }
    private void openRemotePlay() {
        try {
            Intent launch=getPackageManager().getLaunchIntentForPackage("com.playstation.remoteplay");
            if(launch!=null) { startActivity(launch); return; }
            Intent web=new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.playstation.remoteplay"));
            startActivity(web);
            status.setText("PS Remote Play kurulum sayfası açıldı.");
        } catch(Exception error) {
            status.setText("PS Remote Play uygulamasını elle aç.");
        }
    }
}
