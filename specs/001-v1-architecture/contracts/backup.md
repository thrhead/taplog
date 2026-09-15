# `.tpb` yedek sözleşmesi

## Paket

Mantıksal ve sürümlü paket; Record, Target, ilişkiler, Event snapshot/timestamps, NFC binding ve kullanıcı ayarlarını taşır. Widget/Tile platform instance'ları, Pro entitlement, UndoReceipt ve dataset generation taşınmaz.

Başlıkta format sürümü, kripto profil kimliği, salt ve nonce bulunur. İçerik UTF-8 mantıksal veri paketidir; AES-256-GCM ile şifrelenir ve başlık authenticated data olarak doğrulanır. Paroladan Argon2id ile anahtar türetilir; ilk profil 64 MiB/3 geçiş/4 paralellik, 16-byte salt, 32-byte key, 12-byte nonce ve 16-byte tag'dir. Parametreler cihaz benchmark'ı sonrası sabitlenir; dosyadan keyfî parametre kabul edilmez.

## Export

Room tutarlı snapshot → mantıksal serialization → Argon2id/AES-GCM → tamamlanmış geçici dosya → kullanıcı seçtiği URI. Yazma/şifreleme tamamlanmadan başarı verilmez. Parola saklanmaz veya geri getirilemez.

## Import

1. Dosya boyutu/format/kripto profili sınırlarını denetle.
2. Parola ile doğrula; GCM başarısızsa veri değişmesin.
3. Tüm kimlik, ilişki, behavior, zaman, Duration/State invariant'larını bellekte doğrula.
4. Kullanıcıya overwrite kapsamını gösterip onay al.
5. Tek Room transaction'ında mevcut veri silinip yedek verisi yazılır; dijital bağlantılar rebind gerektirecek şekilde sıfırlanır; dataset generation artırılır.
6. Commit sonrası kanallar yenilenir.

Herhangi bir adım commit öncesi başarısızsa mevcut DB değişmeden kalır. Import cihaz entitlement'ını değiştirmez ve Pro kazandırmaz. NFC mapping taşınır; fiziksel etiket yeniden yazılmaz.
