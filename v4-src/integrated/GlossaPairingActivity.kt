// SPDX-License-Identifier: AGPL-3.0-only
package com.metallic.chiaki.glossa

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.metallic.chiaki.lib.RegistInfo
import com.metallic.chiaki.lib.Target
import com.metallic.chiaki.regist.RegistExecuteActivity

/** Glossa's own PS4 (8+) registration wizard, delegating to Chiaki's engine. */
class GlossaPairingActivity: AppCompatActivity() {
    companion object {
        const val EXTRA_HOST="glossa_pairing_host"
        const val EXTRA_MANUAL_ID="glossa_pairing_manual_id"
    }
    private lateinit var t:GlossaTheme
    private lateinit var host:EditText
    private lateinit var pin:EditText
    private lateinit var account:TextView
    private lateinit var message:TextView
    private fun dp(n:Int)=GlossaTheme.dp(this,n)
    private fun gap(p:LinearLayout,n:Int) {
        p.addView(View(this),LinearLayout.LayoutParams(1,dp(n)))
    }
    private fun add(p:LinearLayout,v:View,bottom:Int=0){
        val lp=LinearLayout.LayoutParams(-1,-2)
        lp.bottomMargin=dp(bottom)
        p.addView(v,lp)
    }
    private fun card(title:String,subtitle:String):LinearLayout {
        val c=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(20),dp(18),dp(20))
            background=GlossaTheme.shape(this@GlossaPairingActivity,t.panel,18)
        }
        add(c,t.text(this,title,18,true,t.white),7)
        add(c,t.text(this,subtitle,14,false,t.muted),15)
        return c
    }
    private fun field(value:String,hint:String,numeric:Boolean):EditText =
        EditText(this).apply {
            setText(value)
            this.hint=hint
            isSingleLine=true
            textSize=16f
            setTextColor(t.white)
            setHintTextColor(t.muted)
            inputType=if(numeric) android.text.InputType.TYPE_CLASS_NUMBER
                else android.text.InputType.TYPE_CLASS_PHONE
            setPadding(dp(12),dp(14),dp(12),dp(14))
            background=GlossaTheme.shape(this@GlossaPairingActivity,0xFF203652,12)
        }
    private val register=registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if(result.resultCode==Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK)
            finish()
        } else {
            message.text="Eşleştirme tamamlanamadı. PS4 IP, hesap ID ve 8 haneli kodu kontrol et."
            message.setTextColor(0xFFFFB3A2.toInt())
        }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        t=GlossaTheme.load(this)
        window.statusBarColor=t.bg;window.navigationBarColor=t.bg
        val scroll=ScrollView(this).apply { setBackgroundColor(t.bg);isFillViewport=true }
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(22),dp(25),dp(22),dp(35))
        }
        scroll.addView(root)
        add(root,t.text(this,"GLOSSA LAB  /  PS4 KURULUM",12,true,t.accent),10)
        add(root,t.text(this,"PS4'ü hesabınla eşleştir",27,true,t.white),9)
        add(root,t.text(this,
            "İkinci PSN hesabınla Remote Play bağlantısı oluştur. Eşleştirme yalnızca ilk kurulumda gerekir.",
            14,false,t.muted),20)

        val psn=card("01  •  PSN hesabı",
            "Sony girişinden aldığın Account ID otomatik kullanılacak.")
        account=t.text(this,"Hesap bilgisi kontrol ediliyor…",15,true,t.accent)
        add(psn,account,11)
        add(psn,t.button(this,"Hesabı ekle / Account ID al",false).apply {
            setOnClickListener {
                startActivity(Intent(this@GlossaPairingActivity,PsnAccountActivity::class.java))
            }
        })
        add(root,psn,14)

        val conn=card("02  •  Konsol bağlantısı",
            "PS4 IP adresini kontrol et. PS4 yazılımı 8.0 ve üzeri kullanılır.")
        host=field(intent.getStringExtra(EXTRA_HOST).orEmpty(),"PS4 IP adresi",false)
        add(conn,host)
        add(root,conn,14)

        val code=card("03  •  PS4 kayıt kodu",
            "PS4 → Ayarlar → Uzaktan Oynatma Bağlantı Ayarları → Cihaz Ekle. Orada gördüğün 8 haneli kodu yaz.")
        pin=field("","8 haneli PS4 cihaz ekleme kodu",true)
        pin.filters=arrayOf(android.text.InputFilter.LengthFilter(8))
        add(code,pin,11)
        message=t.text(this,"Kod 6 haneli TV PIN'inden farklıdır.",13,false,t.muted)
        add(code,message,14)
        add(code,t.button(this,"✓  PS4 ile eşleştir",true).apply {
            setOnClickListener { beginPairing() }
        })
        add(root,code,14)

        add(root,t.button(this,"←  Konsollara dön",false).apply {
            setOnClickListener{finish()}
        })
        setContentView(scroll)
        updatePsnState()
    }
    private fun updatePsnState(){
        if(!::account.isInitialized)return
        val saved=getSharedPreferences("glossa_psn",MODE_PRIVATE)
            .getString("account_id","").orEmpty()
        account.text=if(saved.isBlank())
            "○  PSN Account ID kayıtlı değil."
            else "●  PSN Account ID hazır • eşleştirmeye geçebilirsin."
        account.setTextColor(if(saved.isBlank())t.muted else t.accent)
    }
    override fun onResume() {super.onResume();updatePsnState()}
    private fun beginPairing() {
        val address=host.text.toString().trim()
        val code=pin.text.toString().trim()
        val saved=getSharedPreferences("glossa_psn",MODE_PRIVATE)
            .getString("account_id","").orEmpty()
        if(!GlossaTvClient.validIp(address)) {
            host.error="Geçerli PS4 IP adresi gerekiyor";return
        }
        val bytes=try { Base64.decode(saved,Base64.DEFAULT) }
            catch(_:IllegalArgumentException){null}
        if(bytes==null || bytes.size!=RegistInfo.ACCOUNT_ID_SIZE) {
            message.text="Önce PSN hesabını ekleyip Account ID al."
            message.setTextColor(0xFFFFB3A2.toInt())
            return
        }
        if(!code.matches(Regex("[0-9]{8}"))) {
            pin.error="PS4'ün gösterdiği 8 rakamı gir";return
        }
        getSharedPreferences(GlossaTheme.PREF,MODE_PRIVATE).edit()
            .putString("ps4_host",address).apply()
        // Chiaki handles PS4 v8+ registration and protected key exchange.
        val info=RegistInfo(Target.PS4_10,address,false,null,bytes,code.toInt())
        val next=Intent(this,GlossaPairingProgressActivity::class.java).apply {
            putExtra(RegistExecuteActivity.EXTRA_REGIST_INFO,info)
            if(intent.hasExtra(EXTRA_MANUAL_ID))
                putExtra(RegistExecuteActivity.EXTRA_ASSIGN_MANUAL_HOST_ID,
                    intent.getLongExtra(EXTRA_MANUAL_ID,0))
        }
        register.launch(next)
    }
}
