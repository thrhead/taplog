# Giriş kanalları sözleşmesi

## Ortak akış

Kanal kendi duplicate/interaction kontrolünü yapar → Engine komutu üretir → commit sonrası kanal geri bildirim verir. Kanal, Engine'in kabul ettiği gerçek istekleri debounce ederek silemez.

## NFC

İlk NDEF kaydı TapLog özel MIME türüdür. Payload: format sürümü + rastgele 128-bit button ID; isim, Record, Target veya komut taşımaz. Geçersiz sürüm/uzunluk Unknown/invalid akışıdır. NFC Action bağlantısı Record + opsiyonel Target, Target bağlantısı yalnız Target taşır.

Kanal donanım tekrarını davranışa göre bastırabilir: Moment/Duration/State daha sıkı, Counter bilinçli art ardayı koruyacak kadar toleranslıdır. ORPHANED son snapshot ile rebind seçenekleri gösterir; Unknown yeni pairing başlatır. Aktif Target fakat aktif Record yoksa ORPHANED değil, “Kayıt oluştur” akışıdır.

Etiket yazımı doğrulanmadan binding ACTIVE yapılmaz. Bildirim izni/heads-up başarısız olsa da Event korunur; giriş ekranı sonuç ve uygulanabilir Undo gösterir. Kilit ekranı/OEM davranışı garanti edilmez.

## Widget ve Quick Settings

Her Glance Widget tek Record + opsiyonel Target aksiyonudur; instance kimliği `DigitalBinding` ile eşlenir. Quick Settings tek Tile ve tek yapılandırılabilir aksiyondur. Her dokunuşta güncel binding/aktiflik transaction içinde denetlenir.

Archive/unlink dijital bağlantıyı ORPHANED yapar; unarchive otomatik rebind yapmaz. Import Widget/Tile kurulumlarını taşımaz; eski platform callback'leri dataset generation ile reddedilir. Widget callback'i kısa işlem içindir; Activity açılacaksa doğrudan açık Activity action kullanılır.

## Notification/background

Kayıt commit'i bildirimden önce gelir. Bildirim kanalının kapalı olması başarıyı değiştirmez. Duration için foreground service, alarm veya periyodik update yoktur. Tile/Widget güncellemesi commit sonrasında best-effort yapılır; veri kaynağı her zaman Room'dur.
