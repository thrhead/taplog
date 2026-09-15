# Kararlar ve Araştırma

**Tarih:** 2026-09-15. Kullanıcının konuşmada seçtiği tercihler ve topluca onayladığı taslak. Ürün kaynağı [PRD §44.1](../../docs/product/TapLog_V1_PRD.md), teknik sonuç [plan](plan.md).

## Decision register

| ID | Karar | Gerekçe | Alternatifler |
|---|---|---|---|
| D01 | Archive korur; hard-delete geçmişi siler | §31/anayasa uyumu | Target geçmişini koru; tüm geçmişi koru |
| D02 | Kayıt+Hedef tek OPEN | Bağımsız nesne takibi | Kayıt tek; çok OPEN |
| D03 | Duration hızlı toggle | Tek dokunuş | Ayrı buton; yapılandırma |
| D04 | State grup+Hedef | Nesne bağımsızlığı | Global; seçilebilir kapsam |
| D05 | Tekrar State yeni Event | Bilinçli tekrar | No-op; kapatma |
| D06 | Dijital buton ORPHANED/manual rebind | Tutarlı lifecycle | Otomatik geri bağla; temizle |
| D07 | Unlink bağlamı kapatır | Geçersiz ilişkide kayıt yok | Önce süre çöz; butonları koru |
| D08 | Üç modül | Sınır + sadelik | Tek; çok modül |
| D09 | İlişkisel model + snapshot | Sade geçmiş koruma | Tam audit; event sourcing |
| D10 | Tarihsel ad/ikon | Eski bağlam korunur | Güncel ad; her seferinde sor |
| D11 | Davranış ilk Event sonrası sabit | Karma tarih yok | İleri değişim; geçmiş dönüşümü |
| D12 | State geçmişten | Timeline tutarlılığı | Ayrı durum; her edit'te sor |
| D13 | State archive temizler | Önceki durumu uydurmaz | Öncekiye dön; arşivliyi tut |
| D14 | Eski Undo çakışırsa reddet | Sonraki değişiklik korunur | Tam sil; ek onay |
| D15 | Türkçe parser | Dar doğrulanabilir kapsam | İki dil; İngilizce |
| D16 | Açık sınırlı zaman dili | Hız/doğruluk | Manuel; geniş dil |
| D17 | Geçmiş Duration çakışması düzeltme | Açık süre korunur | Başlangıcı değiştir; mevcut süreyi kapat |
| D18 | Fuzzy yalnız öneri | Yanlış kayıt azaltılır | Eşikle otomatik; her eşleşmeyi sor |
| D19 | Tek Tile | En dar V1 | Çok yuva; picker |
| D20 | Widget tek aksiyon | Sade kurulum | Çok aksiyon; iki tür |
| D21 | Import dijital rebind | Eski etkileşim yeni veriye uygulanmaz | Tercih taşı; otomatik koru |
| D22 | NFC sürüm+kimlik | Kişisel veri yok | Aksiyon; tam tanım |
| D23 | Room tek DB/ayarlar dahil | Atomik import/lifecycle | SQLite; ayrı DataStore |
| D24 | Kısa doğrudan transaction | Az bileşen | Bellek kuyruğu; kalıcı kuyruk |
| D25 | Mantıksal yedek | Şemadan bağımsız doğrulama | DB kopyası; ikisi |
| D26 | Argon2id + AES-GCM | Parola denemelerine bellek maliyeti | PBKDF2 |
| D27 | API 26 | Cihaz erişimi | API 29; 33 |
| D28 | Özel MIME NDEF | Web altyapısı yok | External Type; HTTPS |
| D29 | Bildirim yoksa in-app Undo | Temel kayıt devam | Engelle; yalnız Timeline |
| D30 | Compose+Glance | Kotlin UI | RemoteViews; XML |
| D31 | İstatistik sorgu anında | Düzeltme tutarlılığı | Özet; cache |
| D32 | Google Play | Tek dağıtım | APK; çok mağaza |
| D33 | İstemci Billing | Backend işletimi yok | Sunucu; yönetilen hizmet |
| D34 | Limitte kullanıcı beşini seçer | Veri korunur | Mevcutları bırak; otomatik seç |
| D35 | Duration timestamp | Sürekli iş gerekmez | Servis; periyodik iş |
| D36 | AI belirsizlikte | Hata/gecikme sınırı | Her girdide; manuel çağrı |
| D37 | Codespaces+yerel cihaz | Mevcut ortamda KVM yok | Bulut test; tamamen yerel |
| D38 | Pozitif decimal | Miktar takibi | Integer; negatif |
| D39 | Tek sabit opsiyonel birim | Net toplam | Dönüşüm; Event birimi |
| D40 | Kayıt varsayılan miktarı | Tekrar kurulum yok | Buton miktarı; hep +1 |
| D41 | Elle constructor injection | Açık küçük grafik | DI framework |

## Birincil kaynaklar

2026-09-15 oturumunda kontrol edildi:

- [Android data layer](https://developer.android.com/topic/architecture/data-layer): Room ilişkili/sorgulanabilir veri için; tek DB tercihi TapLog'un atomiklik ihtiyacından çıkarımdır.
- [NFC basics](https://developer.android.com/develop/connectivity/nfc/nfc): MIME dispatch, Android 16 web ACTION_VIEW, Android 17 izin/stopped-app/web açma koşulları.
- [Quick Settings](https://developer.android.com/develop/ui/views/quicksettings-tiles): TileService, API 33 ekleme ve sürüme uygun Activity açma.
- [Glance interaction](https://developer.android.com/develop/ui/compose/glance/user-interaction): ActionCallback async receiver; lambda WorkManager; Activity için actionStartActivity.
- [UI layer](https://developer.android.com/topic/architecture/ui-layer): Compose, ViewModel, tek yönlü veri akışı.
- [ML Kit](https://developers.google.com/ml-kit/genai), [Prompt](https://developers.google.com/ml-kit/genai/prompt/android/get-started): Koşullu cihaz/model, ön planda çıkarım, API 26 minimum; incelenen SDK beta. Türkçe kalite garantisi çıkarılmaz.
- [Android crypto](https://developer.android.com/privacy-and-security/cryptography): AES-GCM.
- [RFC 9106](https://www.rfc-editor.org/rfc/rfc9106.html): Argon2id 64 MiB/3 geçiş/4 paralellik bellek kısıtlı profil; cihaz testi gerekir.
- [Bouncy Castle Argon2 API](https://downloads.bouncycastle.org/java/docs/bcjce-jdk13-javadoc/org/bouncycastle/crypto/generators/Argon2BytesGenerator.html): Hazır algoritma; belge sürümü dependency seçimi değildir.
- [Billing](https://developer.android.com/google/play/billing/integrate), [güvenlik](https://developer.android.com/google/play/billing/security): Pending/purchased, acknowledgement; sunucu önerilir, istemci eşdeğer güvenlik sağlamaz.
- [Auto Backup](https://developer.android.com/identity/data/autobackup): Açık cloud/device transfer dışlamaları.
- [JDK/build](https://developer.android.com/build/jdks): Toolchain/AGP birlikte kilitlenir.

## Doğrulama sınırları

Event sürümü, veri kümesi sürümü ve State temizleme sınırı onaylanan bütünleşik taslağın teknik tamamlamalarıdır. NFC/OEM, Widget Undo, Argon2 maliyeti, Türkçe AI kalitesi ve import kesintileri [quickstart](quickstart.md) kapılarıdır. Dependency patch sürümleri ilk build özelliğinde seçilir; gelişmiş Pro rapor ürün listesi kendi specification'ına aittir. Bu belge ölçüm yapıldığını iddia etmez.
