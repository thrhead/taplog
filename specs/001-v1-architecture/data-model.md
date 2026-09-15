# Veri Modeli ve Bütünlük

Kaynak: [spec](spec.md), [PRD](../../docs/product/TapLog_V1_PRD.md). SQL veya migration kodu değildir.

## Değerler ve varlıklar

Kimlikler rastgele UUID; Android widget ID'si domain kimliği değildir. Zaman UTC epoch-millisecond; source zone/offset gerektiğinde ayrıca tutulur. `occurredAt/createdAt/updatedAt` ayrıdır. Eşit zamanda kalıcı sequence sıralaması; import sequence'i korur. Revision monoton artar; silinen kimlik yeniden kullanılmaz. Counter canonical decimal string/BigDecimal, pozitif ve sonlu; float yok.

| Varlık | Alanlar | Kurallar |
|---|---|---|
| Record | id, name, icon, behavior, lifecycle, unit?, defaultQuantity, stateGroupId?, hasEverLogged, revision | İlk Event sonrası behavior/unit kilitli; Event silinince kilit kalkmaz |
| Target | id, name, icon, lifecycle, revision | Birden çok Record |
| RecordTarget | recordId+targetId, linked, revision | Unlink ilişkiyi pasif kılar; geçmiş FK bozulmaz |
| StateGroup | id, name, revision | Birbirini dışlayan State Kayıtları |
| Event | id, recordId, targetId?, behavior, occurredAt, createdAt, updatedAt, sequence, source, revision, snapshot | Yaşayan FK + tarihsel görünüm |
| Counter alanları | quantity, unit? | Yalnız Counter; Record birimi |
| Duration alanları | startAt, endAt?, status, incompleteReason? | Yalnız Duration; COMPLETED iff endAt var; end>=start |
| State alanları | stateGroupId, lifecycleGeneration | Yalnız State |
| StateScope | groupId+targetScope, generation, resetSequence, resetAt | Temizleme sınırı, tarihsel reset kayıtları korunur |
| NfcBinding | buttonId, mode, recordId?, targetId?, lifecycle, revision, lastBindingSnapshot, previousReference? | Action: Record+opsiyonel Target; Target: yalnız Target |
| DigitalBinding | localId, kind, platformId, recordId?, targetId?, lifecycle, revision, snapshot, datasetGeneration | Widget instance veya tek Tile, cihaz içi |
| PinnedAction | id, recordId, targetId?, order | Home tercihi; aktiflik yeniden denetlenir |
| UndoReceipt | id, operation, affectedId, expectedRevision, beforeImage?, scopeVersions, datasetGeneration, consumed | Sınırlı Undo, audit değil, yedek dışı |
| DatasetMetadata | singleton, generation, mutationRevision, nextSequence | Import/eskimiş callback koruması |
| UserSettings | tipli ayarlar | Yedek dahil |
| Entitlement | local singleton, verifiedState, verificationMetadata | Yedek dışı; overwrite korur |

Event tek tablo, behavior discriminator ve tipli nullable alanlar kullanır. Core tipleri/data validation/import geçersiz kombinasyonları engeller. Serbest JSON Event deposu ve ayrı payload tabloları gerekmez.

## Snapshot ve indeksler

Event snapshot Record/Target adı/ikonu, behavior ve unit içerir. Oluşumda alınır; ad veya zaman/miktar düzenlemesi eski görünümü güncel adlarla yeniden yazmaz. Tarihsel Event'leri başka Kayıt/Hedefe toplu taşıma bu temel tasarımın sunduğu özellik değildir.

NFC orphan snapshot bağlantı geçersizleşirken son canlı görünümden alınır; Event snapshot'ından farklıdır. Görüntüleme canlı FK'ye bağlı değildir; ilgili hard-delete mapping'i de kaldırır.

Event zaman/sequence, Record-zaman, Target-zaman; State scope/generation/zaman; aktif RecordTarget; NFC buttonId ve dijital kind/platformId unique indeksleri. Aynı Kayıt+Target için OPEN Duration partial unique; hedefsiz OPEN için ayrı partial unique indeks gerekir. SQL NULL tekilliği varsayılmaz. State scope hedefsiz anahtarı gerçek UUID ile çakışmayan teknik değer kullanır. Migration gerçek SQLite'ta test edilir, destructive fallback yoktur.

## Duration bütünlüğü

- OPEN: start var, end/neden yok.
- COMPLETED: start/end var, end>=start.
- INCOMPLETE: start var, end yok, neden var; terminal.
- OPEN→COMPLETED finish/toggle; OPEN→INCOMPLETE archive/unlink. Unarchive, edit ve Undo terminal süreyi OPEN yapamaz.
- Tamamlama Undo'su Event revision + aktif bağlam + başka OPEN olmaması ile mümkün; aksi halde Conflict.
- Tek OPEN kuralı dışında tamamlanmış tarihsel aralıkların örtüşmesine yeni bir ürün yasağı eklenmez.
- Saat geriye giderse negatif bitiş reddedilir; UTC farkı kullanılır, reboot sonrası monotonic saat taşınmaz.

## State bütünlüğü

Her group+Target scope bir generation taşır. Mevcut State, mevcut generation'ın geçerli Event'leri arasında `(occurredAt, sequence)` sonuncusudur. Temizlemede generation artar, eski generation Timeline'da kalır fakat geri aktifleşmez. Reset geçmişi bu sınırları korur ve yedeğe girer.

Archive/unlink mevcut State'i geçersiz kılarsa sadece ilgili scope temizlenir. Eski Event edit'i generation'ı yeniye taşımaz. Temizleme öncesine yeni geçmiş Event girilirse tarihine ait generation'a atanır; bugünkü durumu diriltmez. Eşit zaman sınırları sequence ile çözülür. Yeniden gruplandırma/toplu tarihsel taşıma bu tasarımda sunulan bir düzenleme değildir.

## Atomiklik ve silme

Archive/unlink: tanım/ilişki + Duration + State reset + orphan + ilgili Undo geçersizleşmesi aynı transaction. Hard-delete: onay kapsamındaki Event/snapshot/Undo/binding metadata birlikte kaldırılır. Target silmede farklı/hedefsiz geçmiş korunur. Dijital platform örneği varsa rebind görünümü kalır; NFC Unknown olur. Onay sonrası silme kapsamı genişlemişse yeniden onay gerekir.

Record+ilk Event tek transaction. Birleşik işlemin Undo'su mevcut tasarımda Event'i geri alır, tanımı kaldırmak ayrı yönetim işlemidir; ilgili feature spec'i bunu görünür biçimde doğrulamalıdır. Bu, açıkça onaylanmamış tanım silmeyi önleyen teknik varsayımdır.
