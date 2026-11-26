# Frontend Agent Prompt – Randevu Takvimi

Aşağıdaki talimatı frontend kodlama agent'ına doğrudan verin. İçindeki contract backend'de mevcut `AppointmentResponse`, request DTO'ları, `PageResponse`, `ApiError`, appointment controller ve PostgreSQL şemasıyla uyumludur. Kaynak sözleşme: `docs/APPOINTMENTS_API_CONTRACT.md`. Bu prompttaki alanlar ve endpoint'ler dışında backend/API alanı varsaymayın.

---

## GÖREV

Veteriner Klinik Yönetim Platformu için **Randevu Takvimi** ekranını mevcut frontend uygulamasına entegre ederek geliştir.

### Uygulamaya başlamadan önce

1. Frontend'in mevcut framework, route, auth/session, API client, state yönetimi, form, modal/drawer, takvim ve tasarım sistemi yapılarını incele. Mevcut bileşenleri ve stilleri yeniden kullan.
2. Yeni state-management, UI, calendar veya date-time kütüphanesi ekleme; önce mevcut bağımlılıklarla çöz. Gerekli yeni bağımlılık gerçekten yoksa gerekçesini raporla ve onaysız ekleme.
3. Login akışını, tenant/şube bağlamını, route'ları, ortak API hata işleyicisini ve mevcut tasarım sistemini bozma.
4. Mock veriyi yalnızca geliştirme/test için kullan; gerçek ekran verisini aşağıdaki API contract'ından beslenen servis katmanından yükle.
5. Backend Entity/veritabanı alanlarını tahmin etme. Özellikle `vetId` filtre query'sidir; create/reschedule body alanı `veterinarianId`'dir.

## EKRAN VE ETKİLEŞİM GEREKSİNİMLERİ

- Minimal, profesyonel, premium SaaS görünümü; açık zemin ve mavi vurgular. Mevcut frontend renk, tipografi, spacing ve bileşen standartlarını koru.
- Günlük/haftalık görünüm seçici; hekim bazlı sütunlar; önceki/sonraki gün/hafta ve bugüne dönme navigasyonu.
- Hekim, randevu türü, durum ve kanal filtreleri. Filtre değişince doğru zaman aralığıyla API çağrısı yap; aynı hekim/tür/status için backend'e göre var olmayan alan veya endpoint uydurma.
- Günlük özet kartları yalnızca yüklenmiş liste verisinden sayılabilen ölçüleri göstermeli; API'de olmayan istatistik endpoint'i varsayma.
- Randevu kartında gerçek DTO'dan saat aralığı, hayvan, hasta no, sahip, hekim, tür, durum, kanal ve varsa sebebi göster.
- Randevu kartına tıklama detay sağ panelini açsın. Detayda backend DTO alanlarını null-safe biçimde kullan.
- Randevu oluşturma sağ paneli: hayvan ve sahip seçimi; hekim; tür; başlangıç/bitiş; sebep; kanal. Başlangıç ve bitişi kontrol et, ancak çakışma kararında backend sonucunu esas al.
- Duruma göre eylemler: Onayla, check-in, muayeneyi başlat, tamamla, iptal et, no-show ve yeniden planla. Yalnızca aşağıdaki geçiş tablosunda izin verilen eylemi göster. `appointments.manage` olmayan kullanıcıya yönetim eylemi gösterme; başlat/tamamlama için `clinical.write` da gerekir.
- İptal panelinde `cancellationReason` zorunlu; boş/whitespace değeri göndermeden frontend doğrulaması yap.
- Yeniden planlama paneli yeni başlangıç, bitiş ve opsiyonel hekim almalı. Null/boş `veterinarianId` mevcut hekimi korur, temizlemez.
- Loading, empty, error, success, unauthorized/forbidden, validation, conflict ve responsive dar ekran durumlarını uygula. A11y: klavye, focus management, label'lar ve anlaşılır hata metinleri.
- Saat çakışması kontrolünü yalnızca UX ipucu say; yarış koşullarında DB kısıtları esastır.

## GÖMÜLÜ BACKEND API CONTRACT

### Ortak kurallar

- Base path: `/api/v1`; JSON response sarmalayıcısızdır.
- Kimlik: ortak auth istemcisindeki `Authorization: Bearer <accessToken>`.
- Organizasyon JWT'den gelir; request'e `organizationId` ekleme.
- Yazma çağrılarında aktif şubeyi `X-Clinic-Id: <UUID>` header'ı ile taşı.
- Bütün appointment listeleme/detay/yazma uçları backend'de `appointments.manage` kontrol eder. `/start` ve `/complete` ayrıca `clinical.write` ister. UI gizlemesi backend kontrolünün yerine geçmez.
- Randevu timestamp'leri ISO-8601 offset biçimindedir (ör. `2032-05-03T09:30:00+03:00`) ve backend Europe/Istanbul'a normalize eder. Yeni frontend daima offset'li ISO gönderir. Yerel takvim gününün başlangıç/bitişini Europe/Istanbul'da hesapla; UTC `Date.toISOString()` kullanıyorsan önce yerel gün sınırlarını doğru offset'e dönüştür.
- Eski `yyyy-MM-dd HH:mm` parser uyumluluğu vardır ama yeni istemci bu biçimi kullanmamalıdır.
- Tarih penceresi half-open ve kesişim bazlıdır: randevu, `endsAt > startDateTime && startsAt < endDateTime` ise döner. Başlangıç parametresi randevu başlangıç zamanına uygulanmaz.
- Pagination 0-tabanlıdır; varsayılan `page=0`, `size=20`, max `size=200`. Response: `{ content, page, size, totalElements, totalPages, first, last }`.
- `status` parametresini tekrarlayarak gönder: `status=BOOKED&status=CONFIRMED`; virgülle ayrılmış değer de kabul edilir. Boş/null filtreleri URL'ye ekleme.
- Varsayılan sıralama `startsAt,asc`. Desteklenen tek `sort` değeri: `startsAt`, `endsAt`, `appointmentNo`, `status`, `appointmentType`, `bookingChannel` + `asc|desc` (ör. `sort=startsAt,asc`).
- Query adları: `startDateTime`, `endDateTime`, `vetId`, `status`, `appointmentType`, `bookingChannel`, `animalId`, `ownerId`, `page`, `size`, `sort`. Geriye uyumluluk alias'ları `from`, `to`, `veterinarianId`, `type`, `clientId`, `clinicId`; yeni istemci canonical adları kullanmalı. `ownerId`, appointment satırındaki `clientId`'dir.
- Yanıttaki `reason`, `internalNotes`, hekim alanları ve iptal alanları nullable olabilir.
- Create'te `clientId` verilmezse backend hayvanın güncel birincil iletişim sahibini, yoksa ilk güncel sahibini seçer. Geçmiş tarihe randevu oluşturulmasını yasaklayan kural backend'de yoktur.
- Randevu `appointmentType` değerleri: `EXAM`, `VACCINATION`, `SURGERY`, `FOLLOW_UP`, `GROOMING`, `LAB`, `IMAGING`, `OTHER`.
- `status`: `REQUESTED`, `BOOKED`, `CONFIRMED`, `CHECKED_IN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `NO_SHOW`.
- `bookingChannel`: `STAFF`, `PHONE`, `WEB`, `MOBILE`, `WHATSAPP`, `OTHER`. DB ayrıca `OTHER` kabul eder.

### Endpoint özeti

| Method / URL | Request | Response ve davranış |
|---|---|---|
| `GET /appointments` | Query filtreleri yukarıda | `200 PaginatedResponse<AppointmentListItem>`; hatalı query/range/sort `400`; auth `401/403` |
| `GET /appointments/{id}` | Body yok | `200 AppointmentDetail`; bulunamadı/erişim dışı `404` |
| `POST /appointments` | `AppointmentCreateRequest`, `X-Clinic-Id` | `201 Appointment`, başlangıç status `BOOKED`; validation `400`, çakışma `409`, DB iş kuralı `422` |
| `PATCH /appointments/{id}/confirm` | Body yok | `200 Appointment`; `REQUESTED/BOOKED → CONFIRMED`; geçersiz geçiş `409 INVALID_STATUS_TRANSITION` |
| `PATCH /appointments/{id}/cancel` | `{ "cancellationReason": "..." }` | `200 Appointment`; sebep gerekli, `1..500`; eksik `400` |
| `PATCH /appointments/{id}/check-in` | Body yok | `200 Appointment`; `BOOKED/CONFIRMED → CHECKED_IN` |
| `PATCH /appointments/{id}/no-show` | Body yok | `200 Appointment`; `BOOKED/CONFIRMED → NO_SHOW` |
| `PATCH /appointments/{id}/reschedule` | `AppointmentRescheduleRequest` | `200 Appointment`; sadece `REQUESTED/BOOKED/CONFIRMED`; range, hekim, saat ve çakışma tekrar kontrol edilir |
| `PATCH /appointments/{id}/start` | Opsiyonel `{ "chiefComplaint": string, "history": string }` | `200 EncounterResponse`; bağlı encounter oluşturup randevuyu `IN_PROGRESS` yapar; `appointments.manage` + `clinical.write` |
| `PATCH /appointments/{id}/complete` | Opsiyonel `{ "vetNotes": string }` | `200 Appointment`; açık encounter'ı kapatır; tamamlanmış encounter `COMPLETED` yapar; `appointments.manage` + `clinical.write` |

Eski istemci uyumluluğu için confirm/cancel/check-in/no-show/reschedule aynı URL'de `POST` ile de çalışır. Yeni frontend sadece `PATCH` kullanır. `POST /appointments/{id}/encounter` ve `POST /encounters/{id}/close` mevcut klinik akışıyla alternatif olarak korunur. Genel `PATCH /appointments/{id}` yoktur; `AppointmentUpdateRequest` desteklenmez.

### TypeScript modelleri (DTO ile birebir)

```ts
export type UUID = string;

export type AppointmentStatus =
  | "REQUESTED" | "BOOKED" | "CONFIRMED" | "CHECKED_IN"
  | "IN_PROGRESS" | "COMPLETED" | "CANCELLED" | "NO_SHOW";

export type AppointmentType =
  | "EXAM" | "VACCINATION" | "SURGERY" | "FOLLOW_UP"
  | "GROOMING" | "LAB" | "IMAGING" | "OTHER";

export type BookingChannel =
  | "STAFF" | "PHONE" | "WEB" | "MOBILE" | "WHATSAPP" | "OTHER";

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
  startsAt: string;
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
  clientId?: UUID | null;
  veterinarianId?: UUID | null;
  appointmentType?: AppointmentType;
  startsAt: string;
  endsAt: string;
  reason?: string | null;
  internalNotes?: string | null;
  bookingChannel?: BookingChannel;
}

export interface AppointmentRescheduleRequest {
  startsAt: string;
  endsAt: string;
  veterinarianId?: UUID | null;
}

export interface AppointmentCancelRequest {
  cancellationReason: string;
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
  from?: string; // legacy alias
  to?: string; // legacy alias
  veterinarianId?: UUID; // legacy alias
  type?: AppointmentType; // legacy alias
  clientId?: UUID; // legacy alias
  clinicId?: UUID; // legacy filter
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
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  details: Record<string, unknown>;
}
```

Appointment `startsAt`, `endsAt`, `cancelledAt`, `createdAt`, `updatedAt` ISO offset stringsidir; null olanlar dışında alanlar zorunludur. `ApiError.timestamp` common API serializer nedeniyle şu an `yyyy-MM-dd HH:mm` Europe/Istanbul biçimindedir. `AppointmentUpdateRequest` yoktur.

### Örnek istekler/yanıtlar

Günlük liste:

```http
GET /api/v1/appointments?startDateTime=2032-05-03T00%3A00%3A00%2B03%3A00&endDateTime=2032-05-04T00%3A00%3A00%2B03%3A00&sort=startsAt,asc
```

Haftalık liste:

```http
GET /api/v1/appointments?startDateTime=2032-05-03T00%3A00%3A00%2B03%3A00&endDateTime=2032-05-10T00%3A00%3A00%2B03%3A00&vetId=aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa&page=0&size=100
```

Her iki liste response'u:

```json
{
  "content": [{
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
  }],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "first": true, "last": true
}
```

Detay:

```http
GET /api/v1/appointments/11111111-1111-4111-8111-111111111111
```

`200` body, list içindeki tek appointment nesnesidir; pagination wrapper yoktur.

Randevu oluştur:

```http
POST /api/v1/appointments
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

`201` body tam Appointment nesnesidir; `status` başlangıçta `BOOKED`.

Onayla:

```http
PATCH /api/v1/appointments/11111111-1111-4111-8111-111111111111/confirm
X-Clinic-Id: 22222222-2222-4222-8222-222222222222
```

`200` body tam Appointment nesnesidir, `status: "CONFIRMED"`.

İptal:

```http
PATCH /api/v1/appointments/11111111-1111-4111-8111-111111111111/cancel
X-Clinic-Id: 22222222-2222-4222-8222-222222222222
Content-Type: application/json
```

```json
{ "cancellationReason": "Hasta sahibi randevuyu iptal etti." }
```

`200` body tam Appointment nesnesidir; `status: "CANCELLED"`, `cancellationReason` ve `cancelledAt` dolar.

Yeniden planla:

```http
PATCH /api/v1/appointments/11111111-1111-4111-8111-111111111111/reschedule
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

`200` body tam Appointment nesnesidir. `veterinarianId: null` veya eksik olması mevcut hekimi korur.

Çakışma:

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

### Hata kodları

Hata body'si `{ timestamp, status, code, message, path, details }` şeklindedir.

| Status | Code / davranış |
|---|---|
| 400 | `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `BAD_REQUEST`, `MISSING_HEADER`; alan/tarih/sort sorunlarını formda göster |
| 401 | `UNAUTHORIZED`; ortak auth istemcisiyle oturumu yenile veya login'e yönlendir |
| 403 | `FORBIDDEN`; yönetim eylemlerini gizle |
| 404 | `NOT_FOUND`; kayıt yok veya organizasyon/şube RLS ile görünmez |
| 409 | `APPOINTMENT_TIME_CONFLICT` (23P01), `DB_CONFLICT`, `CONFLICT`; slotu değiştir veya yeniden yükle |
| 422 | `VETERINARIAN_OUTSIDE_WORKING_HOURS`, `BUSINESS_RULE_VIOLATION`, `DB_RULE_VIOLATION`; `message` kullanıcıya gösterilir |

Kullanıcı mesajları: çakışmada "Bu saat dolu. Lütfen farklı bir saat seçin."; çalışma saati dışında "Seçilen saat hekimin çalışma saatleri dışında."; geçersiz durum "Bu randevu için seçilen işlem gerçekleştirilemiyor." Error `code`'a göre UX yap; SQL/DB metni veya hassas ayrıntı göstermeye çalışma.

### Backend durum geçişleri ve DB garantisi

| Durum | Takvim UI eylemleri |
|---|---|
| `REQUESTED` | Onayla veya iptal |
| `BOOKED` | Onayla, check-in, iptal, no-show |
| `CONFIRMED` | Check-in, iptal, no-show |
| `CHECKED_IN` | Muayeneyi başlat; iptal de mevcut state machine'de mümkün |
| `IN_PROGRESS` | Muayene akışı tamamlanmışsa tamamla |
| `COMPLETED`, `CANCELLED`, `NO_SHOW` | Terminal; yeni durum eylemi yok |

Reschedule yalnızca `REQUESTED`, `BOOKED`, `CONFIRMED` durumlarında kullanılabilir. Randevu başlatma/tamamlama encounter açma/kapama kurallarını kullanır; `IN_PROGRESS`/`COMPLETED` alanlarını doğrudan PATCH etmeye çalışma.

DB'de aynı hekim ve aynı hayvan için aktif zaman aralıklarını eşzamanlı çağrılarda da engelleyen EXCLUDE constraint'ler var. Çalışma saati denetimi ayrı bir trigger ile yapılır: atanmış hekim için etkin çalışma saatleri kayıtlıysa klinik timezone'unda uygulanır; hiç schedule kaydı yoksa trigger saat kısıtı uygulamaz. Atanmış hekimli randevuda yerel gün aşımı trigger tarafından reddedilebilir. Frontend bu DB kurallarını kopyalayan yeni bir yetki/validasyon otoritesi oluşturmamalıdır.

## UYGULAMA ÇIKTISI

- Mevcut frontend mimarisinde ekran, route, API service/hooks ve bileşenleri oluştur/güncelle.
- Günlük/haftalık takvim, hekim sütunları, tarih navigasyonu, dört filtre, günlük özetler, kartlar, create/detail/reschedule sağ panelleri ve status-uyumlu action'ları tamamla.
- API çağrıları ortak auth/client altyapısını kullansın; status tekrarlarını query string'e doğru encode etsin; `X-Clinic-Id`'yi yalnızca write isteklerinde mevcut şube seçicisinden eklesin.
- Loading/empty/error/success states, form validation, responsive görünüm ve erişilebilirliği tamamla.
- Contract dışında endpoint, DTO alanı, izin, status geçişi veya çalışma saati varsayımı ekleme.
- Değişen dosyaları ve test/validation sonuçlarını kısa şekilde bildir; çalışmayan test veya altyapı eksiklerini açıkça belirt.
