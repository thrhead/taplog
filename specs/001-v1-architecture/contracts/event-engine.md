# Event Engine sözleşmesi

Bu sözleşme Android UI veya kanal nesnesi içermez. Her istek transaction dışında oluşturulabilir; uygulanırken güncel durum yeniden okunur.

## Komutlar

`LogMoment(recordId, targetId?, occurredAt, source)`; `AddCounter(recordId, targetId?, quantity?, occurredAt, source)`; `StartDuration`; `FinishDuration`; `ToggleDuration`; `SetState`; `CreateRecordAndLog`; `EditEvent`; `DeleteEvent`; `Undo`.

Komutlar kimlik, zaman, kaynak ve beklenen revision/dataset generation bilgisini taşıyabilir. Kanal katmanı kullanıcı niyetini doğrulanmış komuta dönüştürür; Engine doğal dil veya Android Intent almaz.

## Sonuçlar

- `Applied`: commit edilmiş Event veya yaşam döngüsü değişikliği ve etkilenen kimlikler.
- `NeedsConfirmation`: belirsiz niyet/eşleşme, limit veya hard-delete onayı.
- `Conflict`: beklenen revision, dataset generation veya Duration/State bağlamı artık geçerli değil.
- `Invalid`: domain invariant ihlali.
- `StorageFailure`: transaction commit edilemedi; kullanıcı verisi kısmen değiştirilmiş sayılmaz.

`Applied` yalnız commit sonrasında döner. Feedback başarısızlığı `Applied` sonucunu geriye çevirmez.

## İşlem sınırları

Archive/unlink: tanım/ilişki, açık Duration→INCOMPLETE, State generation reset, bağlantı orphan ve ilgili Undo invalidation tek transaction. Record+ilk Event tek transaction. Hard-delete önce etki özeti ve confirmation, sonra kapsam içindeki Event/snapshot/binding temizliği tek transaction.

Kayıt+Target başına tek OPEN Duration; hedefsiz scope ayrı sentinel kullanır. State group+Target scope başına tek mevcut State generation. Engine global debounce uygulamaz.

## Undo

UndoReceipt beklenen Event revision ve dataset generation ile tüketilir. Event daha sonra değişmişse `Conflict` döner ve Timeline düzeltmesi önerilir. Undo audit log değildir; yedeklenmez. Import, tüm eski receipt'leri generation değişimiyle geçersiz kılar.
