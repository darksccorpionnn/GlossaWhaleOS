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
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PhoneActivity extends Activity {
    private static final int CAPTURE_REQUEST=4102;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean scanning=new AtomicBoolean(false);
    private SharedPreferences prefs;
    private EditText tvIp;
    private EditText pairPin;
    private TextView status;
    private TextView tvStatus;
    private TextView ps4Status;
    private TextView ps4Diagnostics;
    private EditText manualPs4Ip;
    private TextView live;
    private boolean registered=false;

    private final BroadcastReceiver events=new BroadcastReceiver() {
        @Override public void onReceive(Context ctx,Intent intent) {
            String value=intent.getStringExtra("status");
            if(value!=null) {
                status.setText(value);
                prefs.edit().putString("last_status",value).apply();
            }
            String line=intent.getStringExtra("subtitle");
            if(line!=null && !line.isEmpty()) live.setText(line);
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(GlossaUI.BG);
        getWindow().setNavigationBarColor(GlossaUI.BG);
        prefs=getSharedPreferences("glossa_v2_phone",MODE_PRIVATE);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(GlossaUI.BG);
        LinearLayout page=GlossaUI.column(this);
        GlossaUI.pad(page,this,22,34,22,44);
        scroll.addView(page,new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        GlossaUI.add(page,GlossaUI.text(this,"DARK SCORPION  /  GLOSSA LAB",12,GlossaUI.TEAL,true));
        GlossaUI.gap(page,this,11);
        GlossaUI.add(page,GlossaUI.text(this,"GlossaWhaleOS",32,GlossaUI.WHITE,true));
        GlossaUI.add(page,GlossaUI.text(this,"PS4 ve TV'yi Wi-Fi'de otomatik bul.",16,GlossaUI.MUTED,false));
        GlossaUI.gap(page,this,23);

        LinearLayout hero=GlossaUI.column(this);
        hero.setBackground(GlossaUI.gradient(this,0xFF234F68,0xFF272D70,25));
        GlossaUI.pad(hero,this,23,23,23,24);
        GlossaUI.add(hero,GlossaUI.text(this,"●  TELEFON MERKEZİ    V2.1",13,GlossaUI.TEAL,true));
        GlossaUI.gap(hero,this,17);
        GlossaUI.add(hero,GlossaUI.text(this,"Cihazları keşfet.\nÇeviriyi hazırla.",25,GlossaUI.WHITE,true));
        GlossaUI.gap(hero,this,13);
        status=GlossaUI.text(this,prefs.getString("last_status",
            "Yerel ağ bağlantısı taranmaya hazır"),14,GlossaUI.WHITE,false);
        GlossaUI.add(hero,status);
        page.addView(hero,GlossaUI.spaced(this,19));

        LinearLayout ps4=GlossaUI.card(this,"01  ·  PS4 otomatik bulma",
            "Telefon ve PS4 aynı ağdaysa cihazın adı ve IP adresi otomatik algılanır.");
        ps4Status=GlossaUI.text(this,"PS4 taraması bekleniyor…",16,GlossaUI.TEAL,true);
        GlossaUI.add(ps4,ps4Status);
        GlossaUI.gap(ps4,this,9);
        ps4Diagnostics=GlossaUI.text(this,
            "Ağ tanılama bilgileri taramadan sonra burada görünecek.",
            12,GlossaUI.MUTED,false);
        GlossaUI.add(ps4,ps4Diagnostics);
        GlossaUI.gap(ps4,this,14);
        TextView scan=GlossaUI.button(this,"↻  PS4 ve TV'yi yeniden tara",true);
        scan.setOnClickListener(v -> scanNetwork());
        GlossaUI.add(ps4,scan);
        GlossaUI.gap(ps4,this,15);
        manualPs4Ip=GlossaUI.field(this,
            "İsteğe bağlı: PS4 IP adresi (otomatik bulunamazsa)",
            prefs.getString("ps4_manual_ip",""),false);
        ps4.addView(manualPs4Ip,GlossaUI.spaced(this,10));
        TextView tryManual=GlossaUI.button(this,"Bu IP'deki PS4'ü kontrol et",false);
        tryManual.setOnClickListener(v -> {
            String ip=manualPs4Ip.getText().toString().trim();
            if(!RelayClient.validLocalIp(ip)) {
                Toast.makeText(this,"Geçerli bir yerel PS4 IP'si gir.",
                    Toast.LENGTH_LONG).show();
                return;
            }
            prefs.edit().putString("ps4_manual_ip",ip).putString("ps4_ip",ip).apply();
            scanNetwork();
        });
        GlossaUI.add(ps4,tryManual);
        GlossaUI.gap(ps4,this,13);
        GlossaUI.add(ps4,GlossaUI.text(this,
            "PS4 eşleştirme kodu: PS4 → Ayarlar → Uzaktan Oynatma Bağlantı Ayarları → Cihaz Ekle.\n" +
            "Not: Bu sürüm PS4'ü keşfeder. PSN hesabıyla eşleştirme ve görüntü akışı henüz etkin değil.",
            13,GlossaUI.MUTED,false));
        page.addView(ps4,GlossaUI.spaced(this,16));

        LinearLayout pairing=GlossaUI.card(this,"02  ·  TV otomatik bağlantısı",
            "TV alıcısı açıksa IP adresi otomatik doldurulur. Güvenlik için TV'deki 6 haneli kod ilk kez girilir; ardından cihazda saklanır.");
        tvStatus=GlossaUI.text(this,"TV henüz aranmadı.",14,GlossaUI.TEAL,false);
        GlossaUI.add(pairing,tvStatus);
        GlossaUI.gap(pairing,this,11);
        tvIp=GlossaUI.field(this,"TV IP (otomatik bulunur; elle de girilebilir)",
            prefs.getString("tv_ip",""),false);
        pairing.addView(tvIp,GlossaUI.spaced(this,10));
        pairPin=GlossaUI.field(this,"TV ekranındaki 6 haneli kod",
            prefs.getString("pin",""),false);
        pairing.addView(pairPin,GlossaUI.spaced(this,12));
        TextView save=GlossaUI.button(this,"TV eşleştirmesini kaydet ve dene",false);
        save.setOnClickListener(v -> {
            if(!saveTvCredentials()) return;
            testSubtitle();
        });
        GlossaUI.add(pairing,save);
        page.addView(pairing,GlossaUI.spaced(this,16));

        LinearLayout action=GlossaUI.card(this,"03  ·  Canlı altyazı çevirisi",
            "Telefon ekranındaki İngilizce altyazıyı OCR ile okuyup çevirir. Bu, PS4 görüntüsü telefona ulaştığında kullanılabilir.");
        TextView start=GlossaUI.button(this,"▶  Ekran OCR ve çeviriyi başlat",true);
        start.setOnClickListener(v -> beginCapture());
        action.addView(start,GlossaUI.spaced(this,11));
        TextView stop=GlossaUI.button(this,"■  Çeviriyi durdur",false);
        stop.setOnClickListener(v -> {
            Intent intent=new Intent(this,OcrCaptureService.class);
            intent.setAction(OcrCaptureService.STOP);
            startService(intent);
            status.setText("Çeviri durduruldu");
        });
        GlossaUI.add(action,stop);
        page.addView(action,GlossaUI.spaced(this,16));

        LinearLayout preview=GlossaUI.card(this,"04  ·  Canlı altyazı",
            "Çevrilen son cümle burada görüntülenir.");
        live=GlossaUI.text(this,"Henüz altyazı algılanmadı.",20,GlossaUI.TEAL,true);
        GlossaUI.add(preview,live);
        GlossaUI.gap(preview,this,18);
        TextView test=GlossaUI.button(this,"TV'ye test altyazısı gönder",false);
        test.setOnClickListener(v -> testSubtitle());
        GlossaUI.add(preview,test);
        page.addView(preview,GlossaUI.spaced(this,16));

        LinearLayout note=GlossaUI.card(this,"Bağlantı durumu",
            "PS4 ve TV keşfi yerel Wi-Fi üzerinden çalışır. PSN şifresi istenmez. " +
            "PS4'e gerçek uzaktan oynatma bağlantısı ve video alma işlevi bu sürümde bulunmaz.");
        GlossaUI.add(note,GlossaUI.text(this,
            "Gizlilik: OCR ve çeviri telefonda çalışır. TV'ye yalnızca altyazı metni gönderilir. " +
            "Bazı TV'ler HDMI üstünde üçüncü taraf altyazı katmanını göstermeyebilir.",
            13,GlossaUI.MUTED,false));
        GlossaUI.add(page,note);
        setContentView(scroll);
        scanNetwork();
    }

    private void scanNetwork() {
        if(!scanning.compareAndSet(false,true)) return;
        ps4Status.setText("PS4 aranıyor • UDP 987…");
        tvStatus.setText("TV aranıyor • Wi-Fi…");
        worker.execute(() -> {
            DiscoveryClient.Result result=DiscoveryClient.scan(
                getApplicationContext(),prefs.getString("ps4_ip",""));
            runOnUiThread(() -> {
                scanning.set(false);
                if(isFinishing() || isDestroyed()) return;
                ps4Diagnostics.setText(result.diagnostics());
                if(result.consoles.isEmpty()) {
                    ps4Status.setText("PS4 bulunamadı. Tarama iki yöntemle denendi. " +
                        "Ağ bilgilerini kontrol et; PS4 IP'sini biliyorsan alttaki yedek alanı kullan.");
                } else {
                    DiscoveryClient.Ps4 preferred=result.consoles.get(0);
                    String oldId=prefs.getString("ps4_id","");
                    for(DiscoveryClient.Ps4 p:result.consoles)
                        if(p.id.equals(oldId)) {preferred=p;break;}
                    prefs.edit().putString("ps4_id",preferred.id)
                        .putString("ps4_ip",preferred.ip).apply();
                    ps4Status.setText("✓  "+preferred.name+" • "+preferred.state+"\n"+
                        "IP otomatik: "+preferred.ip+" • Sistem: "+preferred.systemVersion+
                        (result.consoles.size()>1?"\nAğdaki PS4 sayısı: "+result.consoles.size():""));
                }
                if(result.tvAddresses.isEmpty()) {
                    tvStatus.setText("TV alıcısı bulunamadı. TV'de GlossaWhaleOS'u açıp alıcı iznini ver.");
                } else {
                    String oldIp=prefs.getString("tv_ip","");
                    String picked=result.tvAddresses.contains(oldIp)?oldIp:result.tvAddresses.get(0);
                    tvIp.setText(picked);
                    prefs.edit().putString("tv_ip",picked).apply();
                    final String pin=pairPin.getText().toString().trim();
                    if(pin.matches("[0-9]{6}")) {
                        tvStatus.setText("TV bulundu: "+picked+" • Otomatik bağlantı doğrulanıyor…");
                        worker.execute(() -> {
                            String state=RelayClient.ping(picked,pin);
                            runOnUiThread(() -> {
                                if(!isFinishing() && !isDestroyed() &&
                                        picked.equals(tvIp.getText().toString().trim()))
                                    tvStatus.setText(state+"\nIP: "+picked);
                            });
                        });
                    } else {
                        tvStatus.setText("✓  TV bulundu: "+picked+
                            " • Bağlanmak için TV'deki kodu bir kez gir.");
                    }
                }
                if(!result.error.isEmpty()) {
                    status.setText(result.error);
                    ps4Diagnostics.setText(result.error+"\n"+result.diagnostics());
                }
            });
        });
    }

    private boolean saveTvCredentials() {
        String ip=tvIp.getText().toString().trim();
        String pin=pairPin.getText().toString().trim();
        if(!RelayClient.validLocalIp(ip) || !pin.matches("[0-9]{6}")) {
            Toast.makeText(this,"TV bulunmalı ve 6 haneli TV kodu girilmeli.",
                Toast.LENGTH_LONG).show();
            return false;
        }
        prefs.edit().putString("tv_ip",ip).putString("pin",pin).apply();
        return true;
    }

    @Override protected void onResume() {
        super.onResume();
        if(!registered) {
            registerReceiver(events,new IntentFilter(OcrCaptureService.STATUS));
            registered=true;
        }
    }
    @Override protected void onPause() {
        if(registered) {unregisterReceiver(events);registered=false;}
        super.onPause();
    }
    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void beginCapture() {
        if(!saveTvCredentials()) return;
        MediaProjectionManager manager=(MediaProjectionManager)
            getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if(manager==null) {
            status.setText("Bu cihaz ekran yakalamayı desteklemiyor.");
            return;
        }
        status.setText("Android ekran yakalama izni bekleniyor…");
        startActivityForResult(manager.createScreenCaptureIntent(),CAPTURE_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==CAPTURE_REQUEST) {
            if(resultCode!=RESULT_OK || data==null) {
                status.setText("Ekran yakalama izni verilmedi.");return;
            }
            Intent intent=new Intent(this,OcrCaptureService.class);
            intent.setAction(OcrCaptureService.START);
            intent.putExtra("resultCode",resultCode);
            intent.putExtra("resultData",data);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(intent);
            else startService(intent);
            status.setText("OCR başlatılıyor… PS4 video akışı henüz entegre değil.");
        }
    }

    private void testSubtitle() {
        if(!saveTvCredentials()) return;
        final String ip=tvIp.getText().toString().trim();
        final String pin=pairPin.getText().toString().trim();
        tvStatus.setText("TV bağlantısı kontrol ediliyor…");
        worker.execute(() -> {
            String response=RelayClient.send(ip,pin,
                "GlossaWhaleOS 2.1 hazır! Telefon ve TV bağlantısı kuruldu.");
            runOnUiThread(() -> {
                if(!isFinishing() && !isDestroyed()) {
                    tvStatus.setText(response);
                    status.setText(response);
                }
            });
        });
    }
}
