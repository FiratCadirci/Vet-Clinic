# Randevu Takvimi – Frontend API Contract

Bu sözleşme `AppointmentController`, appointment DTO'ları, `PageResponse`, `ApiError`, global hata eşlemesi ve PostgreSQL V1 şemasına göre hazırlanmıştır. Takvim entegrasyonunda bu dosya backend alanları için tek referanstır.

## 1. Ortak kurallar

| Konu | Sözleşme |
|---|---|
| Base path | `/api/v1` |
| Kimlik doğrulama | `Authorization: Bearer <accessToken>` |
| Organizasyon | Access token/JWT bağlamından gelir; request'e `organizationId` eklenmez |
| Şube | Yazma isteklerinde `X-Clinic-Id: <clinic UUID>` zorunludur |
| Yetki | Liste, detay ve randevu yönetimi: `appointments.manage`; `/start` ve `/complete` ayrıca `clinical.write` gerektirir |
| İçerik | JSON; yanıtlar ham JSON'dır, genel bir `data` sarmalayıcısı yoktur |
| Tarih-saat | Randevu DTO'larında ISO-8601 offset biçimi (`2032-05-03T09:30:00+03:00`). Uygulama Europe/Istanbul saatine çevirip korur. İsteklerde offset göndermek zorunlu kabul edilmelidir. |
| Eski tarih biçimi | Tarih-saat parser'ı geriye uyumluluk için `yyyy-MM-dd HH:mm` değerini de okur; yeni istemciler bunu kullanmamalıdır. |
| Liste aralığı | Yarım açık kesişim: `appointment.endsAt > startDateTime && appointment.startsAt < endDateTime`. Bitiş sınırında başlayan veya başlangıç sınırında biten randevu dahil edilmez. |
| Zaman dilimi | API'de UTC veya başka offset ile gelen an Türkiye yerel saat diliminde yanıtlanır; istemci saat dilimini kendi başına değiştirmemelidir. |

`X-Clinic-Id` yazma bağlamıdır; organizasyon başlığı değildir. Tenant izolasyonu JWT ve PostgreSQL RLS ile yapılır. Kullanıcının erişmediği klinik/organizasyondaki kayıt RLS nedeniyle yokmuş gibi davranır (`404`). Aynı klinik grubunda görünürlük proje RLS politikasına tabidir.

## 2. Endpoint tablosu

Tüm yolların `/api/v1` öneki vardır. Yazma endpoint'lerinde `appointments.manage` ve `X-Clinic-Id` zorunludur.

| Method | URL | Açıklama | Query / request body | Başarılı response | HTTP / hata senaryoları |
|---|---|---|---|---|---|
| GET | `/appointments` | Takvim randevu listesi; seçilen aralıkla kesişenleri döndürür | Query: `startDateTime`, `endDateTime`, `vetId`, tekrarlanabilir `status`, `appointmentType`, `bookingChannel`, `animalId`, `ownerId`, `page`, `size`, `sort`; eski alias'lar aşağıda | `PaginatedResponse<AppointmentListItem>` | `200`; `400 BAD_REQUEST` geçersiz tarih/filtre/sort; `401`, `403` |
| GET | `/appointments/{id}` | DTO tabanlı detay | UUID path; body yok | `AppointmentDetail` | `200`; `401`, `403`, `404 NOT_FOUND` |
| POST | `/appointments` | Randevu oluşturur; durum `BOOKED` | `AppointmentCreateRequest` | `Appointment` (`201`) | `400 VALIDATION_ERROR` / `MALFORMED_REQUEST`; `401`, `403`, `404`; `409 APPOINTMENT_TIME_CONFLICT`; `409 PATIENT_DECEASED` vefat etmiş hasta; `422` çalışma saati/iş kuralı |
| PATCH | `/appointments/{id}/confirm` | `REQUESTED` veya `BOOKED` → `CONFIRMED` | Body yok | `Appointment` | `200`; `404`; `409 INVALID_STATUS_TRANSITION` geçersiz geçiş |
| PATCH | `/appointments/{id}/cancel` | İptal; `cancellationReason` zorunlu | `AppointmentCancelRequest` | `Appointment` | `200`; `400 VALIDATION_ERROR` sebep eksik; `404`; `409 INVALID_STATUS_TRANSITION` geçersiz geçiş |
| PATCH | `/appointments/{id}/check-in` | `BOOKED` veya `CONFIRMED` → `CHECKED_IN` | Body yok | `Appointment` | `200`; `404`; `409 INVALID_STATUS_TRANSITION` geçersiz geçiş |
| PATCH | `/appointments/{id}/no-show` | `BOOKED` veya `CONFIRMED` → `NO_SHOW` | Body yok | `Appointment` | `200`; `404`; `409 INVALID_STATUS_TRANSITION` geçersiz geçiş |
| PATCH | `/appointments/{id}/reschedule` | İzin verilen bekleyen/onaylı durumdaki randevuyu taşır; yeni slot ve hekim DB tarafından da doğrulanır | `AppointmentRescheduleRequest` | `Appointment` | `200`; `400` hatalı body; `404`; `409 APPOINTMENT_TIME_CONFLICT`; `409 INVALID_STATUS_TRANSITION` geçersiz durum; `422` çalışma saati |
| PATCH | `/appointments/{id}/start` | Bağlı klinik muayenesini başlatır; sadece durum güncellemesi yapmaz | İsteğe bağlı `StartFromAppointmentRequest`: `{ "chiefComplaint": string, "history": string }` | Mevcut `EncounterResponse` | `200`; `401`, `403` (`appointments.manage` + `clinical.write`); `404`; `409` açık muayene/durum çakışması |
| PATCH | `/appointments/{id}/complete` | Tek açık bağlı muayeneyi kurallara göre kapatır; kapatma randevuyu `COMPLETED` yapar | İsteğe bağlı `CloseEncounterRequest`: `{ "vetNotes": string }` | `Appointment` | `200`; `401`, `403` (`appointments.manage` + `clinical.write`); `404`; `409` açık muayene yok/çok veya appointment `IN_PROGRESS` değil; `422` muayene zorunlu alanları eksik |

### Uygun saatler ve form seçenekleri (FG-E2)

| Method | URL | Açıklama | Query | Yanıt | Hatalar |
|---|---|---|---|---|---|
| GET | `/appointments/availability` | Şube + hizmet + hekim + gün için boş başlangıç saatleri | `serviceId` (ertelemede yerine `excludeAppointmentId` verilebilir: süre randevunun kendi süresi), `veterinarianId` (veya `vetId`), `date` (`yyyy-MM-dd`, klinik yerel günü), isteğe bağlı `clinicId` (yoksa `X-Clinic-Id`), `excludeAppointmentId` (ertelemede randevunun kendi saati boş sayılır) | `{ date, clinicId, veterinarianId, serviceId, durationMinutes, slots: [{ startsAt, endsAt, time }] }` | `400` eksik/geçersiz parametre; `403`; `404` hizmet/hekim/şube yok veya erişim dışı; `422` hizmetin süresi tanımsız |
| GET | `/appointments/booking-options` | Randevu formu seçenekleri | isteğe bağlı `clinicId` | `{ services: [{ id, code, name, durationMinutes, price }], veterinarians: [{ id, fullName }] }` | `403`; `404` şube erişim dışı |

Kurallar: başlangıçlar hekimin o şubedeki çalışma penceresinin başından itibaren 15 dakikada bir dizilir, hizmet süresi (`service_catalog.duration_minutes`) pencereye sığmalıdır. İptal ve gelmedi dışındaki randevularla kesişen (organizasyonun tüm şubelerinde, `vet.veterinarian_busy_intervals`), geçmişte kalan ve mesai dışı başlangıçlar listelenmez. Çalışma saati tanımlı olmayan gün boş liste döner. Saatler Europe/Istanbul offset'iyle (`2035-03-06T09:00:00+03:00`) döner. Liste yalnızca yol gösterir: aynı saati eşzamanlı alan iki istekten biri `409 APPOINTMENT_TIME_CONFLICT` alır.

### Geriye uyumluluk

Var olan istemcileri kırmamak için `POST /appointments/{id}/{confirm|cancel|check-in|no-show|reschedule}` yolları da aynı işlemleri yapmaya devam eder. Yeni frontend yalnızca yukarıdaki `PATCH` metotlarını kullanmalıdır. Var olan `POST /appointments/{id}/encounter` endpoint'i de klinik modülündeki mevcut muayene başlatma alternatifi olarak durur (`clinical.write`).

### Liste query parametreleri

| Parametre | Tür | Anlam |
|---|---|---|
| `startDateTime` | ISO-8601 offset date-time | Arama penceresinin dahil olan başlangıcı; appointment bitişi bu andan sonra olmalıdır |
| `endDateTime` | ISO-8601 offset date-time | Arama penceresinin hariç tutulan bitişi; appointment başlangıcı bu andan öncedir |
| `vetId` | UUID | Hekim ID'si |
| `status` | enum, tekrarlanabilir veya virgülle ayrılmış | Bir veya daha fazla durum |
| `appointmentType` | enum | Randevu türü |
| `bookingChannel` | enum | Kayıt kanalı |
| `animalId` | UUID | Hayvan ID'si |
| `ownerId` | UUID | Randevu satırındaki `clientId`/sahip ID'si; hayvanın tüm geçmiş sahipleri üzerinden arama değildir |
| `page` | integer | 0 tabanlı; varsayılan `0` |
| `size` | integer | Varsayılan `20`, üst sınır `200` |
| `sort` | string | Tek alan: `property,asc` veya `property,desc`; desteklenen alanlar `startsAt`, `endsAt`, `appointmentNo`, `status`, `appointmentType`, `bookingChannel`; varsayılan `startsAt,asc` |

Geriye dönük alias'lar: `from` → `startDateTime`, `to` → `endDateTime`, `veterinarianId` → `vetId`, `type` → `appointmentType`, `clientId` → `ownerId`. `clinicId` de eski istemciler için filtre olarak desteklenir. Yeni kod canonical adları kullanmalı; canonical ve eski alan birlikte verilirse canonical değer önceliklidir. Boş veya gönderilmeyen filtre uygulanmaz.

`status` örneği: `?status=BOOKED&status=CONFIRMED`. Virgülle ayrılmış biçim de kabul edilir: `?status=BOOKED,CONFIRMED`. `URLSearchParams.append("status", value)` ile tekrarlamak önerilir.

## 3. TypeScript modelleri

UUID değerleri JSON'da string, nullable alanlar `null` dönebilir. Liste, detay ve create aynı `AppointmentResponse` yapısını kullanır.

```ts
export type UUID = string;

export type AppointmentStatus =
  | "REQUESTED"
  | "BOOKED"
  | "CONFIRMED"
  | "CHECKED_IN"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED"
  | "NO_SHOW";

export type AppointmentType =
  | "EXAM"
  | "VACCINATION"
  | "SURGERY"
  | "FOLLOW_UP"
  | "GROOMING"
  | "LAB"
  | "IMAGING"
  | "OTHER";

// DB CHECK constraint ayrıca OTHER değerini kabul ediyor.
export type BookingChannel =
  | "STAFF"
  | "PHONE"
  | "WEB"
  | "MOBILE"
  | "WHATSAPP"
  | "OTHER";

export interface Appointment {
  id: UUID;
  appointmentNo: string;
  clinicId: UUID;
  clientId: UUID;
  clientName: string;
  animalId: UUID;
  animalName: string;
  patientNo: string;
  veterinarianId: UUID | null;
  veterinarianName: string | null;
  appointmentType: AppointmentType;
  status: AppointmentStatus;
  startsAt: string; // ISO-8601 with Europe/Istanbul offset, e.g. 2032-05-03T09:30:00+03:00
  endsAt: string;
  reason: string | null;
  internalNotes: string | null;
  bookingChannel: BookingChannel;
  cancelledAt: string | null;
  cancellationReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export type AppointmentListItem = Appointment;
export type AppointmentDetail = Appointment;

export interface AppointmentCreateRequest {
  animalId: UUID;
  clientId?: UUID | null; // Omitted/null: current primary owner is resolved by backend
  veterinarianId?: UUID | null;
  appointmentType?: AppointmentType; // default EXAM
  startsAt: string; // ISO-8601 offset date-time
  endsAt: string; // ISO-8601 offset date-time, strictly after startsAt
  reason?: string | null;
  internalNotes?: string | null;
  bookingChannel?: BookingChannel; // default STAFF
}

// General appointment update is not implemented; PATCH /appointments/{id} does not exist.
export interface AppointmentRescheduleRequest {
  startsAt: string;
  endsAt: string;
  veterinarianId?: UUID | null; // null/omitted preserves the current veterinarian; it cannot clear it
}

export interface AppointmentCancelRequest {
  cancellationReason: string; // required, 1..500 non-whitespace characters
}

export interface AppointmentFilter {
  startDateTime?: string;
  endDateTime?: string;
  vetId?: UUID;
  status?: AppointmentStatus | AppointmentStatus[];
  appointmentType?: AppointmentType;
  bookingChannel?: BookingChannel;
  animalId?: UUID;
  ownerId?: UUID;
  page?: number;
  size?: number;
  sort?: string;

  // Backward-compatible query aliases; do not use for new integrations.
  from?: string;
  to?: string;
  veterinarianId?: UUID;
  type?: AppointmentType;
  clientId?: UUID;
  clinicId?: UUID;
}

export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ApiError {
  timestamp: string; // Common API serializer currently returns yyyy-MM-dd HH:mm in Europe/Istanbul
  status: number;
  code: string;
  message: string;
  path: string;
  details: Record<string, unknown>;
}
```

`AppointmentUpdateRequest` tanımlanmamıştır; genel güncelleme endpoint'i mevcut değildir. `startsAt`/`endsAt` ve response'taki timestamp'ler appointment DTO'sunda ISO-8601 offset biçimindedir. Diğer modüllerin tarih formatlarını bu sözleşmeden çıkarmayın.

## 4. Örnek istek ve yanıtlar

Örnek UUID'ler açıklama amaçlıdır. Response alanları `AppointmentResponse` ile birebir aynıdır.

### Günlük takvim

```http
GET /api/v1/appointments?startDateTime=2032-05-03T00%3A00%3A00%2B03%3A00&endDateTime=2032-05-04T00%3A00%3A00%2B03%3A00&sort=startsAt,asc
Authorization: Bearer <accessToken>
```

### Haftalık takvim

```http
GET /api/v1/appointments?startDateTime=2032-05-03T00%3A00%3A00%2B03%3A00&endDateTime=2032-05-10T00%3A00%3A00%2B03%3A00&vetId=aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa&page=0&size=100
Authorization: Bearer <accessToken>
```

İki liste çağrısının örnek response'u:

```json
{
  "content": [
    {
      "id": "11111111-1111-4111-8111-111111111111",
      "appointmentNo": "A-000123",
      "clinicId": "22222222-2222-4222-8222-222222222222",
      "clientId": "33333333-3333-4333-8333-333333333333",
      "clientName": "Ayşe Yılmaz",
      "animalId": "44444444-4444-4444-8444-444444444444",
      "animalName": "Pamuk",
      "patientNo": "P-000123",
      "veterinarianId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      "veterinarianName": "Dr. Deniz Kaya",
      "appointmentType": "EXAM",
      "status": "CONFIRMED",
      "startsAt": "2032-05-03T09:30:00+03:00",
      "endsAt": "2032-05-03T10:00:00+03:00",
      "reason": "Kontrol muayenesi",
      "internalNotes": null,
      "bookingChannel": "STAFF",
      "cancelledAt": null,
      "cancellationReason": null,
      "createdAt": "2032-04-20T14:10:00+03:00",
      "updatedAt": "2032-04-21T08:30:00+03:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

### Takvim penceresi sınırını kesen randevu

Bir randevu `2032-05-02T23:30:00+03:00`'da başlayıp `2032-05-03T00:30:00+03:00`'da bitiyorsa, pazartesi penceresi `2032-05-03T00:00:00+03:00`'dan başladığında `endsAt > startDateTime` koşuluyla listede yer alır.

### Randevu detayı

```http
GET /api/v1/appointments/11111111-1111-4111-8111-111111111111
Authorization: Bearer <accessToken>
```

Yanıt gövdesi günlük/haftalık örnekteki tek `Appointment` nesnesidir (`content` ve pagination alanları olmadan).

### Randevu oluşturma

```http
POST /api/v1/appointments
Authorization: Bearer <accessToken>
X-Clinic-Id: 22222222-2222-4222-8222-222222222222
Content-Type: application/json
```

```json
{
  "animalId": "44444444-4444-4444-8444-444444444444",
  "clientId": "33333333-3333-4333-8333-333333333333",
  "veterinarianId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  "appointmentType": "EXAM",
  "startsAt": "2032-05-03T09:30:00+03:00",
  "endsAt": "2032-05-03T10:00:00+03:00",
  "reason": "Kontrol muayenesi",
  "internalNotes": null,
  "bookingChannel": "STAFF"
}
```

`201` yanıtı `Appointment` DTO'sudur; ilk durum `BOOKED` olur. `clientId` verilirse hayvanın güncel sahiplerinden biri olmalıdır. Verilmezse backend güncel birincil iletişim sahibini, yoksa ilk güncel sahibi seçer. Geçmiş zamana randevuyu engelleyen bir iş kuralı/şema kısıtı bulunmadığından geçmiş başlangıç ayrıca reddedilmez.

### Randevu onaylama

```http
PATCH /api/v1/appointments/11111111-1111-4111-8111-111111111111/confirm
Authorization: Bearer <accessToken>
X-Clinic-Id: 22222222-2222-4222-8222-222222222222
```

`200` yanıtı aynı `Appointment` alanlarını taşır, `status` değeri `CONFIRMED` olur.

### Randevu iptali

```http
PATCH /api/v1/appointments/11111111-1111-4111-8111-111111111111/cancel
Authorization: Bearer <accessToken>
X-Clinic-Id: 22222222-2222-4222-8222-222222222222
Content-Type: application/json
```

```json
{ "cancellationReason": "Hasta sahibi randevuyu iptal etti." }
```

`200` yanıtında `status: "CANCELLED"`, `cancelledAt` ve `cancellationReason` doldurulur. Eski gövde alanı `reason` da yalnızca geriye uyumluluk için kabul edilir.

### Randevuyu yeniden planlama

```http
PATCH /api/v1/appointments/11111111-1111-4111-8111-111111111111/reschedule
Authorization: Bearer <accessToken>
X-Clinic-Id: 22222222-2222-4222-8222-222222222222
Content-Type: application/json
```

```json
{
  "startsAt": "2032-05-04T11:00:00+03:00",
  "endsAt": "2032-05-04T11:30:00+03:00",
  "veterinarianId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
}
```

`200` yanıtı `Appointment` DTO'sudur. `veterinarianId` gönderilmez/null olursa mevcut hekim korunur. Başlangıç ve bitiş aralığı, hekimin etkinliği, tanımlı çalışma saati ve EXCLUDE çakışma kısıtları tekrar uygulanır.

### Saat çakışması

```http
HTTP/1.1 409 Conflict
Content-Type: application/json
```

```json
{
  "timestamp": "2026-11-09 15:41",
  "status": 409,
  "code": "APPOINTMENT_TIME_CONFLICT",
  "message": "Bu saat dolu. Lütfen farklı bir saat seçin.",
  "path": "/api/v1/appointments",
  "details": { "sqlState": "23P01" }
}
```

## 5. Hata response'ları

Ortak şekil `ApiError`'dır:

```json
{
  "timestamp": "2026-11-09 15:41",
  "status": 422,
  "code": "BUSINESS_RULE_VIOLATION",
  "message": "Bu randevu için seçilen işlem gerçekleştirilemiyor.",
  "path": "/api/v1/appointments/{id}/confirm",
  "details": {}
}
```

| HTTP | `code` | Örnek / UI davranışı |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Eksik/geçersiz alan; `details.reason` ile form alanlarını düzelt |
| 400 | `MALFORMED_REQUEST` | JSON/tarih body biçimi okunamadı |
| 400 | `BAD_REQUEST` | Geçersiz tarih aralığı, durum, tür, kanal veya sıralama |
| 400 | `MISSING_HEADER` | Yazma çağrısında `X-Clinic-Id` eksik |
| 401 | `UNAUTHORIZED` | Oturum/token yenile veya login'e yönlendir |
| 403 | `FORBIDDEN` | Yetki yok; yönetim eylemlerini gizle |
| 404 | `NOT_FOUND` | Kayıt bulunamadı veya RLS ile erişim dışı |
| 409 | `APPOINTMENT_TIME_CONFLICT` | SQLState `23P01`; aynı hekim/hayvan slotu çakışıyor |
| 409 | `DB_CONFLICT` / `CONFLICT` | Diğer veritabanı veya eşzamanlı işlem çakışmaları |
| 422 | `VETERINARIAN_OUTSIDE_WORKING_HOURS` | "Seçilen saat hekimin çalışma saatleri dışında." |
| 409 | `INVALID_STATUS_TRANSITION` | Geçersiz durum geçişi (FG-E4); `details.from`, `details.to` |
| 409 | `PATIENT_DECEASED` | Vefat etmiş hasta için randevu / muayene açılamaz |
| 422 | `BUSINESS_RULE_VIOLATION` | Sahip uyuşmazlığı veya başka uygulama iş kuralı |
| 422 | `DB_RULE_VIOLATION` | DB CHECK/trigger iş kuralı ihlali |

State transition kuralı dışındaki hatalar için istemci yalnızca `status`'a değil, `code`'a göre davranmalı; kullanıcı mesajı `message` alanından gösterilmelidir. Hata ayrıntısında SQL metni veya hassas DB bilgisi beklenmemelidir.

## 6. Durum geçişleri ve doğrulama

İzin verilen randevu geçişleri (dışındaki her geçiş `409 INVALID_STATUS_TRANSITION`):

| Mevcut durum | İzin verilen işlem/hedef |
|---|---|
| `REQUESTED` | `confirm` → `CONFIRMED`; `cancel` → `CANCELLED` |
| `BOOKED` | `confirm` → `CONFIRMED`; `check-in` → `CHECKED_IN`; `cancel` → `CANCELLED`; `no-show` → `NO_SHOW` |
| `CONFIRMED` | `check-in` → `CHECKED_IN`; `cancel` → `CANCELLED`; `no-show` → `NO_SHOW` |
| `CHECKED_IN` | `cancel` → `CANCELLED`; `start` muayeneyi açıp `IN_PROGRESS` yapar |
| `IN_PROGRESS` | Bağlı muayene `complete` ile kapatılır ve `COMPLETED` olur |
| `COMPLETED`, `CANCELLED`, `NO_SHOW` | Yeni geçiş yok |

`reschedule` yalnızca `REQUESTED`, `BOOKED` veya `CONFIRMED` için mümkündür. `start` ve `complete`, appointment durumunu doğrudan değiştiren kısayollar değildir; muayene kaydını açıp kapatma kurallarını kullanır. `complete` için açık muayenenin `PLAN` aşamasına ulaşması gerekir.

## 7. Veritabanı, çalışma saati ve çakışma garantileri

- Şema tek doğruluk kaynağı `db/VetClinic_PostgreSQL_Kurulum_TR.sql` ve `V1__baseline.sql`'dir; randevu sütunları, status/type/channel CHECK değerleri, çalışma saati tablosu, trigger ve iki EXCLUDE constraint zaten tanımlıdır. Bu geliştirme migration/şema değişikliği eklemez.
- `ex_appointments_vet_no_overlap` aynı organizasyon/hekim için etkin randevuların örtüşmesini; `ex_appointments_animal_no_overlap` aynı organizasyon/hayvan çakışmasını transaction yarışları dahil DB'de engeller.
- Trigger, atanmış hekim için o klinikte etkin bir çalışma saati konfigürasyonu varsa randevunun yerel saatlerde tanımlı pencereye sığmasını doğrular. Bu EXCLUDE constraint'ten ayrı bir kontroldür. Hekim için çalışma saati kaydı yoksa trigger kurulum/demo uyumluluğu için saat penceresini zorunlu tutmaz.
- Trigger aynı randevunun klinik yerel saatinde aynı gün içinde olmasını ister. Dolayısıyla API listesi pencereleri kesen tüm kayıtları bulsa da hekim atanmış randevuda gece yarısını aşan yeni kayıt DB tarafından reddedilebilir.
- Geçmiş tarih yasağı yoktur. Aynı hayvanın güncel sahipliği ve atanmış hekimin organizasyon kapsamı backend/RLS ile kontrol edilir. Klinik takvim kısıtları, yalnızca DB'de tanımlanmışsa uygulanır.

## 8. Frontend entegrasyon kuralları

1. `startDateTime`/`endDateTime` değerlerini Europe/Istanbul takvim sınırlarından oluşturup ISO-8601 offset ile gönderin. `Date.toISOString()` UTC üretir; bu kabul edilir ama gün/hafta aralığını hesaplarken Türkiye yerel gün başlangıcını UTC'ye dönüştürmeyi unutmayın.
2. `endDateTime` üst sınırını exclusive gönderin. Liste yalnızca başlangıç zamanına göre filtrelenmez; süresi seçilen pencereyle kesişen kayıtlar da döner.
3. Pagination 0 tabanlıdır. Varsayılan page `0`, size `20`, maximum size `200`; takvimde gerekirse `size=200` kullanıp `totalElements`/`totalPages` ile devam sayfasını kontrol edin.
4. Çoklu durumları tekrarlanan query key ile kodlayın: `status=BOOKED&status=CONFIRMED`. `null`, `undefined` ve boş string filtreleri URL'ye eklemeyin.
5. Backend `reason`, `internalNotes`, hekim alanları ve iptal alanlarını nullable döndürebilir; UI boş metin uydurmak yerine null kontrolü yapmalıdır. Create request'te olmayan `clientId` güncel sahibin backend tarafından çözülmesine izin verir.
6. API hatalarını `{ status, code, message, details }` yapısında okuyun. `409 APPOINTMENT_TIME_CONFLICT` ve çalışma saati `422` hatalarını ilgili zaman alanında gösterin; durum `422` mesajını toast/form mesajı yapın; `401` oturumu yenileyin; `403` yönetim eylemlerini göstermeyin.
7. Bearer access token'ı ortak API istemcisinin `Authorization` header'ında taşıyın. Kurum/tenant ID'si ayrıca gönderilmez; şube seçimi yazma isteklerinde `X-Clinic-Id` ile taşınır.
8. Yönetim ekranı/eylem görünürlüğünü kullanıcının `appointments.manage` yetkisine bağlayın. Başlatma/tamamlama için ayrıca `clinical.write` gerekir. Backend kontrolü her durumda esas olmaya devam eder.
9. Takvim görünümü için hekim, tür, kanal, sahip/hayvan ve durum filtresi query parametrelerine; randevu kartı/detayını ise DTO'daki gerçek alanlara bağlayın. `vetId` yalnızca filter parametresidir; create/reschedule gövdesinin gerçek alanı `veterinarianId`'dir.
10. Çakışma için istemcide slot kontrolü yapılabilir ama bu yalnızca UX ipucudur; eşzamanlı isteklerde DB `EXCLUDE` constraint'i son otoritedir.

## 9. Frontend Agent Promptu

Hazır prompt, contract ve örneklerle birlikte [APPOINTMENT_CALENDAR_FRONTEND_AGENT_PROMPT.md](APPOINTMENT_CALENDAR_FRONTEND_AGENT_PROMPT.md) dosyasındadır.
