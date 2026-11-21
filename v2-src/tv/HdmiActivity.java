package com.darkscorpion.glossasub.tv;

import android.app.Activity;
import android.content.Context;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

public final class HdmiActivity extends Activity {
    private TvView hdmi;
    private TextView status;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(GlossaUI.BG);
        hdmi=new TvView(this);
        hdmi.setFocusable(false);
        root.addView(hdmi,new FrameLayout.LayoutParams(-1,-1));
        status=GlossaUI.text(this,"HDMI kaynağı aranıyor…",16,GlossaUI.WHITE,true);
        status.setBackground(GlossaUI.solid(0xDD0D1830,14,this));
        GlossaUI.pad(status,this,18,12,18,12);
        FrameLayout.LayoutParams label=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);
        label.leftMargin=GlossaUI.dp(this,28);
        label.topMargin=GlossaUI.dp(this,28);
        root.addView(status,label);
        TextView footer=GlossaUI.text(this,"Geri tuşu: Kontrol paneli   ·   Altyazılar TV alıcısından gelir",
            14,GlossaUI.MUTED,false);
        FrameLayout.LayoutParams bottom=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        bottom.bottomMargin=GlossaUI.dp(this,18);
        root.addView(footer,bottom);
        setContentView(root);

        hdmi.setCallback(new TvView.TvInputCallback() {
            @Override public void onVideoAvailable(String id) {
                status.setText("HDMI görüntüsü hazır • Türkçe altyazı bağlantısı açık");
            }
            @Override public void onVideoUnavailable(String id,int reason) {
                status.setText("HDMI görüntüsü bekleniyor • kaynak kontrol et");
            }
            @Override public void onConnectionFailed(String id) {
                status.setText("TV HDMI kaynağına üçüncü taraf erişimini engelliyor");
            }
        });
        try {
            TvInputManager manager=(TvInputManager)getSystemService(Context.TV_INPUT_SERVICE);
            List<TvInputInfo> available=manager.getTvInputList();
            TvInputInfo selected=null;
            for(TvInputInfo info:available) {
                if(!info.isPassthroughInput())continue;
                if(selected==null)selected=info;
                CharSequence labelName=info.loadLabel(this);
                if(labelName!=null && labelName.toString().toLowerCase().contains("hdmi 1")) {
                    selected=info;
                    break;
                }
            }
            if(selected==null) {
                status.setText("WhaleOS HDMI passthrough kaynağını uygulamalara açmıyor. TV kumandasıyla HDMI'ye dön.");
                return;
            }
            Uri channel=TvContract.buildChannelUriForPassthroughInput(selected.getId());
            hdmi.tune(selected.getId(),channel);
        } catch(Exception error) {
            status.setText("HDMI açılamadı: "+error.getClass().getSimpleName()+
                " • TV kumandasıyla HDMI'ye dön");
        }
    }
    @Override public void onDestroy() {
        if(hdmi!=null)hdmi.reset();
        super.onDestroy();
    }
}
