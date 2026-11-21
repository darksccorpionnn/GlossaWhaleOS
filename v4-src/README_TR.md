# GlossaWhaleOS 4.0 – Birleşik telefon uygulaması (beta)

## İçerik

- Tek telefon APK'sı: Glossa ana ekranı, Chiaki Remote Play protokolü,
  PS4/PSN eşleştirmesi, oyun görüntüsü, OCR, Türkçeye çeviri ve TV'ye gönderim.
- PS4'ü seçme/bağlanma ekranında Chiaki'nin açık kaynak, test edilmiş
  konsol listesi ve bağlantı motoru kullanılır; ayrı Chiaki APK'sı gerekmez.
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
   kurulur: `com.darkscorpion.glossawhale.unifiedv4`.
2. Glossa ana ekranı → PS4'e bağlan. Yeni paket altında eşleştirme
   gerekiyor olabilir. PS4'te ikinci hesap ve 8 haneli kod kullanılır.
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
