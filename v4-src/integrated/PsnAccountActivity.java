package com.metallic.chiaki.glossa;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Convert a Sony Remote Play OAuth callback into a PSN Account ID.
 * PlayStation login takes place only at Sony HTTPS in the user's browser.
 * No PlayStation password, bearer token or OAuth code is persisted here.
 */
public final class PsnAccountActivity extends Activity {
    private static final String CLIENT_ID="ba495a24-818c-472b-b12d-ff231c1b5745";
    // Remote Play public OAuth client credentials, as documented by chiaki-ng.
    private static final String CLIENT_SECRET="mvaiZkRsAsI1IBkY";
    private static final String REDIRECT="https://remoteplay.dl.playstation.net/remoteplay/redirect";
    private static final String AUTH_BASE="https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/";
    private static final String LOGIN=AUTH_BASE+"authorize?"+
        "service_entity=urn%3Aservice-entity%3Apsn&"+
        "response_type=code&client_id="+CLIENT_ID+"&"+
        "redirect_uri=https%3A%2F%2Fremoteplay.dl.playstation.net%2Fremoteplay%2Fredirect&"+
        "scope=psn%3Aclientapp&request_locale=en_US&ui=pr&"+
        "service_logo=ps&layout_type=popup&smcid=remoteplay&prompt=always&"+
        "PlatformPrivacyWs1=minimal";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final String PREF="glossa_psn";
    private TextView feedback;
    private TextView current;
    private EditText redirectField;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        GlossaUI.init(this);
        getWindow().setStatusBarColor(GlossaUI.BG);
        getWindow().setNavigationBarColor(GlossaUI.BG);

        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(GlossaUI.BG);
        LinearLayout root=GlossaUI.column(this);
        GlossaUI.pad(root,this,22,31,22,42);
        scroll.addView(root,new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        GlossaUI.add(root,GlossaUI.text(this,"PS4  /  HESAP EŞLEŞTİRME",13,GlossaUI.TEAL,true));
        GlossaUI.gap(root,this,12);
        GlossaUI.add(root,GlossaUI.text(this,"İkinci PSN hesabın",28,GlossaUI.WHITE,true));
        GlossaUI.gap(root,this,13);
        GlossaUI.add(root,GlossaUI.text(this,
            "Ana hesabın PS4'te oyun oynamaya devam ederken, Remote Play için "+
            "ikinci PSN hesabını kullanmayı hedefliyoruz.",14,GlossaUI.MUTED,false));
        GlossaUI.gap(root,this,23);

        LinearLayout auth=GlossaUI.card(this,"01  ·  Sony üzerinden güvenli giriş",
            "Sony'nin resmi giriş sayfasını tarayıcıda aç. PSN şifreni "+
            "GlossaWhaleOS'a yazma.");
        TextView sony=GlossaUI.button(this,"Sony giriş sayfasını aç",true);
        sony.setOnClickListener(v -> {
            try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(LOGIN)));}
            catch(Exception e){feedback.setText("Sony giriş sayfasını açacak tarayıcı bulunamadı.");}
        });
        GlossaUI.add(auth,sony);
        GlossaUI.gap(auth,this,16);
        GlossaUI.add(auth,GlossaUI.text(this,
            "Giriş tamamlanınca tarayıcı, 'redirect' yazan bir sayfaya yönlendirebilir. "+
            "Adres çubuğundaki SON tam bağlantıyı kopyala. "+
            "Bu adres kısa ömürlü yetkilendirme kodu içerir; kimseyle paylaşma.",
            13,GlossaUI.MUTED,false));
        root.addView(auth,GlossaUI.spaced(this,16));

        LinearLayout account=GlossaUI.card(this,"02  ·  PSN Account ID al",
            "Kopyaladığın Sony yönlendirme adresini yalnızca bu alana yapıştır. "+
            "Kod cihazında işlenir, yetki belirteci kaydedilmez.");
        redirectField=GlossaUI.field(this,"https://remoteplay.dl.playstation.net/remoteplay/redirect?code=…","",false);
        account.addView(redirectField,GlossaUI.spaced(this,12));
        TextView resolve=GlossaUI.button(this,"PSN Account ID oluştur",true);
        resolve.setOnClickListener(v -> requestAccountId());
        GlossaUI.add(account,resolve);
        GlossaUI.gap(account,this,11);
        feedback=GlossaUI.text(this,"Henüz PSN hesabı doğrulanmadı.",14,GlossaUI.MUTED,false);
        GlossaUI.add(account,feedback);
        GlossaUI.gap(account,this,16);
        String saved=getSharedPreferences(PREF,MODE_PRIVATE).getString("account_id","");
        current=GlossaUI.text(this,
            saved.isEmpty()?"Account ID kayıtlı değil.":"✓  İkinci PSN hesabı için Account ID kayıtlı.",
            15,GlossaUI.TEAL,true);
        GlossaUI.add(account,current);
        GlossaUI.gap(account,this,12);
        TextView copy=GlossaUI.button(this,"Account ID'yi kopyala",false);
        copy.setOnClickListener(v -> copyId());
        GlossaUI.add(account,copy);
        GlossaUI.gap(account,this,9);
        TextView manual=GlossaUI.button(this,"Elimdeki Account ID'yi kullan",false);
        manual.setOnClickListener(v -> saveManualId());
        GlossaUI.add(account,manual);
        root.addView(account,GlossaUI.spaced(this,16));

        LinearLayout note=GlossaUI.card(this,"03  ·  PS4 kayıt kodu",
            "PS4 → Ayarlar → Uzaktan Oynatma Bağlantı Ayarları → Cihaz Ekle. "+
            "Buradaki 8 haneli kod, PSN Account ID'den farklıdır ve gerçek "+
            "Chiaki bağlantı motorunun kayıt ekranında kullanılacaktır.");
        GlossaUI.add(note,GlossaUI.text(this,
            "Bu sürüm PSN Account ID hazırlama ekranıdır; henüz PS4'e "+
            "bağlandığını veya PIN kaydı yaptığını iddia etmez. "+
            "PSN giriş sorunu olursa elle alınmış Account ID kullanılabilir.",
            13,GlossaUI.MUTED,false));
        GlossaUI.add(root,note);
        GlossaUI.gap(root,this,18);
        TextView next=GlossaUI.button(this,"Devam et → PS4'e eşleştir",true);
        next.setOnClickListener(v -> {
            String id=getSharedPreferences(PREF,MODE_PRIVATE).getString("account_id","");
            if(id.isEmpty()){
                feedback.setText("Önce ikinci PSN hesabının Account ID'sini al veya gir.");
                return;
            }
            android.content.Intent intent=new android.content.Intent(this,com.metallic.chiaki.regist.RegistActivity.class);
            intent.putExtra("glossa_psn_account_id",id);
            intent.putExtra("regist_host",getSharedPreferences("glossa_live_v3",MODE_PRIVATE)
                .getString("ps4_host",""));
            intent.putExtra("regist_broadcast",false);
            startActivity(intent);
        });
        GlossaUI.add(root,next);
        setContentView(scroll);
    }

    private void requestAccountId() {
        String url=redirectField.getText().toString().trim();
        String code;
        try {
            Uri uri=Uri.parse(url);
            if(!"https".equalsIgnoreCase(uri.getScheme()) ||
                    !"remoteplay.dl.playstation.net".equalsIgnoreCase(uri.getHost()) ||
                    !"/remoteplay/redirect".equals(uri.getPath()))
                throw new IllegalArgumentException("Yalnızca Sony'nin resmi yönlendirme adresi kabul edilir.");
            code=uri.getQueryParameter("code");
            if(code==null || code.length()<5 || code.length()>2048)
                throw new IllegalArgumentException("Bağlantıda geçerli giriş kodu bulunamadı.");
        } catch(Exception e) {
            feedback.setText("Sony'den alınan tam yönlendirme bağlantısını yapıştır.");
            return;
        }
        redirectField.setText("");
        feedback.setText("Sony hesabı doğrulanıyor…");
        worker.execute(() -> {
            String result;
            try {
                result=fetchAccountId(code);
                getSharedPreferences(PREF,MODE_PRIVATE).edit()
                    .putString("account_id",result).apply();
                runOnUiThread(() -> {
                    if(isFinishing() || isDestroyed()) return;
                    current.setText("✓  PSN Account ID başarıyla kaydedildi.");
                    feedback.setText("PSN Account ID hazır. Kopyala düğmesiyle kullanabilirsin.");
                });
            } catch(Exception e) {
                runOnUiThread(() -> {
                    if(isFinishing() || isDestroyed()) return;
                    feedback.setText("Sony hesabı doğrulanamadı. "+
                        "Bağlantı süresi dolmuş veya PSN erişilemiyor olabilir. "+
                        "Yeni giriş bağlantısıyla dene.");
                });
            }
        });
    }

    private static String fetchAccountId(String code) throws Exception {
        String auth=Base64.encodeToString((CLIENT_ID+":"+CLIENT_SECRET)
            .getBytes(StandardCharsets.US_ASCII),Base64.NO_WRAP);
        String body="grant_type=authorization_code&code="+
            URLEncoder.encode(code,"UTF-8")+
            "&redirect_uri="+URLEncoder.encode(REDIRECT,"UTF-8");
        HttpURLConnection conn=(HttpURLConnection)new URL(AUTH_BASE+"token").openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(12000);
        conn.setRequestProperty("Authorization","Basic "+auth);
        conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
        conn.setDoOutput(true);
        try {
            byte[] bytes=body.getBytes(StandardCharsets.US_ASCII);
            conn.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
            if(conn.getResponseCode()!=200) throw new IllegalStateException("Sony token response");
            JSONObject tokens=new JSONObject(readLimited(conn.getInputStream()));
            String token=tokens.optString("access_token","");
            if(token.isEmpty()) throw new IllegalStateException("Token missing");
            HttpURLConnection info=(HttpURLConnection)new URL(AUTH_BASE+"token/"+
                URLEncoder.encode(token,"UTF-8")).openConnection();
            info.setRequestMethod("GET");
            info.setConnectTimeout(12000);
            info.setReadTimeout(12000);
            info.setRequestProperty("Authorization","Basic "+auth);
            try {
                if(info.getResponseCode()!=200) throw new IllegalStateException("Sony user info");
                String numeric=new JSONObject(readLimited(info.getInputStream()))
                    .optString("user_id","");
                BigInteger userId=new BigInteger(numeric);
                if(userId.signum()<0 || userId.bitLength()>64)
                    throw new IllegalStateException("Invalid ID");
                byte[] littleEndian=new byte[8];
                BigInteger current=userId;
                for(int i=0;i<8;i++) {
                    littleEndian[i]=current.byteValue();
                    current=current.shiftRight(8);
                }
                return Base64.encodeToString(littleEndian,Base64.NO_WRAP);
            } finally {info.disconnect();}
        } finally {conn.disconnect();}
    }

    private static String readLimited(InputStream in) throws Exception {
        try(InputStream input=in; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buf=new byte[1024];int n;
            while((n=input.read(buf))!=-1) {
                if(out.size()+n>16384) throw new IllegalStateException("Response too long");
                out.write(buf,0,n);
            }
            return out.toString("UTF-8");
        }
    }

    private void copyId() {
        String id=getSharedPreferences(PREF,MODE_PRIVATE).getString("account_id","");
        if(id.isEmpty()) { feedback.setText("Henüz kayıtlı PSN Account ID yok."); return; }
        ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard!=null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("PSN Account ID",id));
            feedback.setText("PSN Account ID kopyalandı. Panodaki bilgiyi yabancılarla paylaşma.");
        }
    }

    private void saveManualId() {
        String candidate=redirectField.getText().toString().trim();
        try {
            byte[] decoded=Base64.decode(candidate,Base64.DEFAULT);
            if(decoded.length!=8) throw new IllegalArgumentException("length");
            String id=Base64.encodeToString(decoded,Base64.NO_WRAP);
            getSharedPreferences(PREF,MODE_PRIVATE).edit()
                .putString("account_id",id).apply();
            current.setText("✓  PSN Account ID elle kaydedildi.");
            feedback.setText("8 baytlık Account ID doğrulandı; PSN hesabı sunucudan doğrulanmadı.");
            redirectField.setText("");
        } catch(Exception e) {
            feedback.setText("Geçerli Base64 PSN Account ID gir (8 bayta dönüşmeli).");
        }
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
