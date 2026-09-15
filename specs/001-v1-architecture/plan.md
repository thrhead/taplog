# Technical Plan: TapLog V1 Mimari Temeli

**Branch**: `main` | **Feature directory**: `001-v1-architecture` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

## Summary

Kotlin, API 26+, üç modül, tek Room/SQLite, kısa atomik işlemler, kanal bağımsız Event Engine, Compose/Glance ve elle constructor injection. Bu çalışma mimari temeldir; bütün V1 için tek uygulama görevi değildir. Kod, Android iskeleti ve `tasks.md` yoktur. Spec Kit yol çözücüsünün BRANCH çıktısı feature adıdır; gerçek git branch'i `main`dir.

## Technical Context

- **Language/Version**: Kotlin/JVM. Kotlin, AGP, Gradle, KSP ve JDK ilk build özelliğinde uyumlu sabit set olarak kilitlenir; dinamik sürüm yok.
- **Primary Dependencies**: Coroutines/Flow, Room, Compose/Material, Lifecycle/ViewModel, Navigation, Glance, Play Billing, kotlinx.serialization; Bouncy Castle Argon2 ve isteğe bağlı ML Kit Prompt.
- **Storage**: Uygulama özel alanında tek Room/SQLite; yedeklenen ayarlar dahil. Foreign key, indeks ve transaction.
- **Testing**: Saf JVM domain/parser; gerçek Android/SQLite integration ve migration; Compose/Glance; fiziksel NFC/AI cihazları.
- **Target Platform**: Android 8.0/API 26 minimum. compile/target SDK ilk build/release özelliğinde stabil SDK ve mağaza koşullarına göre kilitlenir.
- **Project Type**: Yerel Android uygulaması; backend yok.
- **Performance Goals**: Kayıt yolu ağ/AI beklemez; ana thread'de disk/kriptografi yok; Timeline sayfalı, sorgular aralıkla sınırlı. Gecikme/büyük veri/Argon2 ölçümleri [quickstart](quickstart.md) kapılarıdır; gerçekleşmiş ölçüm iddiası değildir.
- **Constraints**: Çevrimdışı çekirdek, tarihsel anlam, kanal düzeyinde suppression, atomik overwrite, terminal INCOMPLETE.
- **Scale/Scope**: Tek kullanıcının yerel verisi; feature bazında uygulama. Gelişmiş Pro rapor içeriği kendi ürün specification'ına tabidir.

## Constitution Check

| İlke | Tasarım karşılığı | Önce / sonra |
|---|---|---|
| I — Ortak Event çekirdeği | Android bağımsız core, ortak komutlar | Uygun / Uygun |
| II — Yerel çekirdek | Ağsız kayıt, deterministik parser, AI öneri | Uygun / Uygun |
| III — Geçmiş/belirsizlik | Snapshot, terminal süre, sürümlü Undo, onaylı hard-delete | Uygun / Uygun |
| IV — Kanal güvenliği | Global debounce yok | Uygun / Uygun |
| V — Test/kapsam | Saf domain, TDD kapıları, uygulama yok | Uygun / Uygun |

Ürün netleştirmeleri kullanıcı onayıyla PRD §44.1'e işlendi. Anayasa değişmedi; tablo runtime testlerinin geçtiği anlamına gelmez.

## Project Structure

### Documentation

- [spec.md](spec.md): gereksinimler ve kabul koşulları.
- [research.md](research.md): tercihler, gerekçeler, alternatifler, kaynaklar.
- [data-model.md](data-model.md): kalıcı model ve bütünlük.
- [contracts/event-engine.md](contracts/event-engine.md): komutlar, transaction, Undo.
- [contracts/input-channels.md](contracts/input-channels.md): NFC/dijital bağlantı/bildirim.
- [contracts/parser.md](contracts/parser.md): Türkçe yorumlama ve AI.
- [contracts/backup.md](contracts/backup.md): `.tpb` ve overwrite.
- [quickstart.md](quickstart.md): gelecek build/test ortamı.
- [checklists/requirements.md](checklists/requirements.md): belge kalite kontrolü.

### Planned source boundaries — henüz oluşturulmadı

| Modül | İçerik | Bağımlılık |
|---|---|---|
| `:core` | Domain, Event Engine/application, lifecycle, parser, statistics policy, ports | Kotlin/JVM; Android sınıfı yok |
| `:data` | Room entity/DAO/mapping, transaction port, backup codec/crypto | core |
| `:app` | Compose, NFC, Glance, TileService, notifications, Billing, AI, composition root | core + data |

Tek uygulama süreci, tek DB instance. Constructor injection; core kapsayıcıyı bilmez. Activity/receiver/service nesneleri uygulama kapsamına sızdırılmaz. Başlangıçta kanal başına modül yok.

## Execution and lifecycle

Kanal girdisi → application isteği → core kuralları + data transaction → commit → kanal feedback'i. Güncel bağlam transaction içinde yeniden okunur. Bildirim, AI, NFC yazma, dosya seçici ve Billing çağrıları transaction dışında kalır.

Arşivleme, unlink, kalıcı silme, binding ve import ayrı application servisleridir; aynı transaction portu/domain kurallarını kullanır. Event Engine genel iş kuyruğu veya dev CRUD sınıfı değildir. Feedback hatası commit'i geri almaz; sonraki açılış DB'den yenilenir.

Duration kalıcı zaman damgalarına dayanır; foreground service, alarm ve periyodik sayaç yok. Glance'in kendi WorkManager kullanımı farklı bir altyapı ayrıntısıdır. Yedekleme kullanıcı başlatmalıdır; süreç ölürse export yeniden başlatılabilir, import rollback veya tam commit olur. Koşulsuz arka plan tamamlama sözü verilmez.

## UI and statistics

Compose/Material, ekran ViewModel'i, değişmez UI state, lifecycle-aware Flow. Ana Activity navigasyonu, küçük NFC Activity platform girişini taşır. Home Pinned/Recent/All, kombinasyon pinleri, Timeline/detail/correction, Record/Target yönetimi, Archive ve Settings PRD kapsamındadır. Glance bileşenleri Compose ekranlarından ayrıdır; ortak şey aksiyon/sunum verisidir.

Tek seferlik mesajlar tüketim kimliğiyle yönetilir; yeniden çizim komutu yeniden çalıştırmaz. Ekranın eski görünümü yetki vermez; servis commit anındaki veriyi esas alır.

StatisticsService core'da rapor tanımı/izin/INCOMPLETE kurallarını uygular; data SQL sayım ve filtreli veri akışlarını sağlar. Counter decimal string olarak saklanır, BigDecimal ile toplanır; SQLite REAL/SUM kullanılmaz. Süre toplam/ortalama yalnız COMPLETED'dır. Gün sınırları rapor zaman diliminden UTC'ye çevrilir; yaz saati gününün 24 saat olduğu varsayılmaz. Kalıcı cache/özet tablo yok. CSV/PDF aynı rapor modelini tüketir. Gelişmiş rapor türleri kendi spec'lerinde tanımlanır.

## Entitlement boundary

Billing adaptörü `Unknown/Free/Pro` görünümü sağlar; ağ hatası tek başına mevcut Pro'yu kaldırmaz. Pending ödeme hak vermez; PURCHASED doğrulanır, acknowledgement yapılır ve başarısızsa tekrar denenir. Foreground dönüşü ve satın alma/restore sırasında Play sorgulanır. Sunucusuz doğrulamanın güvenlik/iade gecikmesi sınırlaması kabul edilmiştir.

NFC bağlama/yeniden aktifleştirme ve Pro rapor/export application policy'de kontrol edilir; sadece UI gizleme yeterli değildir. Sayım ve aktifleştirme aynı transaction. Event domain anlamı hak durumuna göre değişmez. Yedekte hak yok; import cihazın hak kaydını korur. Limit aşımı kanal sözleşmesindedir.

## Dependency and security strategy

Resmî Android/Kotlin kütüphaneleri önce; somut ihtiyaç olmadan bağımlılık yok. Version catalog, Wrapper/checksum, dependency/lisans incelemesi; dinamik `+` sürüm yok. AI beta adaptörde izole ve cihaz testine tabi. Bouncy Castle lightweight Argon2 doğrudan kullanılır; sistem JCA provider'ı değiştirilmez. AES-GCM platform Cipher üzerinden.

Bu tasarım ayrıca SQLCipher veya uygulama kilidi eklemez; parola şifrelemesi taşınabilir yedeğe uygulanır. Kişisel veri/ham dil/parola loglanmaz. DB, kişisel dosyalar ve hak bilgileri cloud backup/device transfer'dan açıkça dışlanır; yalnız allowBackup bayrağına dayanılmaz. PendingIntent explicit, gereken yerde immutable; dış girdiler doğrulanır. NFC olmayan cihaz temel uygulamadan dışlanmaz.

## §44 coverage

| Başlık | Kanonik bölüm |
|---|---|
| Android/module/UI/dependency/build/test | plan + quickstart |
| Domain/database/Duration/State | data-model + event-engine |
| Event Engine | event-engine |
| NFC/NDEF/orphan/Widget/Tile/bildirim | input-channels |
| Parser/fuzzy/AI | parser |
| Background | Execution and lifecycle + input-channels |
| Encryption/backup/import | backup |
| Free/Pro/statistics | Bu planın ilgili bölümleri |

## Complexity Tracking

Anayasa istisnası yok. State temizleme sınırı ve veri kümesi sürümü onaylanan davranışı korur. Kalıcı komut kuyruğu, event sourcing, DI framework, cloud backend, vektör DB ve istatistik cache'i eklenmemiştir.
