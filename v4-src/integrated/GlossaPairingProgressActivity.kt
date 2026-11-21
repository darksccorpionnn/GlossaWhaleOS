// SPDX-License-Identifier: AGPL-3.0-only
package com.metallic.chiaki.glossa

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModelProvider
import com.metallic.chiaki.common.getDatabase
import com.metallic.chiaki.common.ext.viewModelFactory
import com.metallic.chiaki.lib.RegistInfo
import com.metallic.chiaki.regist.RegistExecuteActivity
import com.metallic.chiaki.regist.RegistExecuteViewModel

/**
 * Glossa-branded registration progress page, using Chiaki's original
 * RegistExecuteViewModel and Regist native engine.
 */
class GlossaPairingProgressActivity : AppCompatActivity() {
    private lateinit var vm:RegistExecuteViewModel
    private lateinit var t:GlossaTheme
    private lateinit var state:TextView
    private lateinit var logs:TextView
    private lateinit var spinner:ProgressBar
    private lateinit var done:TextView
    private var duplicateDialog:AlertDialog?=null

    private fun dp(n:Int)=GlossaTheme.dp(this,n.toFloat())
    private fun add(p:LinearLayout,v:View,bottom:Int=0){
        val lp=LinearLayout.LayoutParams(-1,-2)
        lp.bottomMargin=dp(bottom)
        p.addView(v,lp)
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        t=GlossaTheme.load(this)
        window.statusBarColor=t.bg;window.navigationBarColor=t.bg
        val main=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(22),dp(28),dp(22),dp(27))
            setBackgroundColor(t.bg)
        }
        add(main,t.text(this,"GLOSSA LAB  /  PS4 KAYIT",12,true,t.accent),12)
        add(main,t.text(this,"PS4 ile eşleştiriliyor",27,true,t.white),9)
        add(main,t.text(this,
            "PS4 cihaz ekleme kodunu onaylıyor ve bağlantı anahtarlarını güvenle kaydediyoruz.",
            14,false,t.muted),17)
        val card=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(17),dp(16),dp(17))
            background=GlossaTheme.shape(this@GlossaPairingProgressActivity,t.panel,18)
        }
        spinner=ProgressBar(this)
        add(card,spinner,12)
        state=t.text(this,"●  PS4'e bağlanılıyor…",16,true,t.accent)
        add(card,state,13)
        logs=t.text(this,"Bağlantı kaydı bekleniyor…",12,false,t.muted)
        logs.movementMethod=ScrollingMovementMethod()
        logs.setTextIsSelectable(true)
        logs.setPadding(dp(10),dp(10),dp(10),dp(10))
        logs.background=GlossaTheme.shape(this,0xFF0C1525.toInt(),10)
        val logScroll=ScrollView(this).apply { addView(logs) }
        card.addView(logScroll,LinearLayout.LayoutParams(-1,dp(220)))
        add(main,card,18)
        done=t.button(this,"←  Konsollara dön",false)
        done.setOnClickListener {finish()}
        add(main,done)
        setContentView(main)

        val info=IntentCompat.getParcelableExtra(intent,
            RegistExecuteActivity.EXTRA_REGIST_INFO,RegistInfo::class.java)
        if(info==null) {
            state.text="Eşleştirme bilgileri eksik."
            spinner.visibility=View.GONE
            return
        }
        vm=ViewModelProvider(this,viewModelFactory {
            RegistExecuteViewModel(getDatabase(this))
        }).get(RegistExecuteViewModel::class.java)
        vm.logText.observe(this) { text ->
            logs.text=if(text.isBlank())"PS4'ten cevap bekleniyor…" else text
        }
        vm.state.observe(this) { s ->
            when(s) {
                RegistExecuteViewModel.State.RUNNING -> {
                    spinner.visibility=View.VISIBLE
                    state.text="●  PS4 ile bağlantı kuruluyor…"
                }
                RegistExecuteViewModel.State.FAILED -> {
                    spinner.visibility=View.GONE
                    state.text="○  Eşleştirme başarısız. PS4 kodunu ve IP adresini kontrol et."
                    state.setTextColor(0xFFFFB3A2.toInt())
                    setResult(Activity.RESULT_CANCELED)
                }
                RegistExecuteViewModel.State.SUCCESSFUL -> {
                    spinner.visibility=View.GONE
                    state.text="✓  PS4 başarıyla eşleştirildi!"
                    done.text="✓  Konsollara dön"
                    setResult(Activity.RESULT_OK)
                }
                RegistExecuteViewModel.State.SUCCESSFUL_DUPLICATE -> {
                    spinner.visibility=View.GONE
                    state.text="Bu PS4 zaten kayıtlı. Kaydı güncellemek ister misin?"
                    if(duplicateDialog==null) {
                        duplicateDialog=AlertDialog.Builder(this)
                            .setTitle("PS4 zaten kayıtlı")
                            .setMessage("Yeni bağlantı kaydıyla eski kaydı değiştir?")
                            .setNegativeButton("Vazgeç"){_,_->}
                            .setPositiveButton("Kaydı güncelle"){_,_->vm.saveHost()}
                            .create().also{it.show()}
                    }
                }
                RegistExecuteViewModel.State.STOPPED -> {
                    spinner.visibility=View.GONE
                    state.text="Eşleştirme durduruldu."
                }
                else -> {}
            }
        }
        val manualId=if(intent.hasExtra(RegistExecuteActivity.EXTRA_ASSIGN_MANUAL_HOST_ID))
            intent.getLongExtra(RegistExecuteActivity.EXTRA_ASSIGN_MANUAL_HOST_ID,0L)
        else null
        vm.start(info,manualId)
    }
    override fun onStop(){
        if(::vm.isInitialized)vm.stop()
        super.onStop()
    }
    override fun onDestroy(){
        duplicateDialog?.dismiss()
        super.onDestroy()
    }
}
