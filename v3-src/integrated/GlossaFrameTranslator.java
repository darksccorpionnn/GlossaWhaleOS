// SPDX-License-Identifier: AGPL-3.0-only
package com.metallic.chiaki.glossa;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.SurfaceView;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * In-process, permission-free PS4 subtitle capture: copies the Chiaki
 * MediaCodec SurfaceView (NOT the phone's screen) and translates cropped text.
 *
 * PixelCopy support must be tested on the user's real device. It may return a
 * source-no-data or protected surface error; no remote-play decoding is changed.
 */
public final class GlossaFrameTranslator {
    public interface Status {void update(String message);}
    private static final int W=640,H=360;
    private static final int INTERVAL_MS=290;
    private final Activity activity;
    private final SurfaceView video;
    private final Status status;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private TextRecognizer ocr;
    private Translator translator;
    private boolean started=false,closed=false,modelReady=false,captureBusy=false,translating=false;
    private long lastCaptureAt=0;
    private String lastSeen="",lastSent="",queued="",activeTranslation="";
    private int repeat=0,copyFailures=0,blankFrames=0;
    private final Map<String,Long> recentTexts=new LinkedHashMap<String,Long>(64,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Long> eldest) {
            return size()>100;
        }
    };
    private String lastStatus="";
    private String host="",pin="";
    private long latestSourceAt=0;
    private final Map<String,String> cached=new LinkedHashMap<String,String>(48,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,String> eldest) {
            return size()>64;
        }
    };

    public GlossaFrameTranslator(Activity activity, SurfaceView video, Status status) {
        this.activity=activity;
        this.video=video;
        this.status=status;
    }

    public void start() {
        if(closed||started) return;
        SharedPreferences p=activity.getSharedPreferences(GlossaSettingsActivity.PREF,Activity.MODE_PRIVATE);
        if(!p.getBoolean("enabled",false)) {
            report("Glossa kapalı • Ayarlardan etkinleştir");
            return;
        }
        host=p.getString("tv_ip","");
        pin=p.getString("tv_pin","");
        if(!GlossaTvClient.validIp(host)||!pin.matches("[0-9]{6}")) {
            report("TV IP/PIN ayarlarını kontrol et");
            return;
        }
        try {
            if(ocr==null) ocr=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            if(translator==null) {
                TranslatorOptions opt=new TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(TranslateLanguage.TURKISH).build();
                translator=Translation.getClient(opt);
            }
        } catch(Exception e) {
            report("OCR/Çeviri motoru açılamadı: "+e.getClass().getSimpleName());
            return;
        }
        started=true;
        if(!modelReady) {
            report("Türkçe dil modeli hazırlanıyor…");
            translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(v -> {
                    if(closed) return;
                    modelReady=true;
                    report("Glossa canlı OCR • PS4 görüntüsü taranıyor");
                }).addOnFailureListener(e -> {
                    modelReady=false;
                    report("Dil modeli indirilemedi • İnterneti kontrol et");
                });
        }
        main.post(scan);
    }

    public void stop() {
        started=false;
        captureBusy=false;
        main.removeCallbacks(scan);
    }

    public void close() {
        if(closed) return;
        stop();
        closed=true;
        network.shutdownNow();
        if(ocr!=null) {ocr.close();ocr=null;}
        if(translator!=null) {translator.close();translator=null;}
    }

    private void report(String message) {
        if(closed || message.equals(lastStatus)) return;
        lastStatus=message;
        activity.getSharedPreferences(GlossaSettingsActivity.PREF,Activity.MODE_PRIVATE)
            .edit().putString("last_state",message).apply();
        if(status!=null) status.update(message);
    }

    private final Runnable scan=new Runnable() {
        @Override public void run() {
            if(!started||closed) return;
            main.postDelayed(this,INTERVAL_MS);
            if(!modelReady||captureBusy||ocr==null) return;
            if(video.getWidth()<40||video.getHeight()<40||
               video.getHolder()==null||!video.getHolder().getSurface().isValid()) return;
            captureBusy=true;
            lastCaptureAt=SystemClock.elapsedRealtime();
            final Bitmap image;
            try {
                image=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
            } catch(Exception e) {
                captureBusy=false;
                report("Görüntü belleği ayrılamadı");
                return;
            }
            try {
                PixelCopy.request(video,image,result -> {
                    if(!started||closed) {
                        image.recycle();
                        captureBusy=false;
                        return;
                    }
                    if(result!=PixelCopy.SUCCESS) {
                        image.recycle();
                        captureBusy=false;
                        copyFailures++;
                        if(copyFailures==6)
                            report("Video yüzeyi kopyalanamıyor • PixelCopy hata "+result);
                        return;
                    }
                    copyFailures=0;
                    recognize(image,lastCaptureAt);
                },main);
            } catch(Exception e) {
                image.recycle();
                captureBusy=false;
                report("Video yüzeyi erişilemedi: "+e.getClass().getSimpleName());
            }
        }
    };

    private void recognize(Bitmap frame,long startedAt) {
        if(!started||ocr==null) {frame.recycle();captureBusy=false;return;}
        // Most PS4 subtitles sit in the lower central region. Use a small ROI to
        // reduce OCR latency and ignore HUD/menus near the edges.
        SharedPreferences p=activity.getSharedPreferences(GlossaSettingsActivity.PREF,Activity.MODE_PRIVATE);
        float l=p.getFloat("roi_l",.08f),t=p.getFloat("roi_t",.55f);
        float r=p.getFloat("roi_r",.92f),b=p.getFloat("roi_b",.96f);
        // Allow a margin for PixelCopy scaling rounding; protect Bitmap bounds.
        int left=Math.max(0,Math.min(W-2,(int)(W*l)));
        int top=Math.max(0,Math.min(H-2,(int)(H*t)));
        int right=Math.max(left+2,Math.min(W,(int)(W*r)));
        int bottom=Math.max(top+2,Math.min(H,(int)(H*b)));
        Bitmap region;
        try {
            region=Bitmap.createBitmap(frame,left,top,right-left,bottom-top);
        } catch(Exception e) {
            frame.recycle(); captureBusy=false; return;
        }
        frame.recycle();
        ocr.process(InputImage.fromBitmap(region,0))
            .addOnSuccessListener(result -> {
                region.recycle();
                captureBusy=false;
                if(!started||closed) return;
                String text=result.getText().replace('\n',' ')
                    .replaceAll("\\s+"," ").trim();
                onRecognized(text,startedAt);
            }).addOnFailureListener(e -> {
                region.recycle();
                captureBusy=false;
                report("OCR çalışmadı: "+e.getClass().getSimpleName());
            });
    }

    private static String key(String line) {
        return line.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");
    }

    private static boolean sameSentence(String a,String b) {
        if(a.equals(b))return true;
        if(a.length()<14||b.length()<14)return false;
        if(Math.abs(a.length()-b.length())>2)return false;
        // Common OCR mistakes change a single character; reject jitter, not real
        // new dialog lines. Conservative two-edit threshold for long lines.
        int[][] d=new int[2][b.length()+1];
        for(int j=0;j<=b.length();j++)d[0][j]=j;
        for(int i=1;i<=a.length();i++) {
            int row=i%2,prev=(i-1)%2;
            d[row][0]=i;
            for(int j=1;j<=b.length();j++)
                d[row][j]=Math.min(Math.min(d[prev][j]+1,
                    d[row][j-1]+1),
                    d[prev][j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
        }
        return d[a.length()%2][b.length()]<=
            (Math.min(a.length(),b.length())>=40?2:1);
    }

    private void onRecognized(String line,long capturedAt) {
        if(line.length()<6||line.length()>245||!line.matches(".*[A-Za-z].*")) {
            blankFrames++;
            if(blankFrames>=3) {
                lastSeen="";
                lastSent="";
                repeat=0;
            }
            return;
        }
        blankFrames=0;
        String clean=key(line),previous=key(lastSeen);
        if(sameSentence(clean,previous))repeat++;
        else {lastSeen=line;repeat=1;}
        // Repeated OCR noise has to be seen again; clear long lines can be
        // processed immediately to minimize delay.
        if(repeat<2 && line.length()<30)return;
        if(sameSentence(clean,key(lastSent)))return;
        long now=SystemClock.elapsedRealtime();
        for(Map.Entry<String,Long> it:recentTexts.entrySet())
            if(now-it.getValue()<10000 && sameSentence(clean,it.getKey()))return;
        latestSourceAt=capturedAt;
        String existing=cached.get(line);
        if(existing!=null) {
            send(line,existing,capturedAt,true);
            return;
        }
        if(translating) {queued=line;return;}
        translate(line,capturedAt);
    }

    private void translate(String source,long capturedAt) {
        if(!started||closed||translator==null) return;
        translating=true;
        activeTranslation=source;
        report("Altyazı algılandı • çevriliyor…");
        translator.translate(source)
            .addOnSuccessListener(text -> {
                translating=false;
                if(closed||!started) return;
                String turkish=text.trim();
                if(!turkish.isEmpty()) {
                    cached.put(source,turkish);
                    // Never send stale translation after a new subtitle
                    // replaced this one while translation was in flight.
                    if(source.equals(lastSeen)) send(source,turkish,capturedAt,false);
                }
                drainQueue();
            })
            .addOnFailureListener(e -> {
                translating=false;
                if(!closed) report("Çeviri başarısız: "+e.getClass().getSimpleName());
                drainQueue();
            });
    }

    private void drainQueue() {
        String next=queued;
        queued="";
        if(started&&!next.isEmpty()&&!next.equals(lastSent) &&
            next.equals(lastSeen)) translate(next,latestSourceAt);
    }

    private void send(String source,String text,long capturedAt,boolean cacheHit) {
        if(!started||closed||source.equals(lastSent)) return;
        lastSent=source;
        recentTexts.put(key(source),SystemClock.elapsedRealtime());
        long elapsed=SystemClock.elapsedRealtime()-capturedAt;
        report("✓ Çeviri "+elapsed+" ms"+(cacheHit?" • önbellek":"")+" • TV'ye gönderiliyor");
        String selectedHost=host,selectedPin=pin;
        network.execute(() -> {
            String answer=GlossaTvClient.request(selectedHost,selectedPin,text);
            main.post(() -> {
                if(closed||!started) return;
                if("OK".equals(answer))
                    report("✓ TV altyazısı gönderildi • "+(SystemClock.elapsedRealtime()-capturedAt)+" ms");
                else {
                    report(answer);
                    if(source.equals(lastSent)) lastSent="";
                    recentTexts.remove(key(source));
                }
            });
        });
    }
}
