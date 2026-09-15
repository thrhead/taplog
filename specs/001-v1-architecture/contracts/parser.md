# Türkçe parser ve AI sözleşmesi

## Deterministik hat

Girdi normalleştirilir → fiil/niyet, behavior, miktar, birim, zaman ve aday Record/Target çıkarılır → kesin eşleşmeler değerlendirilir → belirsizlik sonucu üretilir. V1 dili Türkçedir.

Zaman yoksa giriş anı kullanılır. Açık “iki saat önce” ve “dün 15.00” desteklenir; “dün” gibi saatsiz ifade `NeedsConfirmation` üretir. Yalnız isim girdisi Event mi Record mu olduğunu seçtirmelidir. Geçmiş eylem ve yeni Record durumunda `CreateRecordAndLog` kullanılır.

Tek kesin isim eşleşmesi kullanılabilir. Yaklaşık eşleşme yalnız aday listesi üretir; kullanıcı onayı olmadan komut oluşmaz. Mevcut OPEN Duration ile çakışan geçmiş başlangıç `NeedsConfirmation`/düzeltme akışıdır.

## AI adaptörü

AI yalnız deterministik hat çözemediğinde ve cihaz/model hazırsa çağrılır. Girdi, gerekli aday özetleriyle sınırlıdır; AI bütün geçmişe veya doğrudan veritabanına erişmez. Çıktı `SuggestedInterpretation` olarak döner; aynı kesinlik/eşleşme/onay kurallarından geçer. Destek yokluğu, timeout veya hata manuel netleştirmeye döner.
