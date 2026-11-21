// SPDX-License-Identifier: AGPL-3.0-only
package com.metallic.chiaki.glossa;
import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Theme-aware UI primitives for the restored PSN account wizard. */
public final class GlossaUI {
    public static int BG=0xFF0B1427,TEAL=0xFF59E2D6,WHITE=0xFFF6F9FF,MUTED=0xFFA9BBD2,PANEL=0xFF192A44;
    private GlossaUI(){}
    public static void init(Activity a){
        GlossaTheme t=GlossaTheme.load(a);
        BG=t.bg;TEAL=t.accent;WHITE=t.white;MUTED=t.muted;PANEL=t.panel;
    }
    private static int dp(Activity a,int x){return GlossaTheme.dp(a,x);}
    public static LinearLayout column(Activity a){
        LinearLayout c=new LinearLayout(a);c.setOrientation(LinearLayout.VERTICAL);return c;
    }
    public static void pad(View v,Activity a,int l,int t,int r,int b){
        v.setPadding(dp(a,l),dp(a,t),dp(a,r),dp(a,b));
    }
    public static void add(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    public static LinearLayout.LayoutParams spaced(Activity a,int bottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(a,bottom);return p;
    }
    public static void gap(LinearLayout p,Activity a,int h){
        p.addView(new View(a),new LinearLayout.LayoutParams(1,dp(a,h)));
    }
    public static TextView text(Activity a,String s,int size,int color,boolean bold){
        TextView t=new TextView(a);t.setText(s);t.setTextColor(color);t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    public static LinearLayout card(Activity a,String title,String subtitle){
        LinearLayout c=column(a);
        c.setBackground(GlossaTheme.shape(a,PANEL,18));
        pad(c,a,18,18,18,18);
        add(c,text(a,title,18,WHITE,true));
        gap(c,a,9);
        add(c,text(a,subtitle,14,MUTED,false));
        gap(c,a,15);
        return c;
    }
    public static TextView button(Activity a,String title,boolean primary){
        TextView t=GlossaTheme.load(a).button(a,title,primary);
        t.setMinHeight(dp(a,52));return t;
    }
    public static EditText field(Activity a,String hint,String initial,boolean number){
        EditText e=new EditText(a);
        e.setHint(hint);e.setText(initial);e.setSingleLine(true);
        e.setTextColor(WHITE);e.setHintTextColor(MUTED);
        e.setTextSize(14);e.setSelectAllOnFocus(true);
        e.setInputType(number?android.text.InputType.TYPE_CLASS_NUMBER:
            android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        e.setBackground(GlossaTheme.shape(a,0xFF203652,10));
        pad(e,a,12,13,12,13);
        return e;
    }
}
