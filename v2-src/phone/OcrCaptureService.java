package com.darkscorpion.glossasub.phone;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OcrCaptureService extends Service {
    public static final String START="com.darkscorpion.glossawhale.v2.START_CAPTURE";
    public static final String STOP="com.darkscorpion.glossawhale.v2.STOP_CAPTURE";
    public static final String STATUS="com.darkscorpion.glossawhale.v2.STATUS";
    private static final String CHANNEL="glossa_v2_capture";
    private final AtomicBoolean inFlight=new AtomicBoolean(false);
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private HandlerThread imageThread;
    private Handler imageHandler;
    private MediaProjection projection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private TextRecognizer recognizer;
    private Translator translator;
    private boolean modelReady=false;
    private long lastScan=0;
    private String candidate="";
    private int candidateCount=0;
    private String lastTranslated="";
    private String tvIp="";
    private String pin="";
    private boolean stopping=false;

    @Override public void onCreate() {
        super.onCreate();
        imageThread=new HandlerThread("Glossa-OCR-Capture");
        imageThread.start();
        imageHandler=new Handler(imageThread.getLooper());
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null) return START_NOT_STICKY;
        if(STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if(!START.equals(intent.getAction())) return START_NOT_STICKY;

        SharedPreferences p=getSharedPreferences("glossa_v2_phone",MODE_PRIVATE);
        tvIp=p.getString("tv_ip","");
        pin=p.getString("pin","");
        ensureForeground();
        emit("Telefon OCR hazırlanıyor…",null);
        try {
            MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            int code=intent.getIntExtra("resultCode",Activity.RESULT_CANCELED);
            Intent data=intent.getParcelableExtra("resultData");
            if(manager==null || data==null || code!=Activity.RESULT_OK) {
                emit("Ekran paylaşımı izni bulunamadı.",null);
                stopSelf();
                return START_NOT_STICKY;
            }
            if(projection!=null) {
                try { projection.stop(); } catch(Exception ignored) {}
            }
            projection=manager.getMediaProjection(code,data);
            if(projection==null) throw new IllegalStateException("MediaProjection boş");
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    emit("Android ekran paylaşımını sonlandırdı.",null);
                    stopSelf();
                }
            },new Handler(Looper.getMainLooper()));
            recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            TranslatorOptions options=new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.TURKISH)
                .build();
            translator=Translation.getClient(options);
            emit("İngilizce–Türkçe model hazırlanıyor… (ilk kullanımda indirilir)",null);
            translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(v -> {
                    modelReady=true;
                    emit("Çeviri aktif • PS Remote Play ekranındaki altyazı aranıyor",null);
                })
                .addOnFailureListener(e -> {
                    modelReady=false;
                    emit("Dil modeli indirilemedi • İnternet bağlantını kontrol et",null);
                });
            createCapture();
        } catch(Exception e) {
            emit("Ekran yakalama başlatılamadı: "+e.getClass().getSimpleName(),null);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void ensureForeground() {
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26) {
            NotificationChannel channel=new NotificationChannel(CHANNEL,"GlossaWhaleOS canlı çeviri",
                NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Android ekran yakalama devam ediyor");
            nm.createNotificationChannel(channel);
        }
        Notification.Builder b=Build.VERSION.SDK_INT>=26
            ? new Notification.Builder(this,CHANNEL) : new Notification.Builder(this);
        Notification n=b.setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("GlossaWhaleOS 2.0")
            .setContentText("Ekrandaki altyazı telefon üzerinde çevriliyor")
            .setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29) startForeground(31,n,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(31,n);
    }

    private synchronized void createCapture() {
        if(projection==null || stopping) return;
        if(virtualDisplay!=null) { virtualDisplay.release(); virtualDisplay=null; }
        if(imageReader!=null) { imageReader.close(); imageReader=null; }
        DisplayMetrics m=new DisplayMetrics();
        WindowManager wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(m);
        int width=Math.max(360,m.widthPixels/2);
        int height=Math.max(240,m.heightPixels/2);
        imageReader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);
        imageReader.setOnImageAvailableListener(this::captureFrame,imageHandler);
        virtualDisplay=projection.createVirtualDisplay(
            "GlossaWhaleOCR",width,height,m.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.getSurface(),null,imageHandler);
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if(projection!=null && imageHandler!=null)
            imageHandler.post(() -> { try { createCapture(); } catch(Exception ignored) {} });
    }

    private void captureFrame(ImageReader source) {
        Image image=null;
        Bitmap frame=null;
        try {
            image=source.acquireLatestImage();
            if(image==null) return;
            long now=System.currentTimeMillis();
            if(now-lastScan<1150 || !modelReady || !inFlight.compareAndSet(false,true)) return;
            lastScan=now;
            final int w=image.getWidth(),h=image.getHeight();
            Image.Plane plane=image.getPlanes()[0];
            ByteBuffer bytes=plane.getBuffer();
            int pixel=plane.getPixelStride();
            int row=plane.getRowStride();
            if(pixel!=4) { inFlight.set(false); return; }
            int paddedWidth=row/pixel;
            frame=Bitmap.createBitmap(paddedWidth,h,Bitmap.Config.ARGB_8888);
            frame.copyPixelsFromBuffer(bytes);
            int y=Math.max(0,(int)(h*0.46));
            int x=Math.max(0,(int)(w*0.035));
            int ww=w-2*x;
            Bitmap crop=Bitmap.createBitmap(frame,x,y,ww,h-y);
            frame.recycle(); frame=null;
            recognizer.process(InputImage.fromBitmap(crop,0))
                .addOnSuccessListener(result -> {
                    String sourceText=result.getText().trim()
                        .replace('\n',' ').replaceAll("\\s+"," ");
                    crop.recycle();
                    onRecognized(sourceText);
                })
                .addOnFailureListener(error -> {
                    crop.recycle();
                    inFlight.set(false);
                });
        } catch(Exception e) {
            inFlight.set(false);
        } finally {
            if(frame!=null && !frame.isRecycled()) frame.recycle();
            if(image!=null) image.close();
        }
    }

    private void onRecognized(String text) {
        if(text.length()<5 || text.length()>320) { candidate="";candidateCount=0;inFlight.set(false);return; }
        if(text.equals(candidate)) candidateCount++;
        else { candidate=text;candidateCount=1; }
        if(candidateCount<2 || text.equals(lastTranslated)) {inFlight.set(false);return;}
        lastTranslated=text;
        emit("Altyazı okundu • Türkçeye çevriliyor…",null);
        translator.translate(text)
            .addOnSuccessListener(turkish -> {
                String translated=turkish.trim();
                if(translated.isEmpty()) { inFlight.set(false); return; }
                emit("Çevrildi • TV'ye gönderiliyor",translated);
                network.execute(() -> {
                    String result=RelayClient.send(tvIp,pin,translated);
                    emit(result,translated);
                });
                inFlight.set(false);
            })
            .addOnFailureListener(error -> {
                emit("Çeviri başarısız • Dil modeli hazır mı?",null);
                lastTranslated="";
                inFlight.set(false);
            });
    }

    private void emit(String state,String subtitle) {
        getSharedPreferences("glossa_v2_phone",MODE_PRIVATE).edit()
            .putString("last_status",state).apply();
        Intent i=new Intent(STATUS);
        i.setPackage(getPackageName());
        i.putExtra("status",state);
        if(subtitle!=null) i.putExtra("subtitle",subtitle);
        sendBroadcast(i);
    }

    @Override public void onDestroy() {
        stopping=true;
        if(imageReader!=null) {imageReader.close();imageReader=null;}
        if(virtualDisplay!=null) {virtualDisplay.release();virtualDisplay=null;}
        if(projection!=null) {try {projection.stop();}catch(Exception ignored){}projection=null;}
        if(recognizer!=null) recognizer.close();
        if(translator!=null) translator.close();
        if(imageThread!=null) imageThread.quitSafely();
        network.shutdownNow();
        emit("Çeviri durduruldu",null);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) {return null;}
}
