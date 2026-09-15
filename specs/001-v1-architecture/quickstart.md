# Mimari doğrulama quickstart'ı

Bu belge gelecekteki uygulama için doğrulama rehberidir. Şu anda Android projesi veya çalıştırılabilir test yoktur.

## Ortamlar

- Codespaces: Gradle Wrapper ile compile, saf Kotlin unit test, parser örnekleri, crypto test vektörleri ve statik kontroller.
- Yerel Android Studio: Room/migration/instrumentation, Compose/Glance ve UI testleri.
- NFC'li gerçek cihaz: API 26 civarı bir cihaz ve güncel Android cihaz; etiket yazma/okuma, kilit ekranı, force-stop, bildirim, OEM ve süreç kapanması.
- AI destekli cihaz: model yok, indiriliyor, hazır ve Türkçe hata/fallback senaryoları.

Codespaces'te mevcut incelemede Java bulundu; `adb`, `sdkmanager`, `emulator` PATH'te yoktu ve `/dev/kvm` mevcut değildi. Android emülatörü Codespaces'in zorunlu önkoşulu değildir.

## Uygulama sonrası komut sırası

1. `./gradlew test` — core parser/domain/engine.
2. `./gradlew :data:test` ve Room migration/instrumentation — transaction, foreign key, partial unique OPEN indeksleri.
3. `./gradlew lint detekt` (seçilen statik araçlar) — thread, dependency ve Android sınırları.
4. `./gradlew connectedCheck` — yerel emulator/cihaz varsa UI ve integration.
5. Yerel cihaz matrisi — NFC/Widget/Tile/notification/backup.

## Kabul senaryoları

- İki bilinçli Counter dokunuşu iki Event; NFC donanım tekrarında davranışa duyarlı suppression.
- Aynı Record+Target için ikinci OPEN reddedilir; farklı Target ve hedefsiz scope eşzamanlı açılır.
- Archive/unlink aynı transaction'da INCOMPLETE + ORPHANED + State reset üretir.
- Eski Undo revision conflict verir; import generation eski callback'i reddeder.
- “kahve içtim”, “dün”, “salon çiçeği”, mevcut OPEN ile geçmiş başlangıç parser sonuçları.
- Yanlış parola, bozuk tag, geçersiz ilişki ve disk kesintisi mevcut DB'yi değiştirmez.
- Argon2 benchmark düşük cihazda bellek/süre eşiğini ve UI dışında çalışmayı doğrular.
- Pro pending/temporary failure/refund-limit akışı; entitlement yedekten gelmez.

Her senaryoda önce domain testleri başarısızlığı görülür, sonra implementasyon ve refactor yapılır. Bu mimari belgenin tamamlanması testlerin geçtiği anlamına gelmez.
