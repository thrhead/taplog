# TapLog — V1 Product Requirements Document

**Product:** TapLog
**Platform:** Android
**Version:** V1
**Product Type:** Local-first personal event logging application
**Status:** Product Definition / PRD (Final — Revision 2)

---

# 1. Product Overview

TapLog, fiziksel ve dijital dünyadaki olayları mümkün olan en düşük sürtünmeyle kaydetmeyi sağlayan bir **event logging** uygulamasıdır.

Temel fikir:

> **Anything. One tap. Logged.**

Kullanıcı bir şeyi kaydetmek istediğinde uygulamayı açmak zorunda kalmamalıdır.

Aynı kayıt sistemi farklı giriş kanallarından kullanılabilir:

* Android App
* Home Screen Widget
* Quick Settings
* NFC Physical Button
* Natural Language Input

TapLog'un temel ürünü NFC değildir.

**Temel ürün primitive'i:**

> **EVENT + TIMESTAMP**

NFC, Widget veya Quick Settings yalnızca bu Event'i oluşturmanın farklı giriş kanallarıdır.

---

# 2. Problem

Günlük hayatta kullanıcılar birçok şeyi takip etmek ister ancak klasik takip uygulamalarındaki sürtünme nedeniyle düzenli kayıt tutmazlar.

Örneğin:

* Kaç kahve içtim?
* İlacımı aldım mı?
* Bitkiyi ne zaman suladım?
* Çamaşır makinesini ne zaman çalıştırdım?
* Arabayı nereye/ne zaman park ettim?
* Ne zaman ofise geldim?
* Bir işi ne zaman başlattım ve bitirdim?

Klasik uygulamalarda kullanıcı genellikle:

1. uygulamayı açar,
2. doğru ekranı bulur,
3. doğru kaydı seçer,
4. bilgiyi girer,
5. kaydeder.

TapLog bu süreci mümkün olduğunca **tek dokunuşa** indirmeyi amaçlar.

---

# 3. Product Vision

TapLog'un amacı kullanıcıya yeni bir "habit tracker" veya karmaşık bir productivity sistemi sunmak değildir.

Amaç:

> **Gerçek hayatta gerçekleşen olayları hızlı, doğal ve güvenilir şekilde kaydetmek.**

Kullanıcı neyi takip etmek istediğine kendisi karar verir. TapLog kullanıcıyı belirli bir kullanım modeline zorlamaz.

---

# 4. Target Users

V1 hedef kitlesi: **Genel Android kullanıcılarıdır.**

Ürün yalnızca quantified-self kullanıcılarına, hardcore productivity kullanıcılarına veya habit tracker kullanıcılarına yönelik değildir.

Hem basit kullanım isteyen kullanıcı hem de daha detaylı veri toplamak isteyen kullanıcı desteklenmelidir.

---

# 5. Core Concepts

## 5.1 Record — Kayıt

**Record**, kullanıcının neyi takip etmek istediğinin kalıcı tanımıdır.

Örnek: ☕ Kahve · 💊 İlaç · 💧 Sulama · 🚗 Park · 🧺 Çamaşır · 🏢 Ofis · 🏠 Ev

Record, gerçekleşen olay değildir.

> **Terminoloji notu:** Bu doküman ve teknik mimaride kavramın adı `Record`'dır. Kullanıcı arayüzünde bu kavram her zaman **"Kayıt"** olarak gösterilir (örn. "+ Yeni Kayıt", "Kayıtlarım"). Ürün adı olan **TapLog** ile kullanıcı-facing birim adı olan **Kayıt** kasıtlı olarak farklı kelimelerdir; karışıklığı önlemek için hiçbir UI metninde "TapLog oluştur" gibi ifadeler kullanılmaz.

## 5.2 Event — Olay

**Event**, bir Record'un belirli bir zamanda gerçekleşmesidir.

Örneğin, Record: ☕ Kahve → Events: 14:32, 16:51, 19:10.

Event minimum olarak zaman bilgisini taşır. Gerektiğinde quantity, unit, target, behavior, source ve diğer metadata ile zenginleşebilir.

## 5.3 Target — Hedef

Target, Event'in hangi kişi/nesne/konum/varlıkla ilişkili olduğunu belirtmek için kullanılır.

Örneğin, Record: 💧 Sulama → Targets: 🌱 Salon Bitkisi, 🌵 Balkon Bitkisi, 🌿 Ofis Bitkisi.

Target bağımsız bir kavramdır ve birden fazla Record tarafından kullanılabilir. Target'ın kendisine ait bir **isim** ve **ikon** bulunur (Record'un ikonundan bağımsızdır).

---

# 6. Record — Target Relationship

Bir Record:

* hiç Target'a sahip olmayabilir,
* tek Target ile ilişkilendirilebilir,
* birden fazla Target ile ilişkilendirilebilir.

Örneğin, Sulama → Salon Bitkisi, Sulama → Balkon Bitkisi, Sulama → Ofis Bitkisi — bunların her biri ayrı Record değildir; aynı Record'un farklı Target bağlamlarıdır.

Bu sayede kullanıcı aynı davranışı farklı nesneler için tekrar kullanabilir. Record ↔ Target bağı (unlink) her iki tarafı da archive etmeden kaldırılabilir.

---

# 7. Behavior Types

V1'de dört temel davranış tipi bulunur.

## 7.1 Moment
Tek seferlik gerçekleşen olay. Örnek: "Kahve içtim."

## 7.2 Counter
Tekrar eden ve miktar olarak sayılabilen olay. Örnek: "Kahve +1", "Su +1", "Sigara +1". Counter kayıtlarında quantity desteklenir.

## 7.3 Duration
Başlangıç ve bitiş zamanı olan olay. Örnek: "Çamaşırı başlattım" → sonra "Çamaşırı bitirdim". Duration Event **OPEN → COMPLETED** olarak ilerler.

## 7.4 State
Kullanıcının veya bir durumun mevcut durumunu temsil eder. Örnek: Evde / Ofiste / Dışarıda. State Group içerisinde aynı anda yalnızca bir durum aktif olabilir.

---

# 8. Record Creation

Record oluşturma süreci mümkün olduğunca basit olmalıdır. Kullanıcı temel olarak:

1. neyi kaydetmek istediğini belirtir,
2. gerekiyorsa davranışı/Target'ı belirler,
3. Record oluşturulur.

Kullanıcı aynı bilgiyi birden fazla yerde tekrar yapılandırmak zorunda bırakılmamalıdır. Record oluşturulduktan sonra günlük kullanım hızlı olmalıdır.

---

# 9. Record + Event Creation İlişkisi

Bu, TapLog'un "sadece tanımlamak" ile "gerçekleştiğini kaydetmek" arasındaki farkı belirleyen temel davranış kuralıdır.

**Kural:**

* Doğal dil girdisi **geçmiş zaman / tamamlanmış eylem** bildiriyorsa (örn. *"kahve içtim"*, *"salon bitkisini suladım"*, *"çamaşırı başlattım"*), ilgili Record henüz yoksa **Record ve ilk Event tek işlemde birlikte oluşturulur.** Record zaten varsa, doğrudan yeni bir Event oluşturulur.
* Doğal dil girdisi **yalnızca bir isim/kavram** içeriyorsa (örn. *"Kahve"*), bu ifade behavior ve niyet açısından belirsizdir. Bu durumda kullanıcıya yalnızca Record tanımlamak ile hemen bir Event de kaydetmek arasında minimal bir seçim sunulabilir.
* Bu ayrım, Section 26 (Ambiguous Input) ile birlikte çalışır: TapLog belirsiz durumlarda sessizce bir tarafı seçmez.

Bu kural, Record oluşturma UX'inin geri kalanının (behavior seçimi, target eşleştirme, manuel fallback) tutarlı şekilde davranmasını sağlar.

---

# 10. Home Screen

Home ekranı TapLog'un ana çalışma alanıdır. Temel yaklaşım: **Pinned + Recent + All.**

Home adaptif bir grid yapısına sahiptir. Hedef kullanım: yaklaşık 4–6 sık kullanılan Action'ın ekranda kolayca erişilebilir olması. Cihaz ekran boyutuna göre düzen adaptif olabilir.

---

# 11. Quick Actions

Home üzerindeki temel amaç Record'u yönetmek değil, **Event oluşturmaktır.**

Örneğin "☕ Kahve" dokunulduğunda doğrudan event oluşturulabilir. Counter için "☕ Kahve +1" gibi aksiyonlar kullanılabilir.

Target'lı Record'lar, Home'da **Record + Target kombinasyonu** olarak pinlenir — Target seçimi tekrar sorulmaz:

> 🌱 Salon — Sulama
> 🌵 Balkon — Sulama

Bunlar iki farklı Record değildir; aynı Record'un farklı Target bağlamlı Quick Action görünümleridir. Amaç, kullanıcının günlük kullanımda tekrar Target seçmek zorunda kalmamasıdır.

---

# 12. Timeline / History

TapLog oluşturulan Event'leri kronolojik olarak göstermelidir. Timeline üzerinden kullanıcı geçmiş kayıtları görebilir, Event detayını açabilir, düzenleyebilir, silebilir, gerektiğinde düzeltebilir.

Timeline, ürünün geçmiş verinin güvenilir şekilde incelenebildiği temel alanıdır.

---

# 13. Undo / Correction

Bir Event oluşturulduktan sonra kullanıcı hızlı şekilde hata düzeltebilmelidir (Undo / Edit / Delete). Feedback yöntemi kullanılan kanala göre değişir:

| Kanal | Feedback | Undo |
|---|---|---|
| App | Snackbar / in-app onay | In-app |
| Widget | Mümkün olduğunca hızlı görsel geri bildirim | Widget üzerinde, teknik uygulanabilirlik Android/Glance kısıtlarına göre belirlenir |
| Quick Settings | Tile state değişimi | Anlık Undo garanti edilmez; "hızlı düzeltilebilirlik" prensibiyle Timeline üzerinden correction |
| NFC | Heads-up notification (kullanıcı ekrana bakmıyor olabilir) | Notification üzerinden Undo |

---

# 14. NFC Physical Buttons

NFC, TapLog'un fiziksel dünyaya açılan giriş kanalıdır. Ucuz NFC sticker'lar fiziksel buton gibi kullanılabilir.

Örnek: yatağın yanında "😴 Uyuyorum", çamaşır makinesinde "🧺 Makineyi çalıştırdım", bitki saksısında "💧 Suladım", arabada "🚗 Park".

## 14.1 Ürün Vaadi ve Pazarlama Dili

NFC dispatch davranışı, uygulama launch mekanizması ve kilit ekranı/OEM farklılıkları Android sürümüne ve üreticiye göre değişebilir. Bu nedenle:

* **TapLog, "ekran kilitliyken/kapalıyken her koşulda çalışır" gibi bir garanti vermez.**
* Ürün vaadi her zaman şu şekilde ifade edilir: **"Telefonuna dokundur → TapLog kaydeder."**
* Bu ilke yalnızca pazarlama metinlerini değil, onboarding metinlerini de bağlar — hiçbir yerde koşulsuz/garanti ima eden dil kullanılmaz.

---

# 15. NFC Modes

## 15.1 Action Button
NFC etiketi doğrudan belirli bir aksiyona bağlıdır (örn. Salon Bitkisi → Sulandı). Kullanıcı NFC'ye dokunur ve Event oluşturulur. Bu, V1'in varsayılan NFC kullanım şeklidir.

## 15.2 Target Button
NFC fiziksel bir Target'ı temsil eder (örn. 🌱 Salon Bitkisi). NFC'ye dokunulduğunda bu Target ile ilişkili **ACTIVE** Record'lar action picker olarak gösterilir (örn. Suladım / Gübreledim). Arşivlenmiş Record'lar listede gösterilmez. Target Button, V1'de Free özelliğidir.

---

# 16. NFC Pairing & Onboarding

Kullanıcı "+ Fiziksel Buton" seçerek NFC sticker'ını telefona okutur. Ardından Action veya Target seçebilir. Pairing otomatik gerçekleştirilir — kullanıcı NFC/NDEF ile teknik olarak uğraşmaz; TapLog pairing sırasında gereken NDEF ID'sini otomatik üretir ve yazar.

**İlk NFC kurulumunda onboarding şu adımları içerir:**

1. NFC açık mı — değilse yönlendirme.
2. Cihaz/Android sürümü gerektiriyorsa, uygulamanın NFC ile otomatik başlatılabilmesi için gerekli sistem izni/erişimi hakkında kullanıcı yönlendirilir (ör. ilgili sistem ayarına deep-link).
3. Sticker'a dokundur.
4. Ne yapacak? (Action / Target seçimi)
5. Bağla.

Bu adımlar, uzun bir "Android kısıtlamaları" metni olarak değil, yalnızca gerektiğinde görünen kısa yönlendirmeler olarak sunulur (bkz. Section 14.1).

---

# 17. NFC Limits

Free kullanıcı **5 aktif NFC Button** kullanabilir. 6. NFC Button bağlanmaya çalışıldığında Pro yükseltme ekranı gösterilir; mevcut 5 NFC Button çalışmaya devam eder. **Orphaned NFC Button'lar aktif limit hesabına dahil edilmez.** Pro: sınırsız NFC Button.

---

# 18. NFC Lifecycle

NFC fiziksel olarak silinmez. Bir Record veya Target artık aktif değilse NFC bağlantısı boşa düşebilir; bu durumda NFC **ORPHANED** durumuna geçer.

## 18.1 Self-Healing Deneyimi

ORPHANED bir NFC'ye tekrar dokunulduğunda deneyim sessiz kalmaz. Sistem, tag'in **son bilinen bağlantısının adını ve ikonunu** (snapshot olarak, canlı ilişki değil) göstererek tek dokunuşla yeniden bağlanmayı kolaylaştırır:

> "Bu buton artık bağlı değil. Daha önce ☕ Kahve için kullanılıyordu."
>
> **[Kahve'ye yeniden bağla]** · **[Başka bir şeye bağla]** · **[Vazgeç]**

Unarchive işlemi NFC'yi otomatik olarak yeniden bağlamaz — kullanıcı bağlantıyı kendisi kurar (başka bir amaçla yeniden kullanılmış olabileceği için).

## 18.2 Aktif Target, Aktif Record Yok

Bir Target-Mode NFC'nin bağlı olduğu Target hâlâ **ACTIVE** olabilir, ama ona bağlı hiçbir Record ACTIVE kalmamış olabilir. Bu durumda **NFC ORPHANED olmaz** — Target hâlâ geçerlidir, sadece o an sunacak bir aksiyon yoktur:

> "Bu Target için aktif bir kayıt bulunmuyor."
>
> **[Kayıt oluştur]** · **[Vazgeç]**

Bu, ORPHANED durumunun yalnızca Record/Target'ın kendisi archive edildiğinde tetiklendiğini, "geçici olarak aksiyonsuz kalma" durumundan ayrı tutulduğunu netleştirir.

---

# 19. Unknown NFC

Bir NFC Button uygulama içinden **kalıcı olarak silinirse** (bkz. Section 31), fiziksel sticker üzerindeki bilgiler fiziksel olarak silinmiş olmaz. Bu sticker tekrar okutulduğunda TapLog bunu **yeni / bilinmeyen (Unknown) bir NFC Button** olarak değerlendirir ve kullanıcı sıfırdan pairing yapabilir.

> **Unknown ≠ Orphaned:** Orphaned = sistem tag'i tanıyor ama aktif bağlantısı yok. Unknown = sistem bu tag ID'sini hiç bilmiyor (hard-delete edilmiş veya hiç eşleşmemiş).

---

# 20. Duplicate / Interaction Handling

Bu, tüm giriş kanallarına eşit şekilde uygulanan **genel bir mimari prensiptir** — yalnızca NFC'ye özgü değildir:

> **Duplicate suppression, input/channel katmanının sorumluluğudur. Event Engine, kendisine ulaşan gerçek bir Event isteğini genel-amaçlı bir debounce ile reddetmez.**

| Kanal | Duplicate / interaction handling sorumluluğu |
|---|---|
| NFC | Fiziksel tag'in kısa sürede tekrar okunmasından kaynaklanan donanımsal duplicate read'lere karşı NFC'ye özel, davranış tipine duyarlı suppression (Moment/Duration/State'te daha sıkı, Counter'da daha toleranslı) |
| Widget | Standart Widget/UI interaction handling |
| Quick Settings | Tile interaction handling |
| App | Standart UI interaction handling |

Kullanıcının bilinçli olarak yaptığı art arda iki aksiyon (örn. Counter'da +1 → +1), hiçbir kanalda tek Event'e indirgenmez. Bu prensip, kanal-bağımsız Event Engine mimarisinin (Section 23) doğal bir sonucudur.

---

# 21. Android Home Widget

Widget, TapLog'un uygulamayı açmadan Event oluşturmasını sağlayan ana giriş kanallarından biridir. Kullanıcı sık kullandığı kayıtları (gerekirse Record + Target kombinasyonu olarak) Home Screen'e koyabilir.

Örnek: ☕ Kahve +1 · 💊 İlacı aldım · 🚗 Park · 💧 Salon'u suladım.

Widget'ın amacı: **App açmadan Event oluşturmak.** Widget üzerindeki etkileşim mümkün olduğunca doğrudan Event oluşturmalıdır.

---

# 22. Quick Settings

TapLog Android Quick Settings Tiles sağlayabilir. Örnek: ☕ Kahve +1 · 💊 İlacı aldım · 🔇 Sessiz · ⏱ 25 dk.

Kullanım amacı: kullanıcının uygulama veya Home Screen'e gitmeden hızlı Event oluşturabilmesi. Tile'ların davranışı Record'un tipine göre değişebilir.

---

# 23. App / Widget / Quick Settings / NFC Relationship

Bu kanallar ayrı ürünler değildir. Hepsi aynı TapLog Event sisteminin farklı giriş noktalarıdır:

* **App** → Kahve'ye dokun
* **Widget** → Kahve +1
* **Quick Settings** → Kahve +1
* **NFC** → Kahve sticker'ına dokun

Sonuç her zaman: **aynı türde TapLog Event oluşturulur.** Kanal yalnızca Event'in nasıl oluşturulduğunu ve geri bildiriminin nasıl sunulduğunu belirtir (bkz. Section 13, 20).

---

# 24. Natural Language Input

TapLog kullanıcıdan doğal dil ile kayıt alabilmelidir.

Örnek: "kahve içtim" · "kahve +1" · "çamaşırı başlattım" · "çamaşırı bitirdim" · "ofisteyim".

Sistem mümkün olduğunda Record, Target, Behavior, Action, Quantity, Unit bilgilerini anlamalı; başlama/bitirme fiilleri ve zaman kipleri (geçmiş zaman vb.) Behavior tespiti için önemli sinyaller olarak değerlendirilmelidir. Record + Event ilişkisi için bkz. Section 9.

---

# 25. AI Strategy

AI, TapLog'un temel çalışma bağımlılığı değildir. V1 yaklaşımı: **deterministic/local parsing first**, cihaz destekliyorsa **on-device AI enhancement**.

Gemini Nano / Android cihaz üzerindeki uygun AI yetenekleri desteklendiğinde kullanılabilir. AI bulunmayan cihazlarda TapLog'un temel Event logging özellikleri çalışmaya devam etmelidir.

---

# 26. Existing Record Recognition

Kullanıcı "kahve içtim" dediğinde sistem, mümkünse yeni bir Record oluşturmak yerine mevcut "☕ Kahve" Record'unu kullanmalıdır. Target'lar da mevcut kayıtlarla eşleştirilmeye çalışılır.

Emin olunamayan durumlarda (örn. "Salon Bitkisi" / "Salon Çiçeği" gibi yakın adlar) kullanıcıya kısa bir seçim/confirmation sunulur. Yanlış eşleşme yapmak yerine kullanıcıdan minimum düzeyde açıklama istemek tercih edilir.

---

# 27. Ambiguous Input

TapLog belirsiz bir ifadeyi sessizce yanlış yorumlamamalıdır. Örneğin "Kahve" ifadesi tek başına Moment, Counter veya mevcut Record'a Event ekleme gibi birden fazla anlama gelebiliyorsa (bkz. Section 9), kullanıcıdan gerekli minimum bilgi alınmalıdır.

Amaç: **AI'ın kullanıcı adına yanlış karar vermesinden kaçınmak.**

---

# 28. Archive

Kullanıcıya görünen normal "Delete" davranışı mümkün olduğunca **Archive** mantığında çalışır. Archive edilen Record:

* aktif listelerden çıkar,
* Home'dan çıkar,
* yeni Event oluşturmak için kullanılmaz,
* geçmiş Event'leri korur.

Archive geri alınabilir.

---

# 29. Archive Screen

V1'de ayrı bir Archive alanı bulunur: Archived Records, Archived Targets. Kullanıcı buradan **Yeniden etkinleştir** veya **Kalıcı olarak sil** işlemlerini gerçekleştirebilir.

---

# 30. Unarchive

Unarchive, Record/Target'ı tekrar ACTIVE hale getirir. Aynı kimlik (`id`) korunur, geçmiş Event'ler korunur. Ancak daha önce ORPHANED olmuş NFC bağlantısı **otomatik olarak geri bağlanmaz** (bkz. Section 18.1).

---

# 31. Permanent Delete

Permanent Delete geri döndürülemez ve Ayarlar/derin yönetim alanında bulunur. Kullanıcı açık şekilde bilgilendirilir:

> "Bu işlem geri alınamaz. 127 Event ve bağlı veriler kalıcı olarak silinecek."

Yüksek miktarda veya hassas veri içeren durumlarda ek confirmation (örn. isim yazdırma) kullanılabilir. Kalıcı silme; ilgili Record/Target, Event geçmişi, NFC binding kaydı ve metadata'yı birlikte kaldırır (bkz. Section 19, Unknown NFC).

---

# 32. Duration Event Integrity

Bir Duration Event başladıktan sonra Record veya Target arşivlenir/silinirse Event geçmişi korunur. Event, OPEN durumunda kalıp **INCOMPLETE** haline gelebilir.

INCOMPLETE terminal bir durumdur. TapLog gerçek bir bitiş zamanı bilmiyorsa herhangi bir bitiş zamanı uydurmaz. Record daha sonra tekrar aktif edilse bile eski INCOMPLETE Event yeniden OPEN haline gelmez.

---

# 33. Statistics

V1'de Event verileri üzerinden temel ve Pro istatistikler sağlanabilir.

| İstatistik | INCOMPLETE dahil mi? |
|---|---|
| Toplam Event sayısı | ✅ Dahil |
| Başlatma sayısı | ✅ Dahil |
| Başlatma sıklığı | ✅ Dahil |
| Tamamlanma sayısı | ❌ Hariç |
| Tamamlanma sıklığı | ❌ Hariç |
| Ortalama Duration | ❌ Hariç |
| Toplam Duration | ❌ Hariç |

Bir aktivite başlatılmış ama tamamlanmamışsa, "kaç kez başlatıldı" istatistiğine dahil edilir; "ortalama ne kadar sürdü" istatistiğine dahil edilmez.

---

# 34. Local-First Architecture — Product Requirement

V1'de kullanıcı hesabı veya zorunlu sunucu bağlantısı bulunmaz. TapLog **local-first** çalışır. Kullanıcı, temel Event logging için internet bağlantısına ihtiyaç duymaz. Kişisel veriler varsayılan olarak cihazda tutulur.

---

# 35. Backup / Export

V1'de manuel backup/export bulunur. Backup **encrypted** olmalıdır; kullanıcı backup için kendi şifresini belirler. Şifre kaybedilirse TapLog backup şifresini geri getiremez — bu, export ekranında açıkça belirtilir. Backup dosyası formatı: `.tpb`.

Backup; Record tanımlarını, Target'ları, Event'leri, NFC mapping'lerini ve kullanıcı ayarlarını kapsar.

---

# 36. Import

V1 import davranışı: **Overwrite.** Import sırasında mevcut verilerin değiştirileceği/silineceği kullanıcıya açıkça belirtilir. V1'de merge, conflict resolution veya gelişmiş veri birleştirme bulunmaz.

---

# 37. Privacy

TapLog'un temel yaklaşımı: **Personal data should remain personal.**

V1'de cloud account zorunluluğu yoktur. Kullanıcıların ilaç, günlük alışkanlık, lokasyon, kişisel aktiviteler gibi hassas olabilecek Event'leri kaydedebileceği dikkate alınmalıdır. AI özelliği de temel kullanım için zorunlu bir cloud AI hesabı gerektirmez.

---

# 38. Free vs Pro

V1 monetization modeli: **Free Core + One-Time Pro Unlock.** Core özellikler subscription arkasına konulmaz.

**Free:** Record · Event · Timeline · temel statistics · App · Widget · Quick Settings · NFC · 5 aktif NFC Button · Action Button · Target Button · deterministic/local parser · desteklenen cihazlarda on-device AI · Undo · Edit · Delete/Archive · Target · encrypted backup/export.

**Pro (tek seferlik):** Unlimited NFC · Advanced Statistics · CSV/PDF Export · Advanced Filtering · Advanced Dashboard · Advanced Personalization · gelecekte gelişmiş local intelligence özellikleri.

---

# 39. Subscription

V1'de core özellikler için subscription bulunmaz. Gelecekte subscription, ancak sürekli maliyet oluşturan servisler (Cloud Sync, Family/Shared Spaces, Cloud Backup, gelişmiş online servisler) geldiğinde değerlendirilebilir.

---

# 40. Future Scope

V1'in parçası olmayan ama ürün vizyonunda yer alabilecekler:

* **Wear OS** — saat üzerinden Event logging.
* **Voice Assistant** — örn. "Hey Google, kahve içtim."
* **Health Connect** — ileride veri okuma/yazma.
* **Tasker / MacroDroid** — Event hooks / automation integrations.
* **Smart Nudges** — örn. "İlacını genellikle bu saatte alıyorsun." (varsayılan olarak agresif olmaz).
* **Physical NFC Kits** — Home Kit, Plant Kit, Baby Kit, Medication Kit gibi tematik sticker paketleri (V1'de fiziksel ürün envanteri zorunlu değil).
* **iOS** — V1 kapsamı dışında.

---

# 41. V1 Non-Goals

* Sosyal ağ / Public profiles
* Karmaşık habit tracking sistemi
* Zorunlu cloud account / internet bağlantısı
* Cloud AI dependency
* Family sharing / Cloud synchronization
* Wear OS / iOS
* Gelişmiş automation engine / Tasker-MacroDroid native integration
* Health Connect integration
* Fiziksel NFC ürün satışı için operasyon altyapısı

---

# 42. Core UX Principles

1. **One Tap First** — en sık kullanılan işlemler mümkünse tek dokunuşla yapılmalıdır.
2. **Don't Make Users Configure Twice** — kullanıcı aynı bilgiyi tekrar girmek zorunda kalmaz.
3. **Event First** — ürün Event oluşturmayı kolaylaştırmalıdır.
4. **NFC Is an Input, Not the Product** — NFC önemli bir kanaldır ama TapLog'un temel ürünü Event logging'dir.
5. **Local First** — temel kullanım internet veya hesap gerektirmez.
6. **Don't Guess Wrong** — belirsiz durumlarda yanlış otomasyon yerine minimum confirmation tercih edilir.
7. **History Is Sacred** — geçmiş Event verileri, kullanıcı açıkça kalıcı silme yapmadıkça korunur.
8. **Fast Correction** — yanlış Event kolayca düzeltilmelidir.
9. **Simple by Default** — gelişmiş özellikler temel kullanıcı için karmaşıklık yaratmamalıdır.
10. **Honest Promises** — ürün, platform/donanım kısıtlarının garanti edemeyeceği davranışları (örn. koşulsuz NFC çalışması) vaat etmez; gerçekçi ve doğrulanabilir ifadeler kullanır (bkz. Section 14.1).

---

# 43. V1 Acceptance Criteria

**Event**
* Kullanıcı Record oluşturabilir. Kullanıcı Event oluşturabilir.
* Event timestamp içerir, geçmişte görüntülenebilir, düzenlenebilir/silinebilir.
* Geçmiş zaman/tamamlanmış eylem bildiren doğal dil girdisi, Record yoksa Record + ilk Event'i birlikte oluşturur (Section 9).
* Yalnızca isim içeren belirsiz girdi, kullanıcıya kayıt/olay ayrımını sorar.

**Target**
* Target bağımsız oluşturulabilir ve birden fazla Record ile ilişkilendirilebilir.
* Record, Target olmadan kullanılabilir. Target bağı (unlink) kaldırılabilir.
* Target silindiğinde/arşivlendiğinde geçmiş Event verileri korunur.

**Behavior**
* Moment, Counter, Duration, State desteklenir.
* Duration OPEN → COMPLETED akışı çalışır; tamamlanamayan Duration Event INCOMPLETE olabilir ve terminal kalır.

**NFC**
* Action Button ve Target Button oluşturulabilir.
* Free kullanıcı 5 aktif NFC Button kullanabilir; 6.'sı için Pro gerekir; orphaned button'lar limite dahil değildir.
* Record/Target archive edildiğinde bağlı NFC ORPHANED olur ve son bilinen bağlantı bilgisiyle yeniden bağlanma seçeneği sunar (Section 18.1).
* Aktif Target'ın aktif Record'u kalmadığında NFC ORPHANED olmaz; "Kayıt oluştur" seçeneği sunulur (Section 18.2).
* Hard-delete edilmiş NFC'ye tekrar dokunmak Unknown/yeni tag olarak ele alınır.
* Her kanal kendi duplicate/interaction handling'inden sorumludur; Event Engine genel debounce uygulamaz (Section 20).
* İlk NFC kurulumunda gerekli izin/erişim onboarding'i sunulur; ürün koşulsuz çalışma vaadi vermez (Section 14.1, 16).

**App / Widget / Quick Settings**
* Ana Home ekranından hızlı Event oluşturulabilir; Record/Target archive edilebilir ve geri alınabilir.
* Permanent Delete açık confirmation gerektirir.
* Widget ve Quick Settings üzerinden mümkün olduğunca doğrudan Event oluşturulur, kullanıcı hızlı feedback alır.

**AI**
* AI bulunmasa da temel uygulama çalışır; deterministic parsing her zaman kullanılabilir.
* Desteklenen cihazlarda on-device AI kullanılabilir.
* Belirsiz ifadeler sessizce yanlış uygulanmaz; mevcut Record/Target'lar mümkün olduğunda tekrar kullanılır.

**Data**
* Temel kullanım local-first çalışır. Backup encrypted'dır. Import, mevcut verinin overwrite edileceğini açıkça belirtir.

---

# 44. Technical TBD

Bu PRD'nin dışında bırakılan, ayrı bir **Technical Design** aşamasında kararlaştırılacak konular:

* Database schema / entity-tablo tasarımı
* Android architecture, Event Engine implementation
* Exact NFC NDEF implementation
* **Widget ve Quick Settings binding'lerinin NFC'dekine benzer bir ORPHANED/lifecycle durumuna sahip olup olmayacağı** — henüz ürün kararı verilmedi, unutulmaması için burada tutuluyor.
* Quick Settings TileService implementation
* Gemini Nano entegrasyon mimarisi
* Parser algoritması ve fuzzy matching algoritması
* Notification implementation, background execution ayrıntıları
* Exact encryption implementation, backup file internals
* UI component architecture, test architecture

---

# 45. V1 Product Definition

> **TapLog, kullanıcının gerçek hayatta gerçekleşen olayları App, Widget, Quick Settings veya NFC üzerinden mümkün olan en düşük sürtünmeyle kaydetmesini sağlayan local-first Android event logging ürünüdür.**

Ürünün temel yapısı:

* **Record** → neyi takip ediyorum?
* **Target** → neyle/kimle ilişkili?
* **Behavior** → nasıl gerçekleşiyor?
* **Event** → ne zaman gerçekleşti?
* **Input Channel** → kullanıcı bunu nasıl kaydetti?

Bu beş kavram TapLog V1'in ürün temelini oluşturur. NFC, Widget, Quick Settings ve App ise aynı Event sisteminin farklı giriş kapılarıdır.
