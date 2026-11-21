// SPDX-License-Identifier: AGPL-3.0-only
package com.metallic.chiaki.glossa

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.metallic.chiaki.common.*
import com.metallic.chiaki.common.ext.viewModelFactory
import com.metallic.chiaki.lib.ConnectInfo
import com.metallic.chiaki.lib.DiscoveryHost
import com.metallic.chiaki.main.MainViewModel
import com.metallic.chiaki.stream.StreamActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One Glossa-branded screen for automatic Chiaki PS4 discovery,
 * registration/pairing, wake-up and stream launch.
 * Chiaki discovery + encrypted Remote Play are reused without replacement.
 */
class GlossaConsolesActivity : AppCompatActivity() {
    private lateinit var vm: MainViewModel
    private lateinit var t: GlossaTheme
    private lateinit var consoles: LinearLayout
    private lateinit var hint: TextView
    private lateinit var discover: TextView
    private lateinit var input: EditText

    private fun dp(x: Int) = GlossaTheme.dp(this,x)
    private fun gap(container: LinearLayout, height:Int) {
        container.addView(View(this),LinearLayout.LayoutParams(1,dp(height)))
    }
    private fun add(container: LinearLayout, view:View, bottom:Int=0) {
        val lp=LinearLayout.LayoutParams(-1,-2)
        lp.bottomMargin=dp(bottom)
        container.addView(view,lp)
    }
    private fun card(title:String, subtitle:String):LinearLayout {
        val v=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(19),dp(19),dp(19),dp(19))
            background=GlossaTheme.shape(this@GlossaConsolesActivity,t.panel,18)
        }
        add(v,t.text(this,title,19,true,t.white),7)
        add(v,t.text(this,subtitle,13,false,t.muted),10)
        return v
    }
    private fun action(title:String,primary:Boolean,onClick:()->Unit):TextView =
        t.button(this,title,primary).also {
            it.setOnClickListener { onClick() }
        }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        t=GlossaTheme.load(this)
        window.statusBarColor=t.bg
        window.navigationBarColor=t.bg
        val scroll=ScrollView(this).apply {
            isFillViewport=true
            setBackgroundColor(t.bg)
        }
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(22),dp(26),dp(22),dp(30))
        }
        scroll.addView(root)
        add(root,t.text(this,"GLOSSA LAB  /  REMOTE PLAY",12,true,t.accent),10)
        add(root,t.text(this,"PS4 konsollarım",29,true,t.white),9)
        add(root,t.text(this,"PS4'ü bul, eşleştir ve oyunu aynı ekrandan başlat.",
            14,false,t.muted),20)

        val account=card("PlayStation hesabı",
            "Remote Play için ikinci PSN hesabının Account ID'sini kaydet.")
        val accountText=getSharedPreferences("glossa_psn",MODE_PRIVATE)
            .getString("account_id","").orEmpty()
        add(account,t.text(this,if(accountText.isBlank())
            "○  Account ID henüz eklenmedi"
            else "●  Account ID kayıtlı",14,true,
            if(accountText.isBlank()) t.muted else t.accent),12)
        add(account,action("PSN hesabım / Account ID al",false) {
            startActivity(Intent(this,PsnAccountActivity::class.java))
        })
        add(root,account,15)

        val found=card("Ağdaki PlayStation konsolları",
            "Telefon ve PS4 aynı Wi-Fi/ağ üzerinde olmalı.")
        discover=action("⟳  Konsolları yeniden tara",true){
            vm.discoveryManager.active=true
            vm.discoveryManager.resume()
            renderHosts(vm.displayHosts.value.orEmpty())
        }
        add(found,discover,10)
        hint=t.text(this,"●  PS4 aranıyor…",13,false,t.accent)
        add(found,hint,10)
        consoles=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        add(found,consoles)
        add(root,found,15)

        val manual=card("Konsol görünmüyor mu?",
            "PS4 IP adresini gir. Eklenen cihaz da bu listeden eşleştirilebilir.")
        input=EditText(this).apply {
            hint="Örneğin 192.168.1.103"
            setTextColor(t.white)
            setHintTextColor(t.muted)
            textSize=16f
            isSingleLine=true
            inputType=android.text.InputType.TYPE_CLASS_PHONE
            setPadding(dp(12),dp(13),dp(12),dp(13))
            background=GlossaTheme.shape(this@GlossaConsolesActivity,0xFF203652,12)
        }
        add(manual,input,11)
        add(manual,action("＋  PS4 IP adresini listeye ekle",false) {
            addManualHost()
        })
        add(root,manual,15)
        add(root,action("←  Glossa ana ekranına dön",false){finish()})
        setContentView(scroll)

        vm=ViewModelProvider(this,viewModelFactory {
            MainViewModel(getDatabase(this),Preferences(this))
        }).get(MainViewModel::class.java)
        vm.displayHosts.observe(this) { renderHosts(it) }
        vm.discoveryActive.observe(this) { active ->
            hint.text=if(active) "●  Konsollar aranıyor…"
                else "○  Keşif kapalı. Yeniden tara düğmesini kullan."
            hint.setTextColor(if(active)t.accent else t.muted)
        }
        if(vm.discoveryActive.value!=true)vm.discoveryManager.active=true
    }

    private fun renderHosts(hosts:List<DisplayHost>) {
        consoles.removeAllViews()
        if(hosts.isEmpty()) {
            add(consoles,t.text(this,"Henüz PS4 bulunamadı. Konsolu açıp aynı ağda olduğundan emin ol.",
                14,false,t.muted))
            return
        }
        for(host in hosts) {
            val registered=host.registeredHost!=null
            val standby=host is DiscoveredDisplayHost &&
                host.discoveredHost.state==DiscoveryHost.State.STANDBY
            val status=when {
                registered && standby -> "Eşleştirilmiş • Dinlenme modunda"
                registered -> "Eşleştirilmiş • Oynamaya hazır"
                else -> "Yeni konsol • Hesapla eşleştirme gerekli"
            }
            val card=card("🎮  "+host.name,host.host+"   ·   "+status)
            if(registered) {
                add(card,action("▶  Bu PS4'te oyna",true) { play(host) },7)
                if(standby) add(card,action("⏻  Konsolu uyandır",false) { wake(host) })
            } else {
                add(card,action("＋  PS4'ü hesabımla eşleştir",true) { pair(host) })
            }
            add(consoles,card,12)
        }
    }

    private fun addManualHost() {
        val host=input.text.toString().trim()
        if(!GlossaTvClient.validIp(host)){
            input.error="Geçerli bir IP adresi gir."
            return
        }
        lifecycleScope.launch {
            val exists=vm.displayHosts.value.orEmpty().any{ it.host==host }
            if(!exists) {
                withContext(Dispatchers.IO) {
                    getDatabase(this@GlossaConsolesActivity).manualHostDao()
                        .insert(ManualHost(host=host,registeredHost=null))
                }
            }
            input.text.clear()
            vm.discoveryManager.active=true
            hint.text="✓  PS4 eklendi. Listede eşleştir düğmesini seç."
        }
    }
    private fun pair(host:DisplayHost) {
        val i=Intent(this,GlossaPairingActivity::class.java)
        i.putExtra(GlossaPairingActivity.EXTRA_HOST,host.host)
        if(host is ManualDisplayHost)
            i.putExtra(GlossaPairingActivity.EXTRA_MANUAL_ID,host.manualHost.id)
        startActivity(i)
    }
    private fun play(host:DisplayHost) {
        val reg=host.registeredHost ?: return pair(host)
        val info=ConnectInfo(host.isPS5,host.host,reg.rpRegistKey,reg.rpKey,
            Preferences(this).videoProfile)
        startActivity(Intent(this,StreamActivity::class.java).apply {
            putExtra(StreamActivity.EXTRA_CONNECT_INFO,info)
        })
    }
    private fun wake(host:DisplayHost) {
        val reg=host.registeredHost ?: return
        vm.discoveryManager.sendWakeup(host.host,reg.rpRegistKey,reg.target.isPS5)
        hint.text="⏻  Uyandırma sinyali gönderildi. Konsol hazır olunca başlat."
    }
    override fun onStart(){super.onStart();if(::vm.isInitialized)vm.discoveryManager.resume()}
    override fun onStop(){if(::vm.isInitialized)vm.discoveryManager.pause();super.onStop()}
}
