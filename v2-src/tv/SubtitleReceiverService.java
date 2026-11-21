package com.darkscorpion.glossasub.tv;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class SubtitleReceiverService extends Service {
    static final String START="com.darkscorpion.glossawhale.v2.tv.START";
    static final String TEST="com.darkscorpion.glossawhale.v2.tv.TEST";
    static final String STATUS="com.darkscorpion.glossawhale.v2.tv.STATUS";
    private static final String CHANNEL="glossa_v2_tv";
    private static final int PORT=8799;
    private static final int DISCOVERY_PORT=8798;

    private final Handler main=new Handler(Looper.getMainLooper());
    private ServerSocket server;
    private Thread listener;
    private Thread discoveryListener;
    private DatagramSocket discoverySocket;
    private WifiManager.MulticastLock wifiDiscoveryLock;
    private WindowManager windowManager;
    private LinearLayout bubbleContainer;
    private TextView bubbleText;
    private WindowManager.LayoutParams bubbleParams;
    private Runnable clearTask;
    private volatile boolean alive=false;
    private String pin;

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        ensureForeground();
        pin=getSharedPreferences("glossa_v2_tv",MODE_PRIVATE).getString("pin","");
        if(!alive) startServer();
        if(intent!=null && TEST.equals(intent.getAction()))
            showSubtitle("GlossaWhaleOS 2.1 hazır! Türkçe altyazı sistemi etkin.",6000);
        return START_STICKY;
    }

    private void ensureForeground() {
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26) {
            NotificationChannel channel=new NotificationChannel(CHANNEL,
                "GlossaWhaleOS TV altyazı alıcısı",NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
        }
        Notification.Builder nb=Build.VERSION.SDK_INT>=26
            ? new Notification.Builder(this,CHANNEL) : new Notification.Builder(this);
        Notification notification=nb.setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("GlossaWhaleOS 2.1 TV")
            .setContentText("Türkçe altyazı bağlantısı açık • Wi-Fi")
            .setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29)
            startForeground(22,notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(22,notification);
    }

    private void startServer() {
        alive=true;
        listener=new Thread(() -> {
            try(ServerSocket socket=new ServerSocket(PORT,12)) {
                socket.setReuseAddress(true);
                server=socket;
                report("TV alıcısı hazır • 8799 • Telefonu eşleştirebilirsin");
                while(alive) {
                    Socket client;
                    try { client=socket.accept(); }
                    catch(SocketException stop) { break; }
                    try { handle(client); }
                    catch(Exception error) { report("Bağlantı isteği işlenemedi"); }
                    finally { try {client.close();}catch(Exception ignored){} }
                }
            } catch(Exception error) {
                if(alive) report("Port 8799 açılamadı • "+error.getClass().getSimpleName());
            } finally { alive=false; }
        },"Glossa-TV-LAN");
        listener.start();
        startDiscoveryResponder();
    }


    /** Announces the TV on local Wi-Fi without revealing its six-digit pairing PIN. */
    private void startDiscoveryResponder() {
        try {
            WifiManager wifi=(WifiManager)getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            if(wifi!=null) {
                wifiDiscoveryLock=wifi.createMulticastLock("GlossaWhaleOS-TV-Discovery");
                wifiDiscoveryLock.setReferenceCounted(false);
                wifiDiscoveryLock.acquire();
            }
        } catch(Exception ignored) {wifiDiscoveryLock=null;}
        discoveryListener=new Thread(() -> {
            try(DatagramSocket socket=new DatagramSocket(DISCOVERY_PORT)) {
                discoverySocket=socket;
                socket.setSoTimeout(1200);
                byte[] response=("GLOSSA_TV_DISCOVERY_RESPONSE v1\nport:8799\n").getBytes(StandardCharsets.US_ASCII);
                while(alive) {
                    byte[] buffer=new byte[256];
                    DatagramPacket request=new DatagramPacket(buffer,buffer.length);
                    try {socket.receive(request);}
                    catch(SocketTimeoutException timeout) {continue;}
                    InetAddress source=request.getAddress();
                    if(source==null || !source.isSiteLocalAddress()) continue;
                    String message=new String(request.getData(),request.getOffset(),
                        request.getLength(),StandardCharsets.US_ASCII);
                    if(!"GLOSSA_TV_DISCOVERY v1\n".equals(message)) continue;
                    socket.send(new DatagramPacket(response,response.length,source,
                        request.getPort()));
                }
            } catch(SocketException stop) {
                if(alive) report("TV otomatik keşif portu kullanılamıyor");
            } catch(Exception error) {
                if(alive) report("TV keşif servisi hatası: "+error.getClass().getSimpleName());
            } finally { discoverySocket=null; }
        },"Glossa-TV-Discovery");
        discoveryListener.start();
    }

    private static String readLine(InputStream in,int max) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        for(int i=0;i<max;i++) {
            int b=in.read();
            if(b==-1 || b=='\n') break;
            if(b!='\r') bytes.write(b);
        }
        return bytes.toString("ISO-8859-1");
    }

    private void handle(Socket client) throws Exception {
        client.setSoTimeout(4500);
        if(!client.getInetAddress().isSiteLocalAddress()) {
            respond(client,403,"Yerel ag baglantisi gerekli");
            return;
        }
        BufferedInputStream input=new BufferedInputStream(client.getInputStream());
        String request=readLine(input,1024);
        String[] fields=request.split(" ");
        if(fields.length<2) {
            respond(client,400,"Gecersiz istek");
            return;
        }
        String route=fields[1];
        boolean health="GET".equals(fields[0]) && route.startsWith("/health?pin=");
        boolean subtitle="POST".equals(fields[0]) && route.startsWith("/subtitle?pin=");
        if(!health && !subtitle) {
            respond(client,404,"Yol bulunamadi");
            return;
        }
        String candidatePin=route.substring(
            health ? "/health?pin=".length() : "/subtitle?pin=".length());
        if(!pin.matches("[0-9]{6}") || !MessageDigest.isEqual(
                pin.getBytes(StandardCharsets.US_ASCII),
                candidatePin.getBytes(StandardCharsets.US_ASCII))) {
            try {Thread.sleep(300);}catch(InterruptedException ignored){}
            respond(client,403,"Eslesme kodu yanlis");
            return;
        }
        if(health) {
            respond(client,200,"GLOSSA_READY");
            return;
        }
        int contentLength=-1;
        for(int i=0;i<32;i++) {
            String line=readLine(input,2048);
            if(line.isEmpty()) break;
            int colon=line.indexOf(':');
            if(colon<0) continue;
            if("content-length".equalsIgnoreCase(line.substring(0,colon).trim()))
                contentLength=Integer.parseInt(line.substring(colon+1).trim());
        }
        if(contentLength<2 || contentLength>4096) {
            respond(client,400,"Gecersiz icerik boyutu");
            return;
        }
        byte[] bytes=new byte[contentLength];
        int offset=0;
        while(offset<bytes.length) {
            int count=input.read(bytes,offset,bytes.length-offset);
            if(count<0) break;
            offset+=count;
        }
        if(offset!=contentLength) {respond(client,400,"Eksik gonderim");return;}
        JSONObject data=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        String text=data.optString("text","").trim();
        if(text.isEmpty() || text.length()>500) {respond(client,400,"Gecersiz altyazi");return;}
        int duration=Math.max(1800,Math.min(12000,data.optInt("duration",6200)));
        showSubtitle(text,duration,data.optJSONObject("style"));
        report("Türkçe altyazı alındı • "+text.substring(0,Math.min(38,text.length())));
        respond(client,200,"OK");
    }

    private static void respond(Socket client,int code,String body) throws IOException {
        byte[] data=body.getBytes(StandardCharsets.UTF_8);
        String reason=code==200?"OK":"Error";
        String header="HTTP/1.1 "+code+" "+reason+"\r\nContent-Type: text/plain; charset=utf-8\r\n"+
            "Content-Length: "+data.length+"\r\nConnection: close\r\n\r\n";
        OutputStream output=client.getOutputStream();
        output.write(header.getBytes(StandardCharsets.US_ASCII));
        output.write(data);
        output.flush();
    }

    private static int bounded(int value,int min,int max) {
        return Math.max(min,Math.min(max,value));
    }
    private static int trustedColor(int value,boolean background) {
        int[] choices=background
            ? new int[]{0xE5081020,0xAA081020,0x00000000,0xE5202642}
            : new int[]{0xFFFFFFFF,0xFFFFEB70,0xFF65E6D6,0xFFB6FFB6};
        for(int option:choices)if(option==value)return option;
        return choices[0];
    }
    private void showSubtitle(String text,int duration) {
        showSubtitle(text,duration,null);
    }
    private void showSubtitle(String text,int duration,JSONObject style) {
        main.post(() -> {
            if(!Settings.canDrawOverlays(this) && Build.VERSION.SDK_INT>=23) {
                report("Overlay izni yok • TV uygulamasından izin ver");
                return;
            }
            try {
                if(bubbleContainer==null) {
                    windowManager=(WindowManager)getSystemService(WINDOW_SERVICE);
                    bubbleContainer=new LinearLayout(this);
                    bubbleContainer.setGravity(Gravity.CENTER);
                    bubbleContainer.setOrientation(LinearLayout.VERTICAL);
                    GlossaUI.pad(bubbleContainer,this,35,3,35,3);
                    bubbleText=GlossaUI.text(this,"",26,GlossaUI.WHITE,true);
                    bubbleText.setGravity(Gravity.CENTER);
                    bubbleText.setShadowLayer(GlossaUI.dp(this,3),0,GlossaUI.dp(this,2),0xFF000000);
                    bubbleText.setBackground(GlossaUI.solid(0xE5081020,12,this));
                    GlossaUI.pad(bubbleText,this,22,12,22,12);
                    bubbleContainer.addView(bubbleText,new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
                    int type=Build.VERSION.SDK_INT>=26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE;
                    WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,type,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                    lp.gravity=Gravity.TOP|Gravity.LEFT;
                    lp.x=0;lp.y=0;
                    bubbleParams=lp;
                    windowManager.addView(bubbleContainer,lp);
                }
                int size=style==null?26:bounded(style.optInt("size",26),16,54);
                int x=style==null?50:bounded(style.optInt("x",50),0,100);
                int y=style==null?87:bounded(style.optInt("y",87),0,100);
                int font=trustedColor(style==null?0xFFFFFFFF:
                    style.optInt("color",0xFFFFFFFF),false);
                int backdrop=trustedColor(style==null?0xE5081020:
                    style.optInt("background",0xE5081020),true);
                bubbleText.setTextSize(size);
                bubbleText.setTextColor(font);
                bubbleText.setBackground(GlossaUI.solid(backdrop,12,this));
                bubbleText.setText(text);
                bubbleContainer.setVisibility(View.VISIBLE);

                DisplayMetrics metrics=new DisplayMetrics();
                windowManager.getDefaultDisplay().getRealMetrics(metrics);
                int availableWidth=Math.max(250,metrics.widthPixels-GlossaUI.dp(this,30));
                bubbleText.setMaxWidth(availableWidth);
                bubbleContainer.measure(
                    View.MeasureSpec.makeMeasureSpec(availableWidth,View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(metrics.heightPixels,View.MeasureSpec.AT_MOST));
                int bubbleWidth=Math.max(1,bubbleContainer.getMeasuredWidth());
                int bubbleHeight=Math.max(1,bubbleContainer.getMeasuredHeight());
                bubbleParams.x=Math.round(
                    (metrics.widthPixels-bubbleWidth)*(x/100f));
                bubbleParams.y=Math.round(
                    (metrics.heightPixels-bubbleHeight)*(y/100f));
                windowManager.updateViewLayout(bubbleContainer,bubbleParams);
                if(clearTask!=null) main.removeCallbacks(clearTask);
                clearTask=() -> {if(bubbleContainer!=null)bubbleContainer.setVisibility(View.GONE);};
                main.postDelayed(clearTask,duration);
            } catch(Exception e) {
                report("TV altyazı katmanı açılamadı: "+e.getClass().getSimpleName());
            }
        });
    }

    private void report(String value) {
        Intent i=new Intent(STATUS);
        i.setPackage(getPackageName());
        i.putExtra("status",value);
        sendBroadcast(i);
    }

    @Override public void onDestroy() {
        alive=false;
        if(server!=null) {try {server.close();}catch(IOException ignored){}}
        if(discoverySocket!=null) discoverySocket.close();
        if(wifiDiscoveryLock!=null && wifiDiscoveryLock.isHeld())
            try {wifiDiscoveryLock.release();}catch(Exception ignored){}
        main.post(() -> {
            if(clearTask!=null)main.removeCallbacks(clearTask);
            if(bubbleContainer!=null && windowManager!=null) {
                try {windowManager.removeView(bubbleContainer);}catch(Exception ignored){}
                bubbleContainer=null;
            }
        });
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i){return null;}
}
