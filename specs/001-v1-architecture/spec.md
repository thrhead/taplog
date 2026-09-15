# Feature Specification: TapLog V1 Mimari Temeli

**Feature Branch**: `main` (belgeleme; özellik dizini branch adından bağımsızdır)
**Created**: 2026-09-15
**Status**: Kullanıcının onayladığı mimari taslağın yazılı karşılığı
**Input**: TapLog V1 teknik mimarisini tasarla; PRD kaynak olsun; kod, Android iskeleti ve uygulama görev listesi oluşturma.

## Kapsam ve kaynaklar

Bu özellik bir mimari temel ve sözleşme çalışmasıdır; bütün V1'in tek uygulama görevi değildir. Ürün kaynağı [PRD](../../docs/product/TapLog_V1_PRD.md), yönetişim kaynağı [anayasa](../../.specify/memory/constitution.md)dır. Kullanıcı, konuşmadaki kararları ve 1–15 bölümlük taslağı belgelere aktarmayı onayladı. Teknik tercihler [plan](plan.md) ve [research](research.md) içindedir.

## User Scenarios & Testing

### User Story 1 — Kanallardan tutarlı kayıt (Priority: P1)

Kullanıcı aynı Kaydı uygulamadan veya bağlı bir butondan kullandığında aynı anlamda olay kaydeder.

**Why this priority**: Ürünün temel değeri güvenilir, düşük sürtünmeli kayıttır.
**Independent Test**: Bir Moment, Counter, Duration ve State Kaydıyla kanal bağımsız kabul senaryoları yürütülür.

**Acceptance Scenarios**:

1. **Given** aktif Counter, **When** kullanıcı bilinçli olarak iki kez dokunur, **Then** iki Event oluşur.
2. **Given** açık Duration, **When** aynı bağlamdaki hızlı aksiyon kullanılır, **Then** aynı Event tamamlanır.
3. **Given** farklı Hedeflerde aynı Duration Kaydı, **When** ikisi başlatılır, **Then** her bağlamda bir açık Event bulunur.
4. **Given** aktif State, **When** aynı State yeniden bildirilir, **Then** yeni Event oluşur, mevcut durum değişmez.

### User Story 2 — Geçmişi koruyarak yönetim ve düzeltme (Priority: P1)

Kullanıcı arşivleme, unlink ve düzeltmelerin geçmişe etkisini öngörebilir.

**Why this priority**: Yanlış durum veya kayıp geçmiş ürün güvenini bozar.
**Independent Test**: Hazır geçmiş üzerinde her yaşam döngüsü işlemi ayrı doğrulanır.

**Acceptance Scenarios**:

1. **Given** açık Duration ve bağlı buton, **When** Kayıt arşivlenir veya ilgili ilişki kaldırılır, **Then** geçmiş korunur, süre terminal INCOMPLETE olur, ilgili bağlantı ORPHANED olur.
2. **Given** arşivli Kayıt, **When** yeniden etkinleştirilir, **Then** eski süre, State ve ORPHANED bağlantılar kendiliğinden açılmaz.
3. **Given** eski bir Undo ve daha sonra düzenlenmiş Event, **When** Undo istenir, **Then** sonraki değişiklik korunur, düzeltme yönlendirmesi sunulur.
4. **Given** açıkça onaylanan kalıcı Hedef silme, **When** işlem tamamlanır, **Then** yalnızca o Hedefe ait geçmiş ve bağlı veriler silinir; diğer Hedeflerin geçmişi korunur.
5. **Given** tarihsel ad/ikon, **When** Kayıt veya Hedef yeniden adlandırılır, **Then** eski Event görünümü değişmez.

### User Story 3 — Doğal dilde güvenilir giriş (Priority: P2)

Kullanıcı Türkçe eylem, miktar ve açık zaman ifadeleriyle kayıt yapabilir; belirsiz yorum sessizce uygulanmaz.

**Why this priority**: Doğal giriş hızı artırırken yanlış kayıt üretmemelidir.
**Independent Test**: Türkçe giriş örnekleri AI mevcutken ve yokken değerlendirilir.

**Acceptance Scenarios**:

1. **Given** mevcut Kahve Kaydı, **When** “kahve içtim” girilir ve eylem kesinleşir, **Then** mevcut Kayıt kullanılır.
2. **Given** yalnızca “Kahve” girdisi, **When** yorumlanır, **Then** tanımlama/olay niyeti netleştirilir.
3. **Given** yakın isimli Hedefler, **When** yaklaşık eşleşme bulunur, **Then** kullanıcı seçimi alınır.
4. **Given** “dün” gibi saatsiz zaman, **When** kayıt hazırlanır, **Then** saat sorulur.
5. **Given** mevcut açık süre, **When** çakışan geçmiş başlangıç girilir, **Then** mevcut süre değiştirilmeden düzeltme sunulur.

### User Story 4 — Yedekleme ve geri yükleme (Priority: P1)

Kullanıcı verisini kendi parolasıyla yedekleyebilir; geçersiz veya yarım import mevcut veriyi bozmaz.

**Why this priority**: Yerel verinin güvenilir taşınması gerekir.
**Independent Test**: Bilinen veri kümesiyle yedek oluşturma, başka kurulumda açma ve hata enjeksiyonu yapılır.

**Acceptance Scenarios**:

1. **Given** doğru parola ve geçerli yedek, **When** overwrite onaylanır, **Then** Kayıt/Hedef/Event/NFC/ayarlar geri gelir; dijital butonlar yeniden bağlama ister.
2. **Given** yanlış parola, bozuk içerik veya geçersiz ilişki, **When** import denenir, **Then** mevcut veri aynen kalır.
3. **Given** başka cihazın yedeği, **When** import edilir, **Then** yedek Pro hakkı kazandırmaz.

### User Story 5 — Ücretsiz çekirdek ve NFC limiti (Priority: P2)

Kullanıcı temel kaydı çevrimdışı kullanır; Pro yalnızca tanımlı ücretli yetenekleri açar.

**Why this priority**: Temel kullanım satın alma durumuna bağımlı olmamalıdır.
**Independent Test**: Free, Pro, bağlantısız ve doğrulanmış hak kaybı durumları denenir.

**Acceptance Scenarios**:

1. **Given** beş aktif Free NFC butonu, **When** altıncı bağlanır, **Then** yükseltme sunulur ve beş mevcut buton çalışır.
2. **Given** hak kaybı/import sonucu beşten fazla eşleme, **When** Free sınırı uygulanır, **Then** kullanıcı beşini seçer; diğer eşlemeler silinmez ve ORPHANED sayılmaz.
3. **Given** geçici bağlantı hatası, **When** hak yenilenemez, **Then** mevcut Pro hakkı sırf bu nedenle kaldırılmaz.

### Edge Cases

- Arşivleme ile aynı anda gelen kayıt: yalnızca bütünüyle geçerli bir sonuç; yarım değişiklik yok.
- Etiket yazıldıktan sonra uygulama kapanması: doğrulanmamış eşleme aktif sayılmaz.
- Aktif Target'ın aktif Kaydı yok: ORPHANED yerine kayıt oluşturma yönlendirmesi.
- Bildirim kapalı: NFC kaydı ve uygulama içi sonuç/Undo kullanılabilir.
- Import öncesinden kalan Undo veya buton etkileşimi: yeni veri kümesine uygulanmaz.
- Saat değişimi nedeniyle bitiş başlangıçtan erken: sahte süre üretmeden düzeltme.
- State arşivleme: önceki State kendiliğinden aktifleşmez.

## Requirements

### Functional Requirements

- **FR-001**: Bütün kanallar aynı dört davranışın anlamını korumalı; bilinçli tekrarlar genel debounce ile birleştirilmemeli (PRD §7,20,23).
- **FR-002**: Duration tek açık olay kuralı Kayıt+Hedef kapsamında uygulanmalı; hedefsiz kullanım ayrı kapsam olmalı (§7,32,44.1).
- **FR-003**: State tek mevcut durum kuralı grup+Hedef kapsamında uygulanmalı; düzeltme geçmişten hesaplanmalı, yaşam döngüsü temizlemesi eski durumu diriltmemeli (§7,44.1).
- **FR-004**: Arşivleme/unlink geçmişi korumalı; açık süreleri INCOMPLETE, ilgili butonları ORPHANED yapmalı; unarchive bunları otomatik açmamalı (§6,18,28–32,44.1).
- **FR-005**: Açıkça onaylanan kalıcı silme, bildirilen kapsamdaki geçmişi kaldırmalı (§31,43).
- **FR-006**: Tarihsel ad/ikon ve olay anlamı korunmalı; ilk Event sonrası davranış/birim sabit olmalı (§44.1).
- **FR-007**: Counter pozitif kesin ondalık miktar, isteğe bağlı tek birim ve değiştirilebilir Kayıt varsayılanı kullanmalı; dönüşüm yapmamalı (§7,44.1).
- **FR-008**: Türkçe deterministik giriş, açık zaman ifadeleri ve kesin mevcut ad eşleşmesini desteklemeli; yaklaşık eşleşme/niyet belirsizliği onaylanmalı (§9,24–27,44.1).
- **FR-009**: AI yalnızca çözülemeyen girdiye öneri sağlamalı; yokluğu kaydı engellememeli ve onayı atlayamamalı (§25,44.1).
- **FR-010**: Tek aksiyonlu çoklu Widget örnekleri ve tek yapılandırılabilir Tile desteklenmeli (§21–22,44.1).
- **FR-011**: NFC Action/Target, Unknown/ORPHANED ayrımı, son bağlantı görünümü ve dürüst platform geri bildirimi korunmalı (§14–20).
- **FR-012**: Sonradan değişmiş Event'e eski Undo uygulanmamalı (§13,44.1).
- **FR-013**: Parolalı taşınabilir yedek gerekli tüm kişisel veriyi içermeli; overwrite bütünüyle gerçekleşmeli veya mevcut veri korunmalı (§35–36).
- **FR-014**: Dijital kurulum ve Pro hakkı yedekle taşınmamalı; import eski etkileşimleri geçersiz kılmalı (§44.1).
- **FR-015**: Tek seferlik Pro ve Free NFC limitinin aşılması kullanıcı verisini silmemeli; bağlantı hatası hak kaybı sayılmamalı (§17,38–39,44.1).
- **FR-016**: İstatistikler §33 dahil/hariç kurallarına uymalı; düzeltmeden sonra güncel geçmişi yansıtmalı.
- **FR-017**: Mimari kararlar ürün gereksinimlerine izlenebilir olmalı; bu çalışma uygulama kodu veya tüm V1 için tek görev listesi üretmemeli.

### Key Entities

Kayıt neyin izlendiğini, Hedef bağlamı, Event gerçekleşen olayı, State Group birbirini dışlayan durumları, buton bağlantısı hızlı erişimi, Pro hakkı ücretli yetenek erişimini temsil eder. Tarihsel bilgiler güncel tanımlardan bağımsız korunur.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Kabul senaryolarında her bilinçli Counter işlemi ayrı olay üretir; aynı bağlamda hiçbir zaman iki açık Duration bulunmaz (FR-001–003).
- **SC-002**: Arşivleme/unlink/unarchive senaryolarının tümünde geçmiş korunur ve kapatılmış süre/durum/bağlantı otomatik dirilmez (FR-004–006).
- **SC-003**: Belirsiz giriş örneklerinin hiçbirinde kullanıcı seçimi olmadan yaklaşık eşleşmeye kayıt yapılmaz (FR-007–009).
- **SC-004**: Yanlış parola, bozuk yedek ve kesinti senaryolarında mevcut veri kısmen değiştirilmez; başarılı aktarım gerekli varlıkların tamamını korur (FR-013–014).
- **SC-005**: Free/Pro ve çevrimdışı senaryolarında temel kayıt çalışır; altıncı NFC ve limit aşımı davranışları belirtilen sonuçları verir (FR-010–011,015).
- **SC-006**: Sonraki düzenlemeyi eski Undo silemez; istatistiklerin tamamı §33 kurallarına uyar (FR-012,016).
- **SC-007**: §44'ün her başlığı en az bir tasarım/sözleşme bölümüne eşlenir; teslimatta uygulama kodu ve uygulama görev listesi bulunmaz (FR-017).

## Assumptions

- Kullanıcı yerel Android Studio ve NFC'li gerçek cihaza erişebileceğini onayladı.
- Gelişmiş Pro rapor/dashboard/kişiselleştirme içerikleri kendi özellik specification'larında tanımlanacak; bu mimari çalışma yeni ürün kapsamı icat etmez.
- Performans ve cihaz uyumluluğu ölçümleri gelecekteki uygulama doğrulamasıdır; mimari onayı bu testlerin çalıştığı anlamına gelmez.
