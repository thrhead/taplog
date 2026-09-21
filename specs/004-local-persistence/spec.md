# Feature Specification: TapLog Kalıcı Yerel Veri Katmanı

**Feature Branch**: `004-local-persistence`

**Created**: 2026-09-16

**Status**: Ready for implementation

**Input**: User description: "TapLog Persistence / Data Layer; already-defined core domain ve Event Engine için durable local persistence."

## Kapsam ve kaynakların önceliği

Bu özellik, uygulanmış/kararlaştırılmış `:core` domain ve kanal-bağımsız Event Engine durumunun cihaz üzerinde kalıcı ve yerel olarak saklanmasını tanımlar. Ürün davranışında yeni bir anlam tanımlamaz. Kaynak önceliği: [PRD](../../docs/product/TapLog_V1_PRD.md), [anayasa](../../.specify/memory/constitution.md), [V1 mimarisi](../001-v1-architecture/), [Android foundation](../002-android-foundation/), [Core Domain + Event Engine](../003-core-event-engine/).

Bu dilim yalnızca veri katmanı sözleşmeleri, yerel saklama, eşleme, işlem, şema sürümleme ve kurtarma davranışını kapsar. UI, NFC, Widget, Quick Settings, parser, backup/export/import, statistics, monetization, AI ve sync/cloud kapsam dışıdır.

## Clarifications

### Session 2026-09-17

- Q: Kalıcı veri setinde, core sözleşmesindeki DatasetGeneration dışında ayrı bir global mutation revision saklanmalı mı? → A: A — DatasetGeneration + etkilenen nesnelerin Revision değerleri authoritative olur; ayrı global mutation revision eklenmez.
- Q: Bir Event snapshotı, olay anındaki hangi değerleri authoritative olarak saklamalı? → A: A — recordId, targetId?, olay anındaki Record/Target name+icon, behavior ve ilgili Counter unit saklanır; canlı tanımdan yeniden oluşturulmaz.
- Q: Şema sürümleme ve migration işlemlerinin sorumluluğu hangi katmanda olmalı? → A: A — `:data` persistence adapter’ı schema version/migration’ı sahiplenir; atomik başarısızlıkta eski veri korunur ve typed `PersistenceFailure` döner. Commit sırasında oluşan persistence failure ise mevcut Boolean port üzerinden `false`/`StorageFailure` olur.
- Q: Aynı dataset üzerinde eşzamanlı iki commit olduğunda stale-write koruması nasıl uygulanmalı? → A: A — Persistence adapter commit sırasında beklenen revision/generation değerlerini aynı atomik işlem içinde doğrular; uyuşmazlıkta değişiklik yapmadan `false` döner ve geçerli commit’ler serialize edilir. Mevcut Boolean port nedeniyle bu sonuç Event Engine'de `StorageFailure` olur; `Conflict` yalnızca commit öncesi command-level stale checks yoludur.
- Q: Event payload fiziksel olarak nasıl saklanmalı? → A: A — `EventEntity` tek satırında bir behavior/payload discriminator ve behavior-specific nullable typed columns kullanılmalı; ayrı payload tablosu, serbest JSON deposu veya canlı tanımlardan yeniden kurma yoktur.

Persistence, core sözleşmesinde tanımlanmayan ayrı bir global mutation revision üretmez veya saklamaz. Genel stale bağlamı için DatasetGeneration; Record, Target, relationship, Event, State scope/generation, binding ve Undo için etkilenen nesnenin monotonik Revision değeri authoritative sürüm bilgisidir.
Event snapshotının authoritative alanları Event Record/Target kimlikleri, olay anındaki Record/Target ad ve ikonları, behavior ve Counter davranışı için olay anındaki optional unittir. Snapshot canlı Record/Target tanımlarından yeniden oluşturulmaz; snapshotta olmayan mutable tanım alanları geçmiş anlamının parçası değildir.
Schema version ve migration sorumluluğu `:data` persistence adapter’ına aittir. Migration işlemleri atomik olmalı; başarısız migration eski geçerli veriyi değiştirmemeli ve `StorageFailure` üretmelidir. `:core` yalnızca domain invariant doğrulamasını yürütür.
Eşzamanlı commit’lerde persistence adapter beklenen entity revision, dataset generation ve ilgili scope generation değerlerini aynı atomik işlem içinde compare-and-check ile doğrular. Geçerli commit’ler serialize edilir; beklenen bağlam güncel değilse hiçbir kalıcı değişiklik yapılmadan `false` döner. Event Engine bu Boolean reddini `StorageFailure` olarak eşler; command-level stale checks ise `Conflict` üretmeye devam eder.

The older 001 architecture data-model note mentioning a global mutation counter is superseded for this feature by this approved clarification: the persisted schema contains DatasetGeneration plus affected entity/scope revisions only. No global mutation revision is introduced by local persistence.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Event'i güvenle kalıcılaştır (Priority: P1)

Kullanıcı herhangi bir desteklenen kanaldan geçerli bir Event komutu verdiğinde, Event ve onun tarihsel anlamı cihazda kalıcı olur; uygulama yeniden başlatılsa da aynı geçmiş geri yüklenir.

**Why this priority**: TapLog'un temel değeri güvenilir EVENT + TIMESTAMP geçmişidir (PRD §§1, 5, 12, 34, 43; anayasa I–III).

**Independent Test**: Her dört davranış için geçerli komutları kaydet, süreci sonlandırıp yeniden başlat, aynı domain durumunu ve snapshot'ları oku.

**Acceptance Scenarios**:

1. **Given** aktif bir Record ve geçerli Moment/Counter/Duration/State komutu, **When** Event Engine commit sınırından döner, **Then** ilgili Event, payload, snapshot, sequence ve revision kalıcı olarak okunur.
2. **Given** başarılı commit sonrası süreç sonlandırılmış, **When** veri katmanı yeniden açılmış, **Then** son başarılı domain durumu eksiksiz geri gelir.
3. **Given** aynı anda iki bilinçli Counter komutu, **When** ikisi de geçerli şekilde kabul edilir, **Then** iki ayrı Event korunur; veri katmanı global duplicate suppression uygulamaz.

### User Story 2 - İşlemleri atomik ve deterministik yürüt (Priority: P1)

Event Engine tek bir operasyonun tüm etkilerini birlikte kaydeder veya hiçbirini kaydeder; hata halinde çağıran katman `StorageFailure` ile güvenilir biçimde verinin değişmediğini anlayabilir.

**Why this priority**: Yarım Event, yanlış süre veya ayrışmış lifecycle durumu geçmiş güvenini bozar (003 Event Engine sözleşmesi; PRD §§31–32, 43).

**Independent Test**: Her commit türünde hata enjeksiyonu yap; commit öncesi snapshot ile işlem sonrası okunabilir durumu karşılaştır.

**Acceptance Scenarios**:

1. **Given** `CreateRecordAndLog`, **When** commit başarılı, **Then** Record ve ilk Event aynı kalıcı işlemde bulunur.
2. **Given** archive veya unlink etkisi, **When** commit başarılı, **Then** tanım/ilişki, OPEN Duration→INCOMPLETE, State generation reset, binding orphaning ve ilgili Undo geçersizliği birlikte uygulanır.
3. **Given** kalıcı silme için açık confirmation ve belirlenmiş kapsam, **When** commit başarılı, **Then** yalnız onaylanan kapsam ve ilişkili metadata kaldırılır.
4. **Given** herhangi bir yazma/commit adımı başarısız, **When** işlem sonuçlanır, **Then** kısmi kalıcı veri görünmez ve sonuç `StorageFailure` olur.

### User Story 3 - Tarihsel geçmişi ve yaşam döngüsünü koru (Priority: P1)

Kullanıcı Record veya Target'ı yeniden adlandırsa, arşivlese, ilişkiyi kaldırsa veya geri etkinleştirse geçmişteki Event'lerin anlamı değişmez; yalnızca açıkça onaylanan permanent delete geçmişi kaldırabilir.

**Why this priority**: History is sacred; arşivleme/unlink geri alınabilir yaşam döngüsü, permanent delete ise açık ve geri döndürülemez istisnadır (PRD §§28–32, 42–43; anayasa III).

**Independent Test**: Tanımları ve ilişkileri değiştir, geçmiş snapshot'larını karşılaştır; unarchive ve hard-delete kapsamlarını ayrı doğrula.

**Acceptance Scenarios**:

1. **Given** Event oluşturulurken Record/Target adı ve ikonu, **When** tanım sonra değiştirilir, **Then** Event snapshot'ı eski değerleri korur.
2. **Given** arşivleme veya unlink, **When** veri yeniden okunur, **Then** taraflar ve geçmiş korunur; etkilenen binding ORPHANED ve son bilinen snapshot ile saklanır.
3. **Given** unarchive, **When** işlem tamamlanır, **Then** kimlik ve geçmiş korunur ancak terminal Duration yeniden OPEN olmaz, temizlenmiş State dirilmez ve ORPHANED binding otomatik ACTIVE olmaz.
4. **Given** a core `DeleteImpact(recordId, targetId?, eventIds)` confirmation, **When** işlem tamamlanır, **Then** persistence removes exactly the Event ids and related rows named by that impact. A null target means the core Record scope, including that Record's no-Target Events; a non-null target means only that Record–Target pair. Target definitions are not independently deleted by this feature because the current core contract has no standalone Target-delete command.

### User Story 4 - Güvenilir düzeltme ve yeniden açılış (Priority: P2)

Kanal veya sonraki yönetim özelliği, eski/stale bir komutun yeni veriyi ezmediğini ve Undo'nun yalnız geçerli bağlamda uygulanabildiğini bilir.

**Why this priority**: Hızlı düzeltme, ancak yeni geçmişi sessizce bozmuyorsa güvenlidir (PRD §13; 003 sözleşmesi).

**Independent Test**: Revision, dataset generation, scope generation ve süreç yeniden açılışı arasında eski komut/receipt senaryolarını çalıştır.

**Acceptance Scenarios**:

1. **Given** beklenen revision veya generation güncel değil, **When** Engine commit dener, **Then** veri katmanı yeni durumu korur; adapter `false` döner ve mevcut Boolean port nedeniyle Engine `StorageFailure` üretir. Command-level stale checks ise `Conflict` üretmeye devam eder.
2. **Given** geçerli UndoReceipt, **When** receipt'in beklediği Event revision ve kapsam sürümleri aynıysa, **Then** Undo'nun belirlediği değişiklik atomik uygulanır.
3. **Given** receipt tüketilmiş, stale veya archive/unlink ile geçersizleşmiş, **When** yeniden kullanılır, **Then** değişiklik yapılmaz ve command-level stale guard'ın `Conflict` sonucu veya commit-level Boolean portun `StorageFailure` sonucu korunur.
4. **Given** süreç commit sırasında kapanmış veya yeniden başlatılmış, **When** uygulama açılır, **Then** yalnız tam commit edilmiş önceki durum okunur; yarım işlem görünmez.

## Edge Cases

- No-Target scope, gerçek Target kimliğiyle çakışmayacak şekilde ayrı ve kararlı anahtar olarak saklanır.
- Aynı `occurredAt` değerindeki Event'ler kalıcı `sequence` ile deterministik sıralanır.
- OPEN Duration için Record + optional Target başına en fazla bir kayıt korunur; farklı Target scope'ları birbirinden bağımsızdır.
- `COMPLETED` ve `INCOMPLETE` Duration terminaldir; saklama/geri yükleme sırasında bitiş zamanı uydurulmaz.
- State currentness yalnızca mevcut State Group + Target scope ve generation üzerinden hesaplanır; eski generation Event'i kendiliğinden current olmaz.
- İlişki unlink edildiğinde ilişki saklanır fakat yeni Event için uygun değildir; geçmiş FK referansları bozulmaz.
- Permanent delete confirmation is owned by core `DeleteScope`/`DeleteImpact`; the current `CommitOperation` carries only the resulting expected/state/lifecycle values. The data layer does not recompute or widen the impact. A changed dataset or stale expected state is write-free rejected by the approved compare-and-check boundary; a new impact requires a new core preview/confirmation before producing the commit state.
- Bozuk, eksik veya invariant'lara aykırı persisted kayıtlar geçerli domain durumuna sessizce çevrilmez; deterministic storage/schema failure verilir ve mevcut geçerli durum korunur.
- Process crash, transaction commit sınırından önce olursa önceki tam durum; commit sonrasında olursa yeni tam durum yeniden açılışta okunur. Yarım transaction hiçbir yeniden açılışta görünmez.
- For `DeleteImpact(recordId, null, eventIds)`, delete the Record, all its RecordTarget rows, its bindings, the listed Events (including no-Target Events selected by the core preview), and Undo receipts for those Event ids; retain every Target definition and unrelated Event/metadata. For `DeleteImpact(recordId, targetId, eventIds)`, delete only that relationship, pair-scoped bindings, listed pair Events, and their Undo receipts; retain the Record, Target, other relationships, other-target Events, and no-Target Events. Shared Target rows and unrelated lifecycle metadata remain. The adapter does not invent standalone Target deletion or extra lifecycle resets; it persists the core-provided committed state exactly.
- Persistence-facing failures are deterministic: read/open/migration/corruption failures throw a typed `PersistenceFailure`; commit-time mapping/database failures return `false` and therefore become Engine `StorageFailure`. No failure silently rewrites, drops, repairs, or reconstructs historical rows.
- Revision ve kimlikler geri alınmaz veya yeniden kullanılmaz; `nextSequence` geriye gitmez.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Veri katmanı, `DomainState`'in Records, Targets, Record–Target ilişkileri, State Groups, Events, current State bilgisi, State generations, dataset metadata, bindings ve UndoReceipt alanlarını kayıpsız okuyup yazmalıdır. (003 `TransactionBoundary`/data model; PRD §§5–7, 43.)
- **FR-002**: Persisted modeller, core domain modellerinden ayrı bir saklama temsili olmalı; iki yönlü mapping tüm kimlik, enum/discriminator, nullable scope, zaman, revision, sequence, snapshot ve payload değerlerini korumalıdır. Core domain Android/framework bağımsız kalmalıdır. (Anayasa I, V; 002 FR-007–009.)
- **FR-003**: Event persistence, tek `EventEntity` satırında Event'in immutable identity/reference'larını, occurredAt, createdAt, updatedAt, sequence, source, revision, behavior discriminator'ını, authoritative snapshot alanlarını (recordId, targetId?, olay anındaki Record/Target name+icon, behavior ve Counter için optional unit) ve yalnız ilgili Moment/Counter/Duration/State payloadının typed nullable columns'ını saklamalıdır. Ayrı payload tablosu, serbest JSON veya canlı Record/Target tanımlarından yeniden oluşturma kullanılmamalıdır. (PRD §§5, 12, 32; 003 FR-005–006, FR-017.)
- **FR-004**: Duration state `OPEN`, `COMPLETED`, `INCOMPLETE` kurallarını saklama ve okuma sırasında korumalı; INCOMPLETE için end timestamp uydurmamalı ve terminal kayıtları yeniden açmamalıdır. (PRD §32; 003 FR-011–014.)
- **FR-005**: State-group state, StateScope başına current Record referansı, generation ve reset sınırlarını tarihsel Event'leri silmeden korumalıdır. (PRD §7.4, §44.1 item 4; 003 FR-015–016.)
- **FR-006**: İlişki lifecycle'ı linked/unlinked, definition lifecycle'ı active/archived ve binding lifecycle'ı active/orphaned ayrımlarını korumalı; archive/unlink sonrası etkilenen açık süre, State, binding snapshot ve Undo metadata etkileri tek commit'te saklanmalıdır. (PRD §§18, 28–32; 003 FR-014, FR-019.)
- **FR-007**: UndoReceipt; etkilenen Event/operation, beklenen revision, scope versions, dataset generation ve consumed/geçersiz durumunu saklamalı; audit log olarak yorumlanmamalı ve bu dilimde backup/export kapsamına alınmamalıdır. (003 data model ve contract; PRD §13.)
- **FR-008**: Veri erişim sınırı, Event Engine'in güncel domain state'i okuyabilmesini ve `CommitOperation(expected, state, lifecycleEffect)` atomik uygulayabilmesini sağlamalıdır; Engine'e UI, Android Intent, kanal nesnesi veya parser girdisi sızmamalıdır. (003 contract; anayasa I, IV, V.)
- **FR-009**: Moment, Counter, Duration, State, `CreateRecordAndLog`, archive/unlink ve confirmed permanent delete işlemleri ya hep ya hiç commit edilmelidir. `Applied` yalnız commit başarıyla tamamlandıktan sonra gözlenmelidir. (003 contract; 001 data model.)
- **FR-010**: Commit başarısızlığında tüm değişiklikler geri alınmış görünmeli, çağıran katman `StorageFailure` almalı ve non-Applied sonuçlar committed Event id/revision iddiasında bulunmamalıdır. (003 `EngineResult`; anayasa III.)
- **FR-011**: Permanent delete, mevcut core `DeleteImpact(recordId, targetId?, eventIds)` ve `DeleteConfirmation` kapsamını veri katmanına taşımalı; null-target Record scope veya non-null Record–Target pair scope dışına çıkmadan ilgili Event/snapshot/relationship/binding/Undo metadata kaldırılmalı, Target tanımları ve unrelated history korunmalıdır. Standalone Target delete bu feature tarafından tanımlanmamalıdır. (PRD §§29–31; 003 contract.)
- **FR-012**: Archive/unlink normal silme değildir; kimlikleri ve geçmişi korumalı, unarchive kimlikleri koruyarak yalnız aktif kullanım uygunluğunu geri getirmeli ve terminal lifecycle etkilerini tersine çevirmemelidir. (PRD §§28–32; 003 FR-018–019.)
- **FR-013**: Şema sürümü ve migration sorumluluğu `:data` persistence adapter’ında açıkça yönetilmeli; fresh database creation `version = 1` ile yapılmalı ve desteklenen upgrade edge'leri açıkça listelenmelidir. Bu feature’da v1’den başka desteklenen upgrade edge’i yoktur; gelecekteki v2+ migration’lar kapsam dışıdır. Migration'lar atomik ve non-destructive olmalı, invariant'ları doğrulamalı, destructive fallback kullanmamalı ve başarısız migration mevcut geçerli veriyi değiştirmeden typed `PersistenceFailure` üretmelidir. `:core` migration teknolojisi veya şema bilgisi taşımamalıdır. (001 data model; 002 foundation; PRD §44.)
- **FR-014**: Kimlik, etkilenen nesne revision'ları, dataset generation, State generation ve sequence değerleri yeniden açılışta korunmalı; ayrı global mutation revision saklanmamalıdır. Yeni dataset generation gerektiren bir değişiklikte eski callback/Undo bağlamı stale kabul edilmelidir. (003 data model/contract.)
- **FR-014a**: Eşzamanlı commit’lerde adapter, beklenen revision/generation değerlerini commit ile aynı atomik işlemde doğrulamalı; geçerli commit’leri serialize etmeli ve stale bağlamda kalıcı değişiklik yapmadan `false` döndürmelidir. Bu feature için typed commit-conflict sonucu kapsam dışıdır; mevcut Boolean port nedeniyle Event Engine sonucu `StorageFailure` olarak raporlar, command-level stale checks ise `Conflict` yoludur.
- **FR-015**: Veri katmanı tamamen local-first çalışmalı; core Event logging için hesap, ağ veya sunucu erişimi gerektirmemelidir. Geçici ağ yokluğu hiçbir yerel commit'i başarısız kılmamalıdır. (PRD §§34, 37, 43; anayasa II.)
- **FR-016**: Repository/data-access sınırları, core domain'in kalıcı saklama teknolojisini bilmemesini; data adapter'ın Android/framework bağımlılıklarını `:data` sınırında tutmasını ve `:data → :core` bağımlılık yönünü korumasını sağlamalıdır. (002 FR-007–009; 001 architecture.)
- **FR-017**: Persisted verinin her okuması domain'e aktarılmadan önce discriminator, ilişkiler, lifecycle, revision, timestamps, sequence, Duration, State ve scope invariant'larını doğrulamalı; malformed/corrupt rows, mapping failures, database-open failures ve migration failures typed `PersistenceFailure` olarak sınıflandırılmalı, retryability açıkça belirtilmeli ve geçersiz veri sessizce düzeltilmemelidir. `DatabaseReadFailure` ve `DatabaseOpenFailure` veri değiştirmeden yeniden denenebilir; `MigrationFailure`, `CorruptRowFailure` ve `MappingFailure` harici onaylı migration/veri düzeltmesi olmadan yeniden denenemez. Commit-time persistence failures ise Boolean port üzerinden `false`/`StorageFailure` olarak kalır; belirsiz commit sonrası blind retry yapılmaz. (003 edge cases; anayasa III, V.)
- **FR-018**: Repository sözleşmeleri fake/in-memory adapter ile çalıştırılabilir olmalı; atomic commit, rollback, mapping, migration, index/constraint, restart recovery ve failure injection testleri Android UI veya ağ olmadan yürütülebilmelidir. (Anayasa V; 002 FR-008, FR-011.)
- **FR-019**: Local storage, Event history ve gerekli metadata için `(occurredAt, sequence)` ascending kronolojik sorgu, exact Record/Target/no-Target scope filtering ve current Duration/State sorgusu sağlamalı; equal timestamps sequence ile çözülmeli, Duration yalnız OPEN ve exact scope ile, State yalnız requested group + target scope + active generation ile filtrelenmeli; bu dilimde Timeline UI veya statistics hesaplama sunulmamalıdır. (PRD §§12, 33; kapsam dışları.)
- **FR-020**: Veri katmanı duplicate suppression, kanal feedback'i, NFC/Widget/Quick Settings binding davranışı, parser, AI, backup/export/import, encryption, sync, statistics veya monetization ürünü uygulamamalıdır; yalnız core sözleşmesinin gerektirdiği binding lifecycle metadata'yı saklayabilir. (003 explicit exclusions; kullanıcı kapsamı.)

### Key Entities

- **Persisted Record / Target**: Yeniden kullanılabilir tanımlar; lifecycle, immutable identity ve revision taşır.
- **Persisted RecordTarget**: Record–Target bağının linked/unlinked yaşam döngüsü ve revision'ı.
- **Persisted Event**: Olay zamanı, kabul/oluşturma/değişiklik zamanları, sequence, source, behavior payload'ı ve historical snapshot.
- **Duration State**: start, optional end, status ve yalnız INCOMPLETE için deterministic reason.
- **State Scope State**: State Group + optional Target kapsamının current referansı, generation ve reset sınırı.
- **Binding Lifecycle Metadata**: Channel-independent binding identity, scope, lifecycle, revision ve son bilinen display snapshot; kanal uygulaması değildir.
- **UndoReceipt**: Sınırlı, revision/generation güvenlik bağlamı olan düzeltme metadata'sı; audit değildir.
- **Dataset Metadata**: Dataset generation ve next sequence gibi yeniden açılış ve stale-context korumaları; ayrı global mutation revision içermez.
- **Schema Version**: Persisted formatın sürümü ve desteklenen migration geçmişi. İlk yayımlanan şema `version = 1`'dir; desteklenen geçmiş şu an yalnızca v1'dir. Bilinmeyen ileri/geri sürümler destructive fallback olmadan storage failure üretir.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Dört behavior türünün her biri için 100 ardışık başarılı commit sonrası, süreç yeniden başlatıldığında 100% Event identity, timestamp, payload, snapshot, source ve sequence değerleri değişmeden okunur.
- **SC-002**: Her atomik işlem ailesinde (Event, Record+Event, archive/unlink, permanent delete) en az 20 hata enjeksiyonu denemesinin 100%'ünde yarım persisted state görülmez ve sonuç deterministic failure kategorisine ayrılır.
- **SC-003**: 1.000 Event içeren yerel bir veri kümesi, ağ bağlantısı kapalıyken başarılı biçimde açılır; temel okuma ve yeni Event commit işlemleri için ağ isteği yapılmaz.
- **SC-004**: Snapshot regression testlerinin 100%'ünde Record/Target rename veya icon değişikliği önceki Event snapshot'ını değiştirmez; archive/unlink geçmiş Event sayısını azaltmaz.
- **SC-005**: Migration testleri, desteklenen her önceki şemadan güncel şemaya geçişte mevcut geçerli veriyi korur; geçersiz/yarım migration denemelerinin 100%'ünde eski veri kullanılabilir kalır.
- **SC-006**: Stale revision, dataset generation, State generation ve UndoReceipt senaryolarının 100%'ünde yeni veri korunur; stale işlem `Applied` olarak raporlanmaz.
- **SC-007**: `:data` pure-JVM test ortamı, Android framework, UI veya ağ olmadan mapping, repository contract, atomicity, rollback ve deterministic failure testlerini çalıştırabilir; `:core` modülünde veri teknolojisi bağımlılığı bulunmaz.
- **SC-008**: Kalıcı silme kapsamı testlerinin 100%'ünde onaylanan kapsam dışındaki Record, Target, no-Target Event history ve ilişkili snapshot'lar korunur.

## Assumptions

- `:data` modülü, 002 Android foundation'da tanımlanan gelecekteki persistence adapter sınırıdır; `:core` içine database, Android veya framework tipi eklenmez.
- 001 mimarisindeki yerel SQLite/Room yönü ve indeks önerileri planlama için teknik bağlamdır; bu spec belirli kütüphane/API sözleşmesini ürün gereksinimi olarak dayatmaz.
- Persisted schema, 003 data modelindeki Event tek tablo/discriminator, typed nullable payload, kalıcı sequence ve scope indeksleriyle uyumlu olacaktır; plan aşaması fiziksel tablo ayrıntılarını belirler.
- UndoReceipt ve cihaz/platform binding metadata'sı bu dilimde saklanır; backup/import kapsamı değildir ve ilgili özelliklerde ayrıca ele alınır.
- Yerel dosya/depolama bozulması için güvenli davranış, mevcut geçerli snapshot'ı korumak ve açık hata vermektir; otomatik veri tahmini veya sessiz onarım yapılmaz.
- Çoklu uygulama süreci veya hesaplar arası paylaşım V1 çekirdek kapsamı değildir; tek cihazdaki local-first dataset hedeflenir.

## Explicit Exclusions

- UI, Timeline ekranı, NFC okuma/eşleştirme/dispatch, Widget, Quick Settings, notification ve kanal duplicate suppression.
- Natural Language parser, AI, statistics, monetization/entitlement ve sync/server/cloud/account.
- Backup/export/import, `.tpb`, encryption ve işletim sistemi otomatik backup/transfer davranışı.
- Yeni domain semantiği, Event Engine invariant değişikliği, Record/Target davranış tasarımı veya PRD'deki Future Scope.
