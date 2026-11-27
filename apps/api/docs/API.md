# API Rehberi

Etkileşimli dokümantasyon için Swagger UI: `/swagger-ui.html`. Bu doküman uç noktaların özetidir.

## Ortak Kurallar

- Kimlik doğrulama: `Authorization: Bearer <accessToken>`
- Şube seçimi: `X-Clinic-Id: <şube UUID>` (yazma işlemlerinde gerekir)
- Tarih biçimi (yanıtlarda): `yyyy-MM-dd HH:mm`
- Hata kodları: `400` eksik başlık/geçersiz istek, `401` kimlik yok, `403` yetki yok, `404` kayıt yok veya başka kiracıya ait, `409` çakışma/durum ihlali, `422` doğrulama/iş kuralı

## Kimlik Doğrulama – `/api/auth`

| Metot | Yol | Açıklama |
|---|---|---|
| POST | `/login` | Kurum kodu + e-posta + şifre ile giriş |
| POST | `/refresh` | Refresh token ile yeni access token |
| POST | `/change-password` | Şifre değiştirme |
| GET | `/me` | Oturumdaki kullanıcı |
| POST | `/api/platform/auth/login`, `/refresh`, `/logout`, GET `/me` | Platform yöneticisi |

## Klinik / Şube – `/api/v1`

| Metot | Yol |
|---|---|
| GET | `/clinics`, `/clinics/{id}`, `/clinic-groups`, `/clinic-groups/{id}`, `/roles` |

## Kullanıcılar – `/api/v1/users` (`users.manage`)

`POST /`, `GET /`, `GET /{id}`, `PATCH /{id}`, `POST /{id}/clinic-roles`, `DELETE /{id}/clinic-roles/{assignmentId}`, `POST /{id}/deactivate`, `POST /{id}/activate`, `POST|GET /{id}/veterinarian`

## Müşteriler – `/api/v1/clients`

`POST /`, `GET /`, `GET /{id}`, `PATCH /{id}`, `POST /{id}/deactivate`, `POST /{id}/activate`

- `GET /` : `q` ad, e-posta, normalize telefon, hayvan adı ve çip ile eşleşir; satırda `animalCount`, `animals` (en çok 5) ve `matchedAnimalIds` döner.
- `POST /` : mükerrer telefon/e-posta adayı varsa ve `confirmDuplicate=true` yoksa kayıt oluşmaz, 409 `DUPLICATE_CLIENT_CANDIDATE` + `details.possibleDuplicates`.

Ayrıntılı frontend sözleşmesi: [CLIENTS_ANIMALS_CONTRACT.md](CLIENTS_ANIMALS_CONTRACT.md)

## Hayvanlar

| Metot | Yol |
|---|---|
| POST / GET | `/animals` |
| GET / PATCH | `/animals/{id}` (yanıtta `primaryOwnerId` + `owners`) |
| GET | `/animals/by-chip/{chipNo}` (çip → hayvan + sahipler) |
| POST | `/clients/{clientId}/animals` (hayvanı oluşturur, müşteriyi birincil sahip yapar; pasif müşteri 422; `clients.write`) |
| GET | `/clients/{clientId}/animals` |
| POST | `/animals/{id}/owners`, `/animals/{id}/owners/{ownerId}/primary`, `/animals/{id}/owners/{ownerId}/end` |
| POST | `/animals/{id}/deceased` |
| GET | `/species`, `/species/{id}/breeds` |

## Randevular – `/api/v1/appointments`

`appointments.manage` yetkisi listeleme ve detay dahil bütün randevu uçlarında sunucuda doğrulanır. Yazma uçlarında `X-Clinic-Id` zorunludur. Takvim filtreleri, DTO alanları, geçişler, hata gövdeleri ve frontend örnekleri için [Randevu Takvimi API Contract](APPOINTMENTS_API_CONTRACT.md) dosyasına bakın.

| Metot | Yol | Açıklama |
|---|---|---|
| GET | `/` | Filtrelenebilir, sayfalanmış takvim listesi; zaman aralığıyla kesişen kayıtlar |
| GET | `/{id}` | DTO tabanlı randevu detayı |
| POST | `/` | Randevu oluşturma; DB çakışma ve tanımlı çalışma saati kontrolleri uygulanır |
| PATCH | `/{id}/confirm`, `/{id}/cancel`, `/{id}/check-in`, `/{id}/no-show`, `/{id}/reschedule` | Geçerli durum geçişleri; `cancel` için `cancellationReason` zorunlu |
| PATCH | `/{id}/start` | Klinik yetkisi olan hekim için bağlı muayene başlatır (`EncounterResponse`) |
| PATCH | `/{id}/complete` | Açık bağlı muayeneyi kurallara göre kapatır; randevu tamamlanır |
| POST | `/{id}/confirm`, `/{id}/cancel`, `/{id}/check-in`, `/{id}/no-show`, `/{id}/reschedule` | Geriye uyumluluk için PATCH karşılıklarının eski metotları |
| POST | `/{appointmentId}/encounter` | Mevcut klinik akışıyla muayene başlatma (`clinical.write`) |
| GET | `/{appointmentId}/clinical-summary` | Randevuya bağlı muayene + tedavi + aşı özeti (`clinical.read`) |

`clinical-summary` yanıtı:

```json
{
  "appointmentId": "…",
  "encounters": [
    { "encounter": { "id": "…", "status": "CLOSED", "treatments": [ ] },
      "vaccinations": [ { "vaccineName": "Kuduz" } ] }
  ]
}
```

Randevu yoksa veya başka kiracıya aitse `404`.

## Global Arama – `/api/v1/search` (`clients.read`)

`GET /api/v1/search?q=` — en az 2 karakter (aksi 422). Müşteri adı, telefon, hayvan adı ve çip no ile arar; en fazla 10 sonuç.

## Hayvan Sağlık – `/api/v1/animals/{animalId}` (`clients.read` / `clients.write`)

| Metot | Yol | Açıklama |
|---|---|---|
| GET | `/health-summary` | Son kilo, aktif alerjiler, çip no |
| GET, POST | `/weights` | Kilo geçmişi / kayıt (201) |
| GET, POST | `/allergies` (`?activeOnly=`) | Alerji listesi / ekleme (201) |
| POST | `/allergies/{id}/resolve` | Alerjiyi kapat |
| GET, POST | `/identifiers` | Çip no vb. kimlikler (tekrar → 409) |

## Fatura / Kasa – `/api/v1/billing` (`billing.manage`)

| Metot | Yol | Açıklama |
|---|---|---|
| GET | `/invoices`, `/invoices/{id}` | Liste (`status` birden çok verilebilir) / detay |
| POST | `/invoices` | Taslak fatura (201). Kalem: `lineType` SERVICE/PRODUCT/OTHER, fiyat KDV hariç, `discountAmount` (TL) veya `discountRate` (%), `taxRate` |
| POST | `/invoices/{id}/lines` | Kalem ekle (yalnız DRAFT) |
| PUT | `/invoices/{id}/lines` | Taslağın kalemlerini topluca değiştir (yalnız DRAFT, aksi 422) |
| POST | `/invoices/{id}/issue` | Kes: FEFO lot dağıtımı + numara (`F-000001`, organizasyon bazında boşluksuz) + stok/komisyon (DB tetikleyicisi) tek transaction; yetersiz stok 409 `INSUFFICIENT_STOCK`, hiçbir şey değişmez |
| POST | `/invoices/{id}/void` | İptal (yalnız DRAFT) |
| POST | `/invoices/{id}/payments` | Ödeme al (201; fazla ödeme → 422) |
| GET | `/invoices/{id}/pdf` | Fatura / fiş taslağı, `application/pdf` (Türkçe karakterli) |
| GET | `/clients/{id}/balance` | Müşteri bakiyesi |
| GET | `/cashier/daily?date=&clinicId=` | Günlük kasa özeti |

Taslak faturanın `invoiceNo` alanı geçici yer tutucudur (`F-TASLAK-…`); gerçek numara kesimde verilir.
Satır tutarları: brüt = round(miktar × birim, 2), net = brüt − indirim, KDV = round(net × oran / 100, 2), toplam = net + KDV.

## Komisyon – `/api/v1/commissions` (`commissions.read`)

| Metot | Yol | Açıklama |
|---|---|---|
| GET | `/?clinicId=&invoiceId=&from=&to=&page=&size=` | Kliniğin komisyon kayıtları (mama/ilaç satırları, KDV hariç matrah). Deneme aboneliğinde kesilen faturada kayıt oluşmaz (`commissionPolicy = NO_COMMISSION`) |

## Muayene – `/api/v1`

| Metot | Yol | Açıklama |
|---|---|---|
| POST | `/encounters` | Randevusuz muayene (`animalId`, `chiefComplaint`) |
| GET | `/encounters`, `/encounters/{id}` | Liste / detay |
| PATCH | `/encounters/{id}` | Güncelleme |
| POST | `/encounters/{id}/examination` · `/diagnosis` · `/plan` | Akış adımları |
| POST | `/encounters/{id}/close` | Kapatma (randevu `COMPLETED`) |
| POST | `/encounters/{id}/addenda` | Kapanmış muayeneye ek not |
| DELETE | `/encounters/{id}` | Silme (bağlı kayıt varsa `409`) |
| POST / GET | `/encounters/{id}/treatments` | Tedavi kalemi ekle / listele |
| DELETE | `/encounters/{id}/treatments/{treatmentId}` | Tedavi kalemi sil |
| GET | `/animals/{animalId}/history` | Hayvanın muayene geçmişi |
| POST / GET / DELETE | `/encounters/{id}/attachments`, `/attachments/{id}/content`, `/attachments/{id}` | Dosya ekleri (multipart, ≤ 10 MB) |

## Aşılar – `/api/v1`

| Metot | Yol |
|---|---|
| POST / GET | `/vaccine-protocols` (`vaccine_protocols.manage`) |
| GET | `/vaccine-protocols/{id}` |
| POST | `/vaccine-protocols/{id}/approve`, `/retire` |
| GET | `/animals/{animalId}/vaccine-template` |
| POST | `/vaccinations` (`encounterId` zorunlu; `protocolDoseId` veya `vaccineName`) |
| DELETE | `/vaccinations/{id}` |
| GET | `/animals/{animalId}/vaccinations`, `/animals/{animalId}/vaccinations/due`, `/vaccinations/due` |

## Örnek Akış

```http
GET  /api/auth/organizations              → giriş combobox'ı (public)
POST /api/auth/sign-in                    → tek giriş (organizasyon seç + e-posta + şifre), panel tespiti
POST /api/auth/login                      → accessToken
POST /api/v1/appointments/{id}/encounter  → muayene başlar
POST /api/v1/encounters/{id}/treatments   → tedavi ekle
POST /api/v1/vaccinations                 → aşı kaydı
GET  /api/v1/appointments/{id}/clinical-summary
POST /api/v1/encounters/{id}/close        → randevu COMPLETED
```
