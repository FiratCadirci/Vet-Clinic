# Kabul ve Bekleme – Frontend API Contract

Bu sözleşme mevcut `AppointmentController`, `AppointmentService`, `EncounterController`, `EncounterService`, DTO ve repository kodlarına göre hazırlanmıştır. Yeni endpoint veya veritabanı değişikliği içermez.

## 1. Kapsam ve sınırlar

Ekran randevulu hastanın gelişini kaydeder, bekleyenleri ve muayenedekileri gösterir. **Bekleme kaydı randevunun `CHECKED_IN` durumudur; ayrı bir kuyruk API'si yoktur.**

| İşlev | Mevcut destek |
|---|---|
| Günlük randevuları listele, onayla, geldi işaretle, iptal/no-show | Var (`appointments.manage`) |
| Bekleyenler | `GET /appointments?status=CHECKED_IN` |
| Muayenedekiler (randevulu) | `GET /appointments?status=IN_PROGRESS` |
| Muayene başlat | Var; klinik yazma yetkisi **ve aktif hekim profili** gerekir |
| Randevusuz hastayı doğrudan muayeneye al | Var; resepsiyon kabulü değil, hekim tarafından muayene açmadır |
| Randevusuz hastayı resepsiyondan beklemeye al | Ayrı walk-in kabul API'si yok; aşağıdaki iki aşamalı randevu akışı kullanılabilir |
| Gerçek geliş saati, bekleme süresi, sıra no, öncelik, oda, çağır/geri çağır | API'de yok |
| Hekime atama/değiştirme (bekleme sırasında), sürükle-bırak sıra | API'de yok |

`checkedInAt`, `waitingMinutes`, `queueNumber`, `priority`, `roomId`, `calledAt` veya `encounterId` alanlarını `Appointment` yanıtında varmış gibi kullanmayın. `updatedAt`, geliş saati değildir. Tarayıcıda tutulan saatleri/sıraları kalıcı klinik kuyruğu gibi sunmayın.

## 2. Ortak kurallar ve yetkiler

- Base path `/api/v1`; JSON response sarmalayıcısızdır.
- `Authorization: Bearer <accessToken>` kullanın. Organizasyon JWT'den gelir; body/query'ye `organizationId` eklemeyin.
- Ortak auth istemcisini ve şube seçiciyi yeniden kullanın. Bu ekranda **okuma ve yazmada** `X-Clinic-Id: <aktif şube UUID>` gönderin; yazma endpoint'lerinde zorunludur.
- RLS aynı klinik grubunun diğer şubelerini de görünür kılabilir. Yalnız aktif şubenin kabul ekranını göstermek için appointment listesine ayrıca **`clinicId=<aktif şube UUID>` query filtresi** ekleyin. Bu desteklenen mevcut parametredir; header tek başına şube listesi filtresi değildir.
- Cache anahtarlarına oturum/organizasyon ve şubeyi dahil edin. Şube değişince açık formu kapatın veya tekrar onay alın, önceki istekleri iptal edin ve eski şubenin yanıtını yeni ekrana yazmayın.
- Şube erişimi/işlem yetkisi backend'de yeniden denetlenir; UI gizlemesi güvenlik kontrolünün yerine geçmez.
- Oturumdaki `permissions` listesi şubelerden gelen izinlerin birleşimi olabilir; seçili şubeye özel bir yetki listesi gibi yorumlamayın.

| İşlem | Yetki / ek koşul |
|---|---|
| Randevu listesi, detay, oluşturma, onay, check-in, iptal, no-show | `appointments.manage` |
| Müşteri/hasta arama ve detay | `clients.read` |
| Müşteri/hasta oluşturma | `clients.write` |
| Randevudan `PATCH /start` | `appointments.manage` + `clinical.write` + oturumdaki kullanıcının aktif hekim profili |
| `POST /encounters` ile randevusuz muayene | `clinical.write` + oturumdaki kullanıcının aktif hekim profili |
| Muayene okuma / klinik özet | `clinical.read` |

Resepsiyon kullanıcılarına otomatik olarak hekim işlemleri göstermeyin. Hekim profili bilgisi mevcut oturum modelinde yoksa yeni `/me/veterinarian` endpoint'i varsaymayın; sadece izin kontrolü profil varlığını garanti etmez. Backend'in `422` profil hatasını kullanıcıya gösterin. Bu ekranda muayene başlatmayı hekim oturumuna bırakmak mümkündür.

## 3. Ekran ve liste sorguları

Önerilen sekmeler: **Gelecek/Gelmesi beklenen**, **Bekleyen**, **Muayenede**, **Tamamlanan**. Tarih seçici ilk sekme ve tamamlananlar için çalışır. İptal/no-show kayıtları ayrı durum filtresiyle incelenebilir.

| Liste | Query |
|---|---|
| Seçili günün gelmesi beklenenleri | Gün aralığı + `status=REQUESTED&status=BOOKED&status=CONFIRMED` |
| Şubede hâlâ bekleyen tüm kayıtlar | **Tarih sınırı olmadan** `status=CHECKED_IN` |
| Randevulu, muayenesi süren tüm kayıtlar | **Tarih sınırı olmadan** `status=IN_PROGRESS` |
| Seçili gün aralığıyla kesişen tamamlanan randevular | Gün aralığı + `status=COMPLETED` |

Bekleyen/muayenede listelerinde tarih sınırı kullanmamak, randevu saati dün olan ancak henüz kapanmamış kayıtların kaybolmasını önler. Günlük listeye ayrıca `CHECKED_IN/IN_PROGRESS` dahil edilirse bunları aynı ekranda iki kez saymayın.

Gün sorgusu örneği (tarih örnektir; gerçek seçili gün Europe/Istanbul'da hesaplanır):

```http
GET /api/v1/appointments?clinicId=22222222-2222-4222-8222-222222222222&startDateTime=2032-05-03T00%3A00%3A00%2B03%3A00&endDateTime=2032-05-04T00%3A00%3A00%2B03%3A00&status=REQUESTED&status=BOOKED&status=CONFIRMED&page=0&size=20&sort=startsAt,asc
```

Bekleyen sorgusu:

```http
GET /api/v1/appointments?clinicId=22222222-2222-4222-8222-222222222222&status=CHECKED_IN&page=0&size=20&sort=startsAt,asc
```

Her ikisinde de Authorization ve aktif şube header'larını gönderin.

### Filtreler, sayfalama, sayaçlar

- Desteklenen ilgili filtreler: `clinicId`, `vetId`, tekrarlanabilir `status`, `appointmentType`, `bookingChannel`, `animalId`, `ownerId`, `startDateTime`, `endDateTime`.
- `vetId`, **veteriner profil UUID'sidir**; `userId` değildir. Appointment listesinde `q` parametresi yoktur. Ad/telefon/çip araması için mevcut müşteri/hasta aramasını kullanıp seçilen `animalId` veya `ownerId` ile listeyi filtreleyin.
- Pagination 0-tabanlı, varsayılan `size=20`, max `200`. Yanıt: `{ content, page, size, totalElements, totalPages, first, last }`.
- Sekmelerin sayacı kendi sorgusunun `totalElements` değeridir; tek sayfanın `content.length` değeri toplam değildir. Hekim/arama filtresi aktifse sayacın da o filtreye ait olduğunu belirtin.
- Kart/kolon görünümünde tüm veriyi gösterecekseniz tüm sayfaları yükleyin; sadece ilk 200 kaydı tüm kuyruk gibi göstermeyin. Kısmi yüklemeyi belirtin.
- Sıralama `sort=startsAt,asc`; bu **randevu saatine göre** sıralamadır, geliş sırası/FIFO değildir. UI'da "Randevu saatine göre" yazın.
- Gün aralığı yarım açık kesişimdir: `endsAt > startDateTime && startsAt < endDateTime`. "Bugün tamamlanan" sayısı gerçek kapanma gününe göre rapor değildir; appointment sorgusu randevu zamanını filtreler.
- Offset'li ISO tarih gönderin. `+03:00` query'de URL-encode edilmelidir; `URLSearchParams` kullanın.

### Kart alanları

`Appointment` modeli [APPOINTMENTS_API_CONTRACT.md](APPOINTMENTS_API_CONTRACT.md) §3 ile aynıdır; mevcut frontend modelini yeniden kullanın. Alanlar:

```ts
export interface AdmissionAppointment {
  id: string;
  appointmentNo: string;
  clinicId: string;
  clientId: string;
  clientName: string;
  animalId: string;
  animalName: string;
  patientNo: string;
  veterinarianId: string | null;
  veterinarianName: string | null;
  appointmentType:
    | "EXAM" | "VACCINATION" | "SURGERY" | "FOLLOW_UP"
    | "GROOMING" | "LAB" | "IMAGING" | "OTHER";
  status:
    | "REQUESTED" | "BOOKED" | "CONFIRMED" | "CHECKED_IN"
    | "IN_PROGRESS" | "COMPLETED" | "CANCELLED" | "NO_SHOW";
  startsAt: string;
  endsAt: string;
  reason: string | null;
  internalNotes: string | null;
  bookingChannel: "STAFF" | "PHONE" | "WEB" | "MOBILE" | "WHATSAPP" | "OTHER";
  cancelledAt: string | null;
  cancellationReason: string | null;
  createdAt: string;
  updatedAt: string;
}
```

Appointment timestamp'leri ISO-8601 offset biçimindedir. Kartta hayvan/hasta no, sahip, randevu no, saat, tür, hekim ve durum gösterin. Hekim null ise "Hekim atanmamış" gösterin. Telefon, tür/ırk ve alerji appointment DTO'sunda yoktur; gerekirse detay paneli açıldığında ilgili API'den, uygun yetkiyle yükleyin. Her satır için ek istek atmayın.

### Hekim görünümü

Yüklenen appointment'lardaki `veterinarianId/veterinarianName` ile gruplama yapılabilir; null değerler "Atanmamış" grubunda kalır. Bu, bütün aktif hekimlerin dizini değildir. Resepsiyona açık genel `GET /veterinarians` listesi mevcut değildir. `/users` ve `/users/{id}/veterinarian` uçları `users.manage` gerektirir; sırf filtre doldurmak için bu yetkiyi istemeyin.

## 4. Randevulu hasta kabulü

| Mevcut durum | Bu ekrandaki eylem | API |
|---|---|---|
| `REQUESTED` | Onayla, iptal | `PATCH /appointments/{id}/confirm` veya `/cancel` |
| `BOOKED`, `CONFIRMED` | Geldi, gelmedi, iptal | `PATCH /appointments/{id}/check-in`, `/no-show`, `/cancel` |
| `CHECKED_IN` | Bekliyor; yetkili hekim muayene başlatabilir; iptal mümkündür | `PATCH /appointments/{id}/start` veya `/cancel` |
| `IN_PROGRESS` | Muayenede; yetkisi varsa klinik kaydı aç | Salt okunur özet/detay; resepsiyondan tamamlama yok |
| `COMPLETED`, `CANCELLED`, `NO_SHOW` | Salt okunur | Yeni durum eylemi yok |

`REQUESTED` kaydını önce onaylayın; tek check-in isteğiyle kabul edilemez. `BOOKED` için önce onay zorunlu değildir.

**Geldi:**

```http
PATCH /api/v1/appointments/{appointmentId}/check-in
Authorization: Bearer <accessToken>
X-Clinic-Id: <aktif şube UUID>
```

Body yoktur. `200` yanıtı tam `Appointment` nesnesidir, `status=CHECKED_IN`. Check-in şikâyet, hekim, öncelik veya geliş saati almaz. Check-in geri alma endpoint'i yoktur; sahte genel `PATCH /appointments/{id}` kullanmayın.

**İptal:** `PATCH /appointments/{id}/cancel`, body `{ "cancellationReason": "Hasta sahibi ayrıldı." }`; sebep boş olamaz, en fazla 500 karakter. **Gelmedi:** `PATCH /appointments/{id}/no-show`, body yok. İkisi de `200 Appointment` döndürür; `CHECKED_IN` durumunda no-show geçerli değildir.

**Muayene başlat (hekim):**

```http
PATCH /api/v1/appointments/{appointmentId}/start
Authorization: Bearer <accessToken>
X-Clinic-Id: <aktif şube UUID>
Content-Type: application/json

{ "chiefComplaint": "İştahsızlık", "history": "İki gündür devam ediyor." }
```

Başlatma `CONFIRMED` veya `CHECKED_IN` durumunda mümkündür. `chiefComplaint` max 4000, `history` max 8000 karakterdir. Body opsiyoneldir; şikâyet boş/eksikse appointment `reason` kullanılır. İkisi de boşsa `422` döner. Frontend başlatma formunda anlamlı bir şikâyeti zorunlu tutsun.

Başarılı yanıt **`200 EncounterResponse`**, Appointment değildir. `response.id` muayene kimliğidir. Randevu `IN_PROGRESS`, encounter `ADMISSION` olur. Muayenenin hekimi request'ten seçilmez: **oturumdaki aktif hekim profilidir**; randevunun planlanan hekimiyle farklı olabilir. Başlatmadan sonra appointment listelerini yeniden yükleyin.

Başlatma yanıtında bu ekranın kullandığı alanların projection'ı (tam DTO değildir):

```ts
export interface AdmissionEncounterProjection {
  id: string;
  encounterNo: string;
  animalId: string;
  animalName: string;
  patientNo: string;
  clinicId: string;
  appointmentId: string | null;
  attendingVetId: string | null;
  encounterType: string;
  status: "ADMISSION" | "EXAMINATION" | "DIAGNOSIS" | "PLAN" | "CLOSED";
  startedAt: string;
  endedAt: string | null;
  chiefComplaint: string | null;
}
```

Encounter yanıtı ayrıca `history`, `examinationSummary`, `assessment`, `treatmentPlan`, `vetNotes`, `followUpAt`, `createdBy`, `createdAt`, `updatedAt`, `addenda`, `treatments` içerir. Ortak Encounter modeliniz varsa onu yeniden kullanın. **Encounter tarihleri** global serializer nedeniyle `yyyy-MM-dd HH:mm` Europe/Istanbul biçimindedir; appointment ISO parser'ıyla veya `new Date("yyyy-MM-dd HH:mm")` ile tarayıcıya bağlı ayrıştırmayın.

Muayene kimliği önceden bilinmiyorsa `clinical.read` ile `GET /appointments/{id}/clinical-summary` kullanılabilir: `{ appointmentId, encounters: [{ encounter, vaccinations }] }`. Tek encounter varmış gibi ilk kaydı körlemesine açmayın; açık kaydı seçin veya geçmiş kayıt seçimi gösterin. Kimlik biliniyorsa `GET /encounters/{id}` kullanılır. Klinik okuma yetkisi olmayan resepsiyonda bu istekleri göndermeyin.

## 5. Randevusuz hasta

### Resepsiyonun beklemeye alma akışı

Ayrı walk-in kuyruğu olmadığı için mevcut randevu API'siyle uygulanabilir:

1. `clients.read` ile müşteri/hasta seçin. Yeni kayıt gerekiyorsa `clients.write` ile mevcut kayıt ekranlarını kullanın; sözleşme: [CLIENTS_ANIMALS_CONTRACT.md](CLIENTS_ANIMALS_CONTRACT.md).
2. Kullanıcıdan başlangıç/bitiş, randevu türü ve geliş nedenini alın. `POST /appointments` ile **gerçek bir randevu** oluşturun. `bookingChannel="STAFF"` kullanın; `WALK_IN` desteklenen kanal değildir. Hekim opsiyoneldir; hekim listesi varsaymayın.
3. Dönen `id` için `PATCH /appointments/{id}/check-in` çalıştırın. Böylece bekleyenlere girer.

Create request örneği:

```json
{
  "animalId": "44444444-4444-4444-8444-444444444444",
  "clientId": "33333333-3333-4333-8333-333333333333",
  "appointmentType": "EXAM",
  "startsAt": "2032-05-03T10:00:00+03:00",
  "endsAt": "2032-05-03T10:30:00+03:00",
  "reason": "Randevusuz geliş: iştahsızlık",
  "bookingChannel": "STAFF"
}
```

`startsAt/endsAt` kullanıcı seçimine aittir; otomatik sabit süreyle gizlice slot üretmeyin. Bu yol randevu çakışma ve varsa hekim çalışma saati kurallarına tabidir; DB sonucunu esas alın. `reason`/`internalNotes` max 2000 karakterdir. `clientId` verilmezse güncel sahip backend'de çözülür.

**İki istek atomik değildir.** Oluşturma `201` ile başarılı olup check-in başarısız olursa "Randevu oluşturuldu, kabul tamamlanamadı" gösterin; oluşan `id`'yi koruyun, detayını yeniden okuyun ve hâlâ `BOOKED/CONFIRMED` ise yalnız check-in'i tekrar deneyin. Aynı formu yeniden POST edip mükerrer randevu oluşturmayın. Bağlantı kesilmesinde yazma isteğini körlemesine tekrar göndermeyin.

Bu kayıt sonradan güvenilir biçimde `walk-in` olarak filtrelenemez; açıklama metninden resmi bir kabul türü/istatistik türetmeyin. `EMERGENCY` appointmentType değildir; bu ekranda acil öncelik alanı bulunmaz.

### Hekimin doğrudan randevusuz muayene açması

`POST /encounters` (`201 EncounterResponse`):

```json
{
  "animalId": "44444444-4444-4444-8444-444444444444",
  "encounterType": "CONSULTATION",
  "chiefComplaint": "İştahsızlık",
  "history": null
}
```

`encounterType`: `CONSULTATION`, `EMERGENCY`, `VACCINATION`, `SURGERY`, `FOLLOW_UP`, `TELEMEDICINE`, `OTHER`; null/eksikse `CONSULTATION`. `chiefComplaint` iş kuralı olarak zorunlu, max 4000; `history` max 8000. `X-Clinic-Id`, `clinical.write` ve aktif hekim profili gerekir. Request `veterinarianId`, `clientId` veya `appointmentId` almaz.

Bu işlem doğrudan `ADMISSION` muayenesi açar, **bekleme kuyruğu oluşturmaz** ve appointment `IN_PROGRESS` listesinde görünmez. Bu yüzden ilk sürümde "Muayenede" sekmesini **randevulu muayeneler** olarak etiketleyin.

Tüm randevusuz muayeneleri de birleştiren operasyon panosu bu ilk sürümün dışındadır: `GET /encounters` yalnız `animalId`, tek `status`, `page`, `size` filtrelerini destekler; sunucuda tarih/clinicId/hekim filtresi yoktur. `clinical.read` gerekir ve klinik grubu kayıtlarını döndürebilir. Varlığı olmayan filtrelerle sorgulayıp sonuçların yalnız aktif şubeye ait olduğunu varsaymayın.

## 6. Yenileme, hatalar ve eşzamanlı kullanım

- Manuel yenileme sağlayın. Mevcut altyapı destekliyorsa görünür sekmede 30 saniyelik polling yapın; gizli sekmede durdurun. SSE/WebSocket endpoint'i varsaymayın.
- Mutasyon sırasında satırın eylemlerini kilitleyin. Başarı cevabından önce hasta durumunu kesinleşmiş gibi değiştirmeyin. Sonrasında sekmeleri ve sayaçları yeniden sorgulayın.
- Check-in tekrar çağrısı idempotent değildir; başka resepsiyon kullanıcısı işlemi yaptıysa `422` alınabilir. Hata mesajını gösterip ilgili kaydı/listeyi yeniden okuyun.
- Yenileme hatasında eski veriyi "Güncellenemedi" ve son başarılı yenileme zamanı ile gösterin; boş kuyruk/sıfır sayaç gibi sunmayın.

```ts
export interface ApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  details?: Record<string, unknown>;
}
```

| HTTP / code | Frontend davranışı |
|---|---|
| `400 VALIDATION_ERROR`, `MALFORMED_REQUEST`, `BAD_REQUEST`, `MISSING_HEADER` | Alan/parametre hatasını göster; eksik şube bağlamında formu durdur |
| `401 UNAUTHORIZED` | Ortak auth akışını kullan; paralel refresh çağrıları yapma |
| `403 FORBIDDEN`, `PASSWORD_CHANGE_REQUIRED` | Yetki/şube/şifre değişimi akışını uygula; boş listeye çevirme |
| `404 NOT_FOUND` | Kayıt yok veya erişim dışı; paneli kapat ve listeyi yenile |
| `409 CONFLICT`, `DB_CONFLICT` | Eşzamanlı işlem/açık muayene çakışması; güncel kaydı getir |
| `409 APPOINTMENT_TIME_CONFLICT` | Randevusuz kabul için oluşturulan slot dolu; farklı saat seçtir |
| `422 BUSINESS_RULE_VIOLATION`, `DB_RULE_VIOLATION`, `VETERINARIAN_OUTSIDE_WORKING_HOURS`, `INVALID_APPOINTMENT_TIME_RANGE` | `message` göster; durum/profil/şikâyet/saat sorununu düzelt |

SQL veya hassas DB ayrıntılarını arayüzde göstermeyin. Auth davranışı: [AUTH_API_CONTRACT.md](AUTH_API_CONTRACT.md).

## 7. Frontend uygulama talimatı ve kabul ölçütleri

Bu sözleşmeyi kullanarak mevcut resepsiyon menüsüne Kabul/Bekleme ekranını entegre edin. Var olan route, auth/session, API client, cache, form ve modal bileşenlerini yeniden kullanın. Yeni API veya kütüphane varsaymayın. Nötr gri/siyah yüzey ve tipografi tercih edin; durumlar yalnız renkle anlatılmasın, metin rozeti de bulunsun.

- Günlük beklenenler, tarih sınırı olmayan bekleyenler ve randevulu muayenedekiler ayrı sorgulansın; tüm sorgular aktif şube filtresi taşısın.
- Tarih, hekim (mevcut veriden), durum ve hasta/sahip seçimi doğru query parametrelerine bağlansın; sayfalama ve filtreli toplamlar tutarlı olsun.
- "Geldi", onay, iptal/no-show yalnız geçerli durumlarda gösterilsin; `CHECKED_IN` kaydına tekrar "Geldi" gösterilmesin.
- Muayene başlatma hekim işlemi olarak ayrılsın; resepsiyon kullanıcısına klinik yetki verilmeden ekran çalışsın. `response.id` muayene kimliği olarak kullanılsın.
- Randevusuz kabulün iki aşamalı başarı/başarısızlığı görünür olsun; retry yeni randevu oluşturmasın.
- Gerçek geliş saati, bekleme dakikası, FIFO sıra, acil öncelik, oda ve çağırma eylemleri uydurulmasın.
- Loading/empty/error/forbidden durumları ayrılsın; hata boş kuyruk gibi görünmesin. Şube değişimi ve geciken yanıtlar eski veriyi göstermesin.
- Sadece ilk sayfayı yükleyen UI toplamı `content.length` ile göstermesin; gece yarısını geçen bekleyen kayıt görünür kalsın.
- Klavye erişimi, form etiketleri, modal focus yönetimi ve dar ekran kart görünümü sağlansın.

Bu belge yalnız frontend entegrasyon sözleşmesidir; backend davranışını veya şemayı değiştirmez.
