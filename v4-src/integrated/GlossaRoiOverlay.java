// SPDX-License-Identifier: AGPL-3.0-only
package com.metallic.chiaki.glossa;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;

/**
 * Interactive OCR selection rectangle expressed in relative *video-surface*
 * coordinates. This avoids incorrect crops when Chiaki adds black bars.
 * Enabled only during adjustment; does not intercept controls while gaming.
 */
public final class GlossaRoiOverlay extends View {
    public static final String P="glossa_live_v3";
    private final SharedPreferences prefs;
    private final SurfaceView source;
    private final Paint fill=new Paint(3),edge=new Paint(3),handle=new Paint(3),label=new Paint(3);
    private float l,t,r,b;
    private int gesture=0;
    private float startX,startY,sl,st,sr,sb;
    private final int[] sourceLoc=new int[2],viewLoc=new int[2];
    private final RectF bounds=new RectF();

    public GlossaRoiOverlay(Activity activity,SurfaceView source) {
        super(activity);
        this.source=source;
        prefs=activity.getSharedPreferences(P,Activity.MODE_PRIVATE);
        l=prefs.getFloat("roi_l",.08f);
        t=prefs.getFloat("roi_t",.55f);
        r=prefs.getFloat("roi_r",.92f);
        b=prefs.getFloat("roi_b",.96f);
        fill.setColor(0x2265E6D6); fill.setStyle(Paint.Style.FILL);
        edge.setColor(GlossaTheme.load(activity).accent); edge.setStrokeWidth(dp(3)); edge.setStyle(Paint.Style.STROKE);
        handle.setColor(GlossaTheme.load(activity).accent);
        label.setColor(0xFFFFFFFF);label.setTextSize(dp(13));label.setFakeBoldText(true);
        setVisibility(GONE);
    }
    private float dp(int n) {return n*getResources().getDisplayMetrics().density;}
    private static float limit(float f,float lo,float hi){return Math.min(hi,Math.max(lo,f));}
    public void setEditing(boolean edit) {
        setVisibility(edit?VISIBLE:GONE);
        setClickable(edit);
        invalidate();
    }
    private boolean updateBounds() {
        if(source.getWidth()<100||source.getHeight()<100)return false;
        source.getLocationOnScreen(sourceLoc);
        getLocationOnScreen(viewLoc);
        float x=sourceLoc[0]-viewLoc[0],y=sourceLoc[1]-viewLoc[1];
        bounds.set(x+l*source.getWidth(),y+t*source.getHeight(),
            x+r*source.getWidth(),y+b*source.getHeight());
        return true;
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if(!updateBounds())return;
        c.drawRoundRect(bounds,dp(8),dp(8),fill);
        c.drawRoundRect(bounds,dp(8),dp(8),edge);
        c.drawCircle(bounds.right,bounds.bottom,dp(12),handle);
        c.drawText("OCR  •  Taşı / sağ alttan boyutlandır",
            Math.max(dp(10),bounds.left),Math.max(dp(22),bounds.top-dp(10)),label);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if(getVisibility()!=VISIBLE||!updateBounds())return false;
        float x=e.getX(),y=e.getY();
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                float dist=(float)Math.hypot(x-bounds.right,y-bounds.bottom);
                gesture=dist<dp(43)?2:bounds.contains(x,y)?1:0;
                if(gesture==0)return true;
                startX=x;startY=y;sl=l;st=t;sr=r;sb=b;
                return true;
            case MotionEvent.ACTION_MOVE:
                if(gesture==0)return true;
                float dx=(x-startX)/source.getWidth(),dy=(y-startY)/source.getHeight();
                if(gesture==1) {
                    float width=sr-sl,height=sb-st;
                    l=limit(sl+dx,0f,1f-width);r=l+width;
                    t=limit(st+dy,0f,1f-height);b=t+height;
                } else {
                    r=limit(sr+dx,l+.18f,1f);
                    b=limit(sb+dy,t+.10f,1f);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if(gesture!=0) {
                    prefs.edit().putFloat("roi_l",l).putFloat("roi_t",t)
                        .putFloat("roi_r",r).putFloat("roi_b",b).apply();
                }
                gesture=0;return true;
            case MotionEvent.ACTION_CANCEL:
                gesture=0;return true;
            default:return true;
        }
    }
}
