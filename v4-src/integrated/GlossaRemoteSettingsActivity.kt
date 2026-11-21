// SPDX-License-Identifier: AGPL-3.0-only
package com.metallic.chiaki.glossa

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.metallic.chiaki.common.Preferences

/**
 * Branded controls for the real Chiaki Preferences shared with the stream.
 * Changing stream quality applies to the NEXT Remote Play connection.
 */
class GlossaRemoteSettingsActivity:AppCompatActivity() {
    private lateinit var theme:GlossaTheme
    private lateinit var prefs:Preferences
    private lateinit var root:LinearLayout
    private fun dp(n:Int)=GlossaTheme.dp(this,n)
    private fun add(v:View,bottom:Int=0) {
        val lp=LinearLayout.LayoutParams(-1,-2)
        lp.bottomMargin=dp(bottom)
        root.addView(v,lp)
    }
    private fun text(value:String,size:Int=15,bold:Boolean=false)=
        theme.text(this,value,size,bold,if(bold)theme.white else theme.muted)
    private fun spinner(title:String,values:List<String>,selected:Int,onSelected:(Int)->Unit) {
        add(text(title,15,true),6)
        val sp=Spinner(this)
        sp.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,values)
        sp.setSelection(selected)
        sp.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,
                view:View?, position:Int,id:Long) {onSelected(position)}
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
        }
        add(sp,15)
    }
    @Suppress("DEPRECATION")
    private fun toggle(name:String,initial:Boolean,onChange:(Boolean)->Unit) {
        val sw=Switch(this)
        sw.text=name
        sw.setTextColor(theme.white)
        sw.textSize=15f
        sw.isChecked=initial
        sw.setOnCheckedChangeListener{_,value->onChange(value)}
        add(sw,12)
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        theme=GlossaTheme.load(this)
        prefs=Preferences(this)
        window.statusBarColor=theme.bg
        window.navigationBarColor=theme.bg
        val scroll=ScrollView(this).apply{setBackgroundColor(theme.bg);isFillViewport=true}
        root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(22),dp(28),dp(22),dp(28))
        }
        scroll.addView(root)
        add(theme.text(this,"GLOSSA LAB  /  REMOTE PLAY",12,true,theme.accent),12)
        add(text("Görüntü ve kontrol ayarları",26,true),8)
        add(text("Bu ayarlar doğrudan PS4 bağlantı motoruna uygulanır. Görüntü ayarları yeni bağlantıda geçerli olur.",14),22)

        spinner("Çözünürlük",listOf("360p","540p","720p","1080p"),
            Preferences.Resolution.values().indexOf(prefs.resolution)){position->
            prefs.resolution=Preferences.Resolution.values()[position]
        }
        spinner("Akıcılık",listOf("30 FPS","60 FPS"),
            Preferences.FPS.values().indexOf(prefs.fps)){position->
            prefs.fps=Preferences.FPS.values()[position]
        }
        spinner("Video kodlayıcı",listOf("H.264 • PS4 için önerilen","H.265 • desteklenen cihazlar"),
            Preferences.Codec.values().indexOf(prefs.codec)){position->
            prefs.codec=Preferences.Codec.values()[position]
        }
        add(text("Bit hızı (kbps)",15,true),7)
        val bitLabel=text("Otomatik",14)
        add(bitLabel,5)
        val bit=SeekBar(this)
        bit.max=48
        val current=prefs.bitrate
        bit.progress=if(current==null)0 else (current/1000-2).coerceIn(0,48)
        add(bit,7)
        val auto=android.widget.CheckBox(this).apply{
            text="Bit hızını otomatik seç"
            setTextColor(theme.white)
            isChecked=current==null
        }
        add(auto,14)
        fun updateBitrate() {
            val selected=2000+bit.progress*1000
            bit.isEnabled=!auto.isChecked
            bitLabel.text=if(auto.isChecked)"Otomatik" else "$selected kbps"
            prefs.bitrate=if(auto.isChecked)null else selected
        }
        bit.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar:SeekBar?,p:Int,fromUser:Boolean){
                if(fromUser)updateBitrate()
            }
            override fun onStartTrackingTouch(bar:SeekBar?) {}
            override fun onStopTrackingTouch(bar:SeekBar?) {}
        })
        auto.setOnCheckedChangeListener{_,_->updateBitrate()}
        updateBitrate()
        add(text("Kol ve dokunmatik ekran",19,true),13)
        toggle("Ekran üzerindeki kontrol tuşları",prefs.onScreenControlsEnabled){
            prefs.onScreenControlsEnabled=it
        }
        toggle("Kumanda titreşimi",prefs.rumbleEnabled){prefs.rumbleEnabled=it}
        toggle("Hareket sensörü",prefs.motionEnabled){prefs.motionEnabled=it}
        toggle("Dokunmatik titreşim",prefs.buttonHapticEnabled){prefs.buttonHapticEnabled=it}
        toggle("Çarpı / daire tuşlarını değiştir",prefs.swapCrossMoon){prefs.swapCrossMoon=it}
        add(theme.button(this,"✓  Kaydet ve Glossa'ya dön",true).apply{
            setOnClickListener{finish()}
        })
        setContentView(scroll)
    }
}
