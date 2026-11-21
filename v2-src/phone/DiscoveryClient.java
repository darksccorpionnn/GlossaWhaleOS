package com.darkscorpion.glossasub.phone;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** LAN discovery only: does not perform PSN login, Remote Play registration or streaming. */
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
        String error="";
    }

    static Result scan(Context context) {
        Result result=new Result();
        WifiManager.MulticastLock lock=null;
        try {
            WifiManager wifi=(WifiManager)context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            if(wifi!=null) {
                lock=wifi.createMulticastLock("GlossaWhaleOS-LAN-Discovery");
                lock.setReferenceCounted(false);
                lock.acquire();
            }
        } catch(Exception ignored) { lock=null; }
        try {
            discover(result);
        } catch(Exception error) {
            result.error="Yerel ağ taraması başarısız: "+error.getClass().getSimpleName();
        } finally {
            if(lock!=null && lock.isHeld()) {
                try {lock.release();}catch(Exception ignored){}
            }
        }
        return result;
    }

    private static void discover(Result result) throws Exception {
        Map<String,Ps4> consoles=new LinkedHashMap<>();
        Set<String> tvs=new LinkedHashSet<>();
        Set<InetAddress> destinations=getBroadcastAddresses();
        if(destinations.isEmpty()) {
            result.error="Wi-Fi veya aynı ağdaki bağlantı bulunamadı.";
            return;
        }
        try(DatagramSocket socket=new DatagramSocket()) {
            socket.setBroadcast(true);
            long deadline=SystemClock.elapsedRealtime()+4200;
            for(int attempt=0;attempt<3 && SystemClock.elapsedRealtime()<deadline;attempt++) {
                for(InetAddress address:destinations) {
                    send(socket,address,PS4_PORT,PS4_SEARCH);
                    send(socket,address,TV_PORT,TV_SEARCH);
                }
                long readUntil=Math.min(deadline,SystemClock.elapsedRealtime()+1400);
                while(SystemClock.elapsedRealtime()<readUntil) {
                    int timeout=(int)Math.max(1,Math.min(400,
                        readUntil-SystemClock.elapsedRealtime()));
                    socket.setSoTimeout(timeout);
                    byte[] bytes=new byte[1500];
                    DatagramPacket response=new DatagramPacket(bytes,bytes.length);
                    try {socket.receive(response);}
                    catch(SocketTimeoutException ignored) {continue;}
                    InetAddress from=response.getAddress();
                    if(!(from instanceof Inet4Address) || !from.isSiteLocalAddress())
                        continue;
                    String ip=from.getHostAddress();
                    String packet=new String(response.getData(),response.getOffset(),
                        response.getLength(),StandardCharsets.UTF_8);
                    if(response.getPort()==TV_PORT &&
                            packet.startsWith("GLOSSA_TV_DISCOVERY_RESPONSE v1\n") &&
                            packet.contains("\nport:8799\n")) {
                        tvs.add(ip);
                    } else if(response.getPort()==PS4_PORT &&
                            (packet.startsWith("HTTP/1.1 200") ||
                             packet.startsWith("HTTP/1.1 620"))) {
                        Map<String,String> fields=parseHeaders(packet);
                        String id=fields.get("host-id");
                        String protocol=fields.get("device-discovery-protocol-version");
                        String type=fields.get("host-type");
                        if(id==null || id.length()<4 ||
                                !"00020020".equals(protocol) ||
                                type==null || !type.toUpperCase(Locale.US).contains("PS4"))
                            continue;
                        String key=id.replaceAll("[^A-Za-z0-9:-]","");
                        if(key.isEmpty() || consoles.size()>=12 && !consoles.containsKey(key))
                            continue;
                        String name=clean(fields.get("host-name"),"PlayStation 4");
                        String firmware=clean(fields.get("system-version"),"Bilinmiyor");
                        String state=packet.startsWith("HTTP/1.1 620")?"Dinlenme modu":"Hazır";
                        consoles.put(key,new Ps4(ip,key,name,state,firmware));
                    }
                }
            }
        }
        result.consoles.addAll(consoles.values());
        result.tvAddresses.addAll(tvs);
    }

    private static void send(DatagramSocket socket,InetAddress address,int port,String text) {
        try {
            byte[] data=text.getBytes(StandardCharsets.US_ASCII);
            socket.send(new DatagramPacket(data,data.length,address,port));
        } catch(Exception ignored) {}
    }

    private static Set<InetAddress> getBroadcastAddresses() throws Exception {
        Set<InetAddress> result=new LinkedHashSet<>();
        Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();
        if(all==null) return result;
        for(NetworkInterface ni:Collections.list(all)) {
            if(!ni.isUp() || ni.isLoopback()) continue;
            for(InterfaceAddress iface:ni.getInterfaceAddresses()) {
                InetAddress ip=iface.getAddress();
                if(!(ip instanceof Inet4Address) || !ip.isSiteLocalAddress()) continue;
                InetAddress broadcast=iface.getBroadcast();
                if(broadcast instanceof Inet4Address) result.add(broadcast);
            }
        }
        if(!result.isEmpty()) result.add(InetAddress.getByName("255.255.255.255"));
        return result;
    }

    private static Map<String,String> parseHeaders(String packet) {
        Map<String,String> fields=new LinkedHashMap<>();
        String[] lines=packet.replace("\r","").split("\n");
        for(int i=1;i<lines.length && i<32;i++) {
            int colon=lines[i].indexOf(':');
            if(colon>0) fields.put(lines[i].substring(0,colon).trim()
                .toLowerCase(Locale.US),lines[i].substring(colon+1).trim());
        }
        return fields;
    }

    private static String clean(String value,String fallback) {
        if(value==null || value.isEmpty()) return fallback;
        value=value.replaceAll("[\\p{Cntrl}]"," ").trim();
        return value.length()>48?value.substring(0,48):value;
    }

    private DiscoveryClient(){}
}
