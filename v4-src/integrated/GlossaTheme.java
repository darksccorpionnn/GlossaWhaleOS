// SPDX-License-Identifier: AGPL-3.0-only
package com.metallic.chiaki.glossa;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.graphics.Typeface;

/** User-selectable theme shared by the unified dashboard and settings. */
public final class GlossaTheme {
    public static final String PREF="glossa_live_v3";
    public static final String[] NAMES={"Okyanus","Gece moru","Ateş","Zümrüt","Buz"};
    private static final int[] BG={0xFF0B1427,0xFF130F26,0xFF211411,0xFF0B1D19,0xFF101A29};
    private static final int[] PANEL={0xFF192A44,0xFF29213F,0xFF39241F,0xFF19372F,0xFF22334B};
    private static final int[] ACCENT={0xFF59E2D6,0xFFC39AFF,0xFFFFB36B,0xFF6DE4AA,0xFF9DDFFF};
    private static final int WHITE=0xFFF6F9FF;
    public final int bg,panel,accent,white=WHITE,muted=0xFFA9BBD2;
    public final int index;
    public final boolean custom;

    private GlossaTheme(int bg,int panel,int accent,int index,boolean custom) {
        this.bg=bg;this.panel=panel;this.accent=accent;this.index=index;this.custom=custom;
    }

    public static GlossaTheme load(Context ctx) {
        SharedPreferences p=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        int i=Math.max(0,Math.min(NAMES.length-1,p.getInt("ui_theme",0)));
        boolean custom=p.getBoolean("ui_custom",false);
        int color=custom?Color.rgb(p.getInt("ui_red",89),p.getInt("ui_green",226),
            p.getInt("ui_blue",214)):ACCENT[i];
        return new GlossaTheme(BG[i],PANEL[i],color,i,custom);
    }
    public static void savePreset(Context c,int id) {
        int i=Math.max(0,Math.min(NAMES.length-1,id));
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .putInt("ui_theme",i).putBoolean("ui_custom",false).apply();
    }
    public static void saveCustom(Context c,int red,int green,int blue) {
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .putBoolean("ui_custom",true).putInt("ui_red",clamp(red))
            .putInt("ui_green",clamp(green)).putInt("ui_blue",clamp(blue)).apply();
    }
    private static int clamp(int v){return Math.max(0,Math.min(255,v));}
    public static int dp(Context c,float n){
        return Math.round(c.getResources().getDisplayMetrics().density*n);
    }
    public static GradientDrawable shape(Context c,int color,int radius) {
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(c,radius));
        return shape;
    }
    public static GradientDrawable bordered(Context c,int color,int border,int radius,int width) {
        GradientDrawable shape=shape(c,color,radius);
        shape.setStroke(dp(c,width),border);
        return shape;
    }
    public TextView text(Context context,String value,int size,boolean bold,int color){
        TextView t=new TextView(context);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setIncludeFontPadding(false);
        return t;
    }
    public TextView button(Context context,String caption,boolean primary) {
        TextView t=text(context,caption,16,true,primary?bg:white);
        t.setMinHeight(dp(context,56));
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        t.setFocusable(true);
        t.setPadding(dp(context,12),dp(context,12),dp(context,12),dp(context,12));
        StateListDrawable drawable=new StateListDrawable();
        int normal=primary?accent:panel;
        int focused=primary?white:accent;
        drawable.addState(new int[]{android.R.attr.state_focused},
            bordered(context,focused,accent,13,2));
        drawable.addState(new int[]{android.R.attr.state_pressed},
            bordered(context,focused,accent,13,2));
        drawable.addState(new int[]{},shape(context,normal,13));
        t.setBackground(drawable);
        t.setTextColor(new ColorStateList(new int[][] {
            new int[]{android.R.attr.state_focused},
            new int[]{android.R.attr.state_pressed},new int[]{}
        },new int[]{bg,bg,primary?bg:white}));
        return t;
    }
}
