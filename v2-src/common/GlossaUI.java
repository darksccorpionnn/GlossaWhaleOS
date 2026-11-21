package com.darkscorpion.glossasub.__MODULE__;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.InputType;

final class GlossaUI {
    static final int BG = 0xFF080F22;
    static final int SURFACE = 0xFF14213B;
    static final int CARD = 0xFF182744;
    static final int WHITE = 0xFFF4F8FF;
    static final int MUTED = 0xFFA7B8D2;
    static final int TEAL = 0xFF51E0D3;
    static final int BLUE = 0xFF668BFF;
    static final int PURPLE = 0xFFAC86FF;

    static int dp(Context c, float amount) {
        return (int) (amount * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static GradientDrawable solid(int color, int radius, Context c) {
        GradientDrawable b = new GradientDrawable();
        b.setColor(color);
        b.setCornerRadius(dp(c, radius));
        return b;
    }

    static Drawable gradient(Context c, int colorA, int colorB, int radius) {
        GradientDrawable g = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[]{colorA, colorB});
        g.setCornerRadius(dp(c, radius));
        return g;
    }

    static LinearLayout column(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    static LinearLayout row(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextColor(color);
        v.setTextSize(sp);
        v.setIncludeFontPadding(false);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    static void pad(View v, Context c, int l, int t, int r, int b) {
        v.setPadding(dp(c,l),dp(c,t),dp(c,r),dp(c,b));
    }

    static void gap(LinearLayout p, Context c, int height) {
        View v = new View(c);
        p.addView(v, new LinearLayout.LayoutParams(1, dp(c,height)));
    }

    static void add(LinearLayout p, View child) {
        p.addView(child, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    static TextView button(Context c, String title, boolean primary) {
        TextView b = text(c,title,16,primary?BG:WHITE,true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c,54));
        b.setBackground(primary ? gradient(c,TEAL,BLUE,16) : solid(0xFF263957,16,c));
        b.setFocusable(true);
        b.setClickable(true);
        b.setElevation(dp(c,2));
        pad(b,c,18,15,18,15);
        return b;
    }

    static EditText field(Context c, String hint, String value, boolean secret) {
        EditText v = new EditText(c);
        v.setSingleLine(true);
        v.setTextColor(WHITE);
        v.setHintTextColor(0xFF8198B5);
        v.setTextSize(16);
        v.setHint(hint);
        v.setText(value);
        v.setSelectAllOnFocus(false);
        v.setInputType(secret
            ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
            : InputType.TYPE_CLASS_TEXT);
        v.setBackground(solid(0xFF0D1830,13,c));
        pad(v,c,16,15,16,15);
        return v;
    }

    static LinearLayout card(Context c, String title, String description) {
        LinearLayout p = column(c);
        p.setBackground(solid(CARD,20,c));
        pad(p,c,20,20,20,20);
        add(p,text(c,title,20,WHITE,true));
        gap(p,c,7);
        add(p,text(c,description,14,MUTED,false));
        gap(p,c,17);
        return p;
    }

    static LinearLayout.LayoutParams spaced(Context c, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin=dp(c,bottom);
        return lp;
    }

    static void status(TextView view, String s) { view.post(() -> view.setText(s)); }
}
