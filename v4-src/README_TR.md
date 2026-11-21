# GlossaWhaleOS 4.2 – Gerçek birleşik telefon uygulaması (beta)

## İçerik

- Tek telefon APK'sı: Glossa ana ekranı, Chiaki Remote Play protokolü,
  PS4/PSN eşleştirmesi, oyun görüntüsü, OCR, Türkçeye çeviri ve TV'ye gönderim.
- PS4 keşif, kayıt ve bağlanma ekranlarının **tamamı Glossa tasarımındadır**.
  Chiaki'nin açık kaynak keşif, şifreli bağlantı ve görüntü motoru yalnızca
  arka planda çalışır; ayrı Chiaki APK'sı gerekmez.
- Glossa içerisinde Sony üzerinden PSN Account ID alma/kaydetme,
  otomatik PS4 keşfi, elle IP kaydetme, 8 haneli PS4 kayıt koduyla
  eşleştirme, sonucu izleme, PS4 uyandırma ve oyunu açma yer alır.
- Görüntü ve kontrol menüsünde doğrudan Chiaki'nin asıl tercihlerine
  yazılan çözünürlük, FPS, H264/H265, bit hızı, ekrandaki tuşlar,
  titreşim ve hareket algılama ayarları bulunur.
- Ana ekran ve çeviri ayarları için beş kayıtlı renk teması + özel RGB vurgu rengi.
- Oyun içinde doğrudan açılan Glossa paneli: OCR seçme kutusunu düzenleme,
  TV altyazısı konumu, boyut/renk/arka plan, oyun profili ve OCR hızı.
- İnternet bağımlılığı olmayan temel İngilizce→Türkçe çeviri için ML Kit.
  İlk dil modeli indirimi sırasında telefonun internet bağlantısı gerekir.
- RDR 2 profilinde çok sınırlı özel isim koruma ve daha temiz OCR metni.
  Bu bir yapay zekâ/LLM bağlam modeli **değildir**; çevirinin kusursuz
  veya sıfır gecikmeli olduğu iddia edilmez.

## Deneme

1. **Eski çalışan APK'ları kaldırmayın.** Bu sürüm ayrı paket kimliğiyle
   kurulur: `com.darkscorpion.glossawhale.unifiedv42`.
2. Glossa ana ekranı → PSN Hesabım → Sony giriş sayfası / Account ID al →
   Glossa konsol listesi → PS4'ü seç → 8 haneli PS4 cihaz ekleme kodunu
   gir → eşleştir → oyna. Ayrı bir Chiaki ekranı açılmaz.
3. TV'de 3.1/3.2 altyazı alıcısını açın. Eski alıcıyı durdurun: iki TV
   alıcısı TCP/8799 portunda eşzamanlı çalışamaz.
4. Glossa ana ekranı → OCR ve altyazı ayarları → TV IP ve 6 haneli PIN.
   Önce TV'ye test altyazısı gönderin.
5. Tema sayfasında beş renk paletini veya özel RGB değerlerini kaydedin.
6. PS4 görüntüsünü açıp oyun içindeki `OCR alanı` ile alanı hizalayın;
   `Glossa ayarları` panelinden görünümü ve kalite modunu değiştirin.
7. RDR 2 İngilizce altyazılı sahnede en az 10 farklı cümleye bakın.
   Menü yazılarının yanlış okunmasını, çeviri tekrarı, TV süresi ve hızını not edin.

## Sınırlar

- PS4/Chiaki eşleştirme verileri Android uygulama özel alanındadır ve
  farklı paket kimliğine otomatik taşınmaz.
- CI derlemesi gerçek cihazda video OCR veya TV overlay uyumluluğu
  konusunda doğrulama yerine geçmez.
- "Hızlı", "Dengeli" ve "Titiz" seçenekleri ek kare doğrulama yapar;
  aynı çevrimdışı temel çeviri modelini kullanır.
- Gelişmiş, bağlam farkındalıklı bulut AI çevirisi şu sürümde bulunmaz:
  güvenilir servis, maliyet, kullanıcı rızası ve gizlilik kararı gerektirir.
- Chiaki-ng AGPL-3.0-only with OpenSSL linking exception lisansıyla
  dağıtılır. Çekirdek kaynak ve derleme betiği repoda mevcuttur.

## Paket güncelleme uyarısı

Glossa 4.2 beta farklı paket kimliği ile kurulmuştur. Android debug imzalı
önceki test APK'larından hatalı güncelleme almamak için ayrı kurulabilir.
Bu nedenle eski PSN eşleştirme kayıtları yeni beta pakete taşınmaz.
Çalıştığını doğrulamadan önceki çalışan sürümü kaldırmayın.

## Güvenlik

Sony şifresini asla Glossa'ya veya sohbetlere yazmayın. OAuth yönlendirme
bağlantıları kısa süreli gizli yetkilendirme kodları içerir; paylaşmayın.
PS4 için ikinci hesap kullanımı Sony ve oyunun oturum kısıtlarına bağlıdır.
