package com.darkscorpion.glossasub.phone;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.wifi.WifiManager;
import android.os.SystemClock;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Local, non-invasive UDP discovery. No PSN auth or Remote Play session exists here. */
final class DiscoveryClient {
    private static final int PS4_PORT=987;
    private static final int TV_PORT=8798;
    private static final String PS4_SEARCH=
        "SRCH * HTTP/1.1\ndevice-discovery-protocol-version:00020020\n";
    private static final String TV_SEARCH="GLOSSA_TV_DISCOVERY v1\n";

    static final class Ps4 {
        final String ip;
        final String id;
        final String name;
        final String state;
        final String systemVersion;
        Ps4(String ip,String id,String name,String state,String systemVersion) {
            this.ip=ip; this.id=id; this.name=name; this.state=state;
            this.systemVersion=systemVersion;
        }
    }
    static final class Result {
        final ArrayList<Ps4> consoles=new ArrayList<>();
        final ArrayList<String> tvAddresses=new ArrayList<>();
        String localIp="Bilinmiyor";
        String error="";
        int packetsSent=0;
        int replies=0;
        int ps4Candidates=0;
        boolean fallbackScan=false;
        String diagnostics() {
            return "Telefon IP: "+localIp+"  |  Gönderilen UDP: "+packetsSent+
                "  |  Gelen yanıt: "+replies+
                "  |  PS4 yanıtı: "+ps4Candidates+
                (fallbackScan?"  |  Alt ağ taraması: yapıldı":"");
        }
    }

    private static final class WifiLink {
        final Network network;
        final Inet4Address address;
        final int prefix;
        final InetAddress broadcast;
        WifiLink(Network network,Inet4Address address,int prefix,InetAddress broadcast) {
            this.network=network; this.address=address;this.prefix=prefix;
            this.broadcast=broadcast;
        }
    }

    static Result scan(Context context,String savedPs4Ip) {
        Result result=new Result();
        WifiManager.MulticastLock lock=null;
        try {
            WifiManager wifi=(WifiManager)context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            if(wifi!=null) {
                lock=wifi.createMulticastLock("GlossaWhaleOS-PS4-Find");
                lock.setReferenceCounted(false);
                lock.acquire();
            }
        } catch(Exception ignored) { lock=null; }
        try {
            discover(context,result,savedPs4Ip);
        } catch(Exception ex) {
            result.error="Ağ tarama hatası: "+ex.getClass().getSimpleName();
        } finally {
            if(lock!=null && lock.isHeld()) {
                try {lock.release();} catch(Exception ignored){}
            }
        }
        return result;
    }

    private static void discover(Context context,Result result,String savedPs4Ip)
        throws Exception {
        WifiLink link=findWifiLink(context);
        if(link==null) {
            result.error="Wi-Fi IPv4 bağlantısı algılanamadı. Telefonun Wi-Fi üzerinden bağlı olduğundan emin ol.";
            return;
        }
        result.localIp=link.address.getHostAddress();
        Set<InetAddress> broadcasts=new LinkedHashSet<>();
        if(link.broadcast!=null) broadcasts.add(link.broadcast);
        broadcasts.add(InetAddress.getByName("255.255.255.255"));
        Map<String,Ps4> consoles=new LinkedHashMap<>();
        Set<String> tvs=new LinkedHashSet<>();

        try(DatagramSocket socket=new DatagramSocket()) {
            socket.setBroadcast(true);
            // A mobile device can have mobile-data as default even with Wi-Fi connected.
            // Explicitly route this UDP socket through Wi-Fi, including networks without internet.
            if(link.network!=null) link.network.bindSocket(socket);
            for(int pass=0;pass<3;pass++) {
                for(InetAddress target:broadcasts) {
                    send(socket,target,PS4_PORT,PS4_SEARCH,result);
                    send(socket,target,TV_PORT,TV_SEARCH,result);
                }
                if(pass==0 && RelayClient.validLocalIp(savedPs4Ip)) {
                    try {
                        send(socket,InetAddress.getByName(savedPs4Ip),PS4_PORT,PS4_SEARCH,result);
                    }catch(Exception ignored){}
                }
                collect(socket,consoles,tvs,result,780);
            }

            if(consoles.isEmpty()) {
                result.fallbackScan=true;
                // Directed unicast is often delivered when APs suppress broadcast traffic.
                // Limit work to a single /24 of the actual Wi-Fi IPv4 address (max 253 peers).
                byte[] local=link.address.getAddress();
                long started=SystemClock.elapsedRealtime();
                for(int host=1;host<255 && consoles.isEmpty();host++) {
                    if(SystemClock.elapsedRealtime()-started>6200) break;
                    if((local[3]&255)==host) continue;
                    InetAddress target=InetAddress.getByAddress(
                        new byte[]{local[0],local[1],local[2],(byte)host});
                    send(socket,target,PS4_PORT,PS4_SEARCH,result);
                    if(host%24==0) collect(socket,consoles,tvs,result,230);
                    else SystemClock.sleep(5);
                }
                collect(socket,consoles,tvs,result,900);
            }
        }
        result.consoles.addAll(consoles.values());
        result.tvAddresses.addAll(tvs);
    }

    /** Collect console headers without requiring a particular firmware header version. */
    private static void collect(DatagramSocket socket,Map<String,Ps4> consoles,
        Set<String> tvs,Result result,int durationMs) throws Exception {
        final long until=SystemClock.elapsedRealtime()+durationMs;
        while(SystemClock.elapsedRealtime()<until) {
            socket.setSoTimeout((int)Math.max(1,Math.min(160,
                until-SystemClock.elapsedRealtime())));
            byte[] raw=new byte[1500];
            DatagramPacket response=new DatagramPacket(raw,raw.length);
            try {socket.receive(response);}
            catch(SocketTimeoutException timeout) {continue;}
            InetAddress from=response.getAddress();
            if(!(from instanceof Inet4Address) || !from.isSiteLocalAddress()) continue;
            result.replies++;
            String ip=from.getHostAddress();
            String payload=new String(response.getData(),response.getOffset(),
                response.getLength(),StandardCharsets.UTF_8);
            String normalized=payload.replace("\r","");
            if(normalized.startsWith("GLOSSA_TV_DISCOVERY_RESPONSE v1\n") &&
                normalized.contains("\nport:8799\n")) {
                tvs.add(ip);
                continue;
            }
            if(!normalized.startsWith("HTTP/1.1 200") &&
                !normalized.startsWith("HTTP/1.1 620")) continue;
            Map<String,String> headers=parseHeaders(normalized);
            String type=headers.get("host-type");
            String id=headers.get("host-id");
            String name=headers.get("host-name");
            // Do not reject valid PS4 firmware for an unexpected discovery version.
            // A recognizable PS4 host-type or the PS4's host ID + request port suffice.
            boolean isPs4=(type!=null &&
                type.toUpperCase(Locale.US).contains("PS4")) ||
                (id!=null && !id.isEmpty() && name!=null &&
                 headers.containsKey("host-request-port"));
            if(!isPs4) continue;
            result.ps4Candidates++;
            String key=(id!=null && !id.isEmpty())?
                id.replaceAll("[^A-Za-z0-9:-]","") : ip;
            if(key.isEmpty()) key=ip;
            if(!consoles.containsKey(key) && consoles.size()>=12) continue;
            String firmware=clean(headers.get("system-version"),"Bilinmiyor");
            String state=normalized.startsWith("HTTP/1.1 620")?"Dinlenme modu":"Hazır";
            consoles.put(key,new Ps4(ip,key,clean(name,"PlayStation 4"),state,firmware));
        }
    }

    private static WifiLink findWifiLink(Context context) {
        ConnectivityManager manager=(ConnectivityManager)
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(manager==null) return null;
        for(Network net:manager.getAllNetworks()) {
            NetworkCapabilities cap=manager.getNetworkCapabilities(net);
            if(cap==null || !cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                continue;
            LinkProperties props=manager.getLinkProperties(net);
            if(props==null) continue;
            for(LinkAddress link:props.getLinkAddresses()) {
                InetAddress addr=link.getAddress();
                if(!(addr instanceof Inet4Address) || !addr.isSiteLocalAddress())
                    continue;
                int prefix=link.getPrefixLength();
                byte[] octets=addr.getAddress();
                int local=((octets[0]&255)<<24)|((octets[1]&255)<<16)|
                    ((octets[2]&255)<<8)|(octets[3]&255);
                int mask=prefix==0?0:(int)(0xFFFFFFFFL<<(32-prefix));
                int broadcast=(local&mask)|(~mask);
                byte[] bytes={(byte)(broadcast>>>24),(byte)(broadcast>>>16),
                    (byte)(broadcast>>>8),(byte)broadcast};
                try {
                    return new WifiLink(net,(Inet4Address)addr,prefix,
                        InetAddress.getByAddress(bytes));
                } catch(Exception ignored) {}
            }
        }
        return null;
    }

    private static void send(DatagramSocket socket,InetAddress target,int port,
        String message,Result report) {
        try {
            byte[] bytes=message.getBytes(StandardCharsets.US_ASCII);
            socket.send(new DatagramPacket(bytes,bytes.length,target,port));
            report.packetsSent++;
        } catch(Exception ignored){}
    }

    private static Map<String,String> parseHeaders(String text) {
        Map<String,String> out=new LinkedHashMap<>();
        String[] lines=text.split("\n");
        for(int i=1;i<lines.length && i<40;i++) {
            int colon=lines[i].indexOf(':');
            if(colon<1) continue;
            out.put(lines[i].substring(0,colon).trim().toLowerCase(Locale.US),
                lines[i].substring(colon+1).trim());
        }
        return out;
    }

    private static String clean(String value,String fallback) {
        if(value==null || value.isEmpty()) return fallback;
        value=value.replaceAll("[\\p{Cntrl}]"," ").trim();
        return value.length()>48?value.substring(0,48):value;
    }

    private DiscoveryClient(){}
}
