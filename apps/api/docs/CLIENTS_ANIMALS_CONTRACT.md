# Müşteriler Ekranı – Frontend API Contract (Hayvan Sahipleri + Hayvanlar)

Bu doküman mevcut backend koduna göre yazılmıştır. "Müşteri" = **hayvan sahibi (client)**, "Hasta" = **hayvan (animal)**.

## 1. Ortak kurallar

| Konu | Kural |
|---|---|
| Base URL | `/api/v1` |
| Kimlik doğrulama | `Authorization: Bearer <JWT>` (`/api/auth` ile alınır) |
| Şube başlığı | `X-Clinic-Id: <uuid>` → **müşteri oluşturma** ve **hasta oluşturma** için zorunlu |
| Yetkiler | Okuma: `clients.read` · Yazma: `clients.write` |
| Yanıt formatı | Ham JSON (sarmalayıcı yok) |
| Tarih | `yyyy-MM-dd` (tarih), `yyyy-MM-dd HH:mm` (tarih-saat) |
| Tenant | JWT'den gelir; başka kuruma ait id → `404` |

### Sayfalama
`?page=0&size=20` (page 0-tabanlı, varsayılan size 20, **maks 200**).
```json
{ "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0, "first": true, "last": true }
```

### Hata gövdesi
```json
{ "timestamp": "2025-01-01T10:00:00Z", "status": 422, "code": "BUSINESS_RULE_VIOLATION",
  "message": "Hastanın en az bir güncel sahibi olmalı.", "path": "/api/v1/animals/..", "details": {} }
```
| HTTP | code | Anlamı / UI davranışı |
|---|---|---|
| 400 | `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `MISSING_HEADER` | Form alanı hatası; `message` gösterilir |
| 401 | `UNAUTHORIZED` | Login'e yönlendir |
| 403 | `FORBIDDEN` | Yetki yok; butonu gizle/pasif yap |
| 404 | `NOT_FOUND` | Kayıt yok |
| 409 | `DB_CONFLICT` / özel code | Çakışma (örn. aynı çip no, zaten sahip) |
| 422 | `BUSINESS_RULE_VIOLATION`, `DB_RULE_VIOLATION` | İş kuralı ihlali; `message` kullanıcıya gösterilir |

---

## 2. Hayvan Sahipleri (Clients)

### 2.1 Liste / Arama – `GET /clients`
Query: `q`, `active` (true/false), `page`, `size`. Sıralama: ada göre A→Z. Yanıt: `PageResponse<ClientListItemResponse>`.

`q` şunlarla eşleşir: ad/soyad/ünvan, e-posta, telefon (boşluk, parantez, tire ve `+90`/`0`/`90` farkları normalize edilir), **sahibin hayvanlarının adı** ve **çip/kimlik no**.

Her satır `ClientResponse` alanlarına ek olarak hayvan özeti taşır (tek toplu sorgu, N+1 yok):
```json
{
  "id": "uuid", "displayName": "Ayşe Yılmaz", "phone": "5551112233", "active": true,
  "animalCount": 7,
  "animals": [
    { "id": "uuid", "name": "Badem", "speciesId": "uuid", "speciesName": "Köpek",
      "deceased": false, "isPrimaryOwner": true }
  ],
  "matchedAnimalIds": ["uuid"]
}
```
- `animals` en çok **5** hayvan içerir; `animalCount` gerçek toplamdır (5'ten fazlaysa "+N" gösterin, tamamı için `GET /clients/{id}/animals`).
- `matchedAnimalIds`: arama hayvan adı veya çip ile eşleştiyse eşleşen hayvanlar (UI vurgulama); aksi halde boş.

### 2.2 Detay – `GET /clients/{id}` → `ClientResponse`
```json
{
  "id": "uuid", "clientType": "PERSON", "displayName": "Ayşe Yılmaz",
  "firstName": "Ayşe", "lastName": "Yılmaz", "companyName": null,
  "taxOffice": null, "taxNumber": null,
  "preferredChannel": "SMS", "marketingConsent": true, "communicationNotes": "Akşam aranmasın",
  "active": true, "phone": "5551112233", "email": "ayse@ornek.com",
  "address": { "addressLine": "Atatürk Cd. No:5", "city": "İstanbul", "district": "Kadıköy",
               "neighborhood": "Moda", "postalCode": "34710", "countryCode": "TR" },
  "createdAt": "...", "updatedAt": "..."
}
```
`possibleDuplicates` yalnızca update yanıtında ve eşleşme varsa gelir (create için bkz. 2.3: 409).

### 2.3 Oluştur – `POST /clients` (201, `X-Clinic-Id` zorunlu)
```json
{
  "clientType": "PERSON",
  "firstName": "Ayşe", "lastName": "Yılmaz",
  "preferredChannel": "SMS", "marketingConsent": false, "communicationNotes": null,
  "contact": { "phone": "5551112233", "email": "ayse@ornek.com" },
  "address": { "addressLine": "Atatürk Cd. No:5", "city": "İstanbul", "countryCode": "TR" }
}
```
| Alan | Kural |
|---|---|
| `clientType` | zorunlu: `PERSON` \| `BUSINESS` |
| `PERSON` | `firstName` + `lastName` zorunlu (≤100) |
| `BUSINESS` | `companyName` zorunlu (≤200); `taxOffice` ≤120, `taxNumber` ≤30 |
| `preferredChannel` | `SMS`, `EMAIL`, `PHONE`, `WHATSAPP`, `NONE` |
| `contact.phone` | ≤40, geçersizse 400 "Telefon numarası geçersiz." |
| `contact.email` | geçerli e-posta, ≤254 |
| `address.addressLine` | adres gönderilirse zorunlu; `city`/`district` ≤100, `neighborhood` ≤120, `postalCode` ≤20, `countryCode` 2 harf |

**Mükerrer kontrolü (kayıt ÖNCESİ):** İsteğe isteğe bağlı `"confirmDuplicate": true` eklenebilir. Aynı telefon (son 10 hane) veya e-posta başka müşteride varsa ve `confirmDuplicate` yoksa müşteri **oluşturulmaz**, 409 döner:
```json
{
  "code": "DUPLICATE_CLIENT_CANDIDATE",
  "message": "...",
  "details": { "possibleDuplicates": [
    { "id": "uuid", "displayName": "Ayşe Y.", "phone": "5551112233", "matchedOn": "EMAIL,PHONE", "animalCount": 2 }
  ] }
}
```
`matchedOn`: `PHONE`, `EMAIL` veya `EMAIL,PHONE`. UI: "Benzer müşteri bulundu" diyaloğu → "Mevcut kayda git" veya "Yine de oluştur" (aynı gövde + `confirmDuplicate: true` → 201).

### 2.4 Güncelle – `PATCH /clients/{id}`
Create ile aynı alanlar, `clientType` **hariç** (değiştirilemez). `null`/gönderilmeyen alan değişmez. Telefon/e-posta değişirse yanıtta yine `possibleDuplicates` gelebilir.

### 2.5 Pasife al / aktifleştir
`POST /clients/{id}/deactivate` · `POST /clients/{id}/activate` → güncel `ClientResponse`.
Pasif müşteri yeni bir hastaya **bağlanamaz** (422).

### 2.6 Müşterinin hayvanları – `GET /clients/{clientId}/animals?page=&size=`
`PageResponse<AnimalResponse>` (yalnızca bu müşterinin güncel hayvanları, bkz. 3.2).

**Sahip bağlamında hayvan ekleme – `POST /clients/{clientId}/animals`** (201, `clients.write`, `X-Clinic-Id` zorunlu)
Gövde `POST /animals` ile aynıdır, `owners` **gönderilmez**; müşteri otomatik **birincil sahip** olarak tek transaction'da bağlanır. Yanıt: `AnimalResponse` (`primaryOwnerId` = `clientId`).
```json
{ "name": "Zeytin", "speciesId": "uuid", "sex": "MALE" }
```
Pasif müşteri → 422 `BUSINESS_RULE_VIOLATION`. Arayüz hayvan eklemek için bu ucu kullanmalıdır; `POST /animals` geriye dönük uyumluluk için durur.

### 2.7 Bakiye / borç paneli (ek yetki: `billing.manage`)
- `GET /billing/clients/{id}/balance` – müşteri bakiyesi
- `GET /billing/invoices?clientId=...` – müşteri faturaları (bkz. `docs/API.md` Billing bölümü)

Yetkisi olmayan kullanıcıda panel gizlenmelidir (403).

### 2.8 Randevu geçmişi
`GET /appointments?clientId={id}&page=&size=` (bkz. `docs/API.md`).

---

## 3. Hayvanlar (Patients)

### 3.1 Referans listeleri (form dropdown'ları)
- `GET /species` → `[{ "id", "code", "name", "scientificName" }]` (aktif türler)
- `GET /species/{speciesId}/breeds` → `[{ "id", "speciesId", "name", "mixed" }]`

Akış: Tür seçilince ırkları yükle. Seçilen ırk türe ait değilse 422 "Irk, seçilen türe ait değil."

### 3.2 Liste / Arama – `GET /animals`
Query: `q` (hayvan adı, hasta no, **sahip adı** veya **çip/kimlik no** içeren), `speciesId`, `page`, `size`. Sıralama: ada göre.

### 3.3 Detay (hasta kartı üst bölümü) – `GET /animals/{id}` → `AnimalResponse`
```json
{
  "id": "uuid", "patientNo": "P-000123", "name": "Pamuk",
  "speciesId": "uuid", "speciesName": "Kedi", "breedId": "uuid", "breedName": "Van",
  "sex": "FEMALE", "birthDate": "2020-05-01", "birthDateEstimated": false,
  "color": "Beyaz", "distinguishingMarks": null,
  "sterilized": true, "sterilizationDate": "2022-01-10",
  "deceasedAt": null, "profileNotes": null,
  "createdAt": "...", "updatedAt": "...",
  "primaryOwnerId": "uuid",
  "owners": [
    { "id": "uuid", "clientId": "uuid", "clientName": "Ayşe Yılmaz", "relationshipType": "OWNER",
      "primaryContact": true, "legalOwner": true, "validFrom": "2020-06-01", "validTo": null }
  ]
}
```
`primaryOwnerId`: birincil iletişim sahibinin **clientId**'si (yoksa `null`); eski `/hastalar/:id` adreslerini `/musteriler/{primaryOwnerId}` sayfasına yönlendirmek için kullanılır.
`owners` içinde `validTo != null` olanlar **eski** sahiptir (geçmiş sekmesi). Sahip adına tıklayınca `/clients/{clientId}` → sahipten hayvana / hayvandan sahibe tek tıkla geçiş.

### 3.3.1 Çipten bul – `GET /animals/by-chip/{chipNo}` (`clients.read`)
Boşluk/tire temizlenir, büyük/küçük harf duyarsızdır. Yanıt `AnimalResponse[]` (owners + primaryOwnerId dahil; aynı numara birden fazla hayvanda varsa hepsi). Kayıt yoksa 404.

### 3.4 Oluştur – `POST /animals` (201, `X-Clinic-Id` zorunlu)
```json
{
  "name": "Pamuk", "speciesId": "uuid", "breedId": "uuid",
  "sex": "FEMALE", "birthDate": "2020-05-01", "birthDateEstimated": false,
  "color": "Beyaz", "distinguishingMarks": null,
  "sterilized": true, "sterilizationDate": "2022-01-10", "profileNotes": null,
  "owners": [ { "clientId": "uuid", "relationshipType": "OWNER", "isPrimaryContact": true, "isLegalOwner": true } ]
}
```
| Alan | Kural |
|---|---|
| `name` | zorunlu, ≤150 |
| `speciesId` | zorunlu |
| `sex` | `MALE`, `FEMALE`, `UNKNOWN`, `INTERSEX` |
| `color` | ≤100 |
| `owners` | **en az 1**, aynı müşteri tekrar edemez, **tam bir** kişi `isPrimaryContact: true` olmalı |
| `relationshipType` | `OWNER`, `CO_OWNER`, `GUARDIAN`, `FOSTER`, `EMERGENCY_CONTACT`, `OTHER` |
| Tarihler | doğum/kısırlaştırma gelecekte olamaz; kısırlaştırma doğumdan önce olamaz |
| Sahip | pasif müşteri 422; olmayan müşteri 404 |

`patientNo` sunucuda üretilir.

### 3.5 Güncelle – `PATCH /animals/{id}`
Alanlar: `name`, `breedId`, `sex`, `birthDate`, `birthDateEstimated`, `color`, `distinguishingMarks`, `sterilized`, `sterilizationDate`, `profileNotes`. **Tür değiştirilemez.** Gönderilmeyen alan değişmez. `name` boş olamaz.

### 3.6 Sahiplik yönetimi
| İşlem | Endpoint | Gövde |
|---|---|---|
| Sahip ekle | `POST /animals/{id}/owners` (201) | `{ "clientId", "relationshipType", "isPrimaryContact", "isLegalOwner" }` |
| Birincil iletişim yap | `POST /animals/{id}/owners/{ownerId}/primary` | – |
| Sahipliği bitir | `POST /animals/{id}/owners/{ownerId}/end` | – |

`ownerId` = `owners[].id` (sahiplik kaydı id'si, clientId **değil**). Hepsi güncel `AnimalResponse` döner.
Hatalar: zaten güncel sahip → 409; son sahip veya birincil iletişim sahibi bitirilemez → 422 (önce başka birini birincil yap).

### 3.7 Vefat – `POST /animals/{id}/deceased`
```json
{ "deceasedAt": "2025-03-01" }
```
Gelecek tarih veya doğumdan önce → 400; zaten vefat → 409.

---

## 4. Hasta kartı sağlık sekmesi (`/animals/{animalId}/...`)

| Endpoint | Açıklama |
|---|---|
| `GET /health-summary` | `{ latestWeight, activeAllergies[], identifiers[] }` – kart başlığı için tek çağrı |
| `GET /weights` · `POST /weights` (201) | Kilo geçmişi (yeniden eskiye) |
| `GET /allergies?activeOnly=false` · `POST /allergies` (201) · `POST /allergies/{allergyId}/resolve` | Alerji; resolve ile pasifleşir |
| `GET /identifiers` · `POST /identifiers` (201) | Çip/pasaport vb. |

**Kilo** `POST`: `{ "weightKg": 4.250, "measuredAt": "2025-03-01 10:30", "measurementSource": "CLINIC", "note": null }`
`weightKg` 0.001–1999.999 zorunlu; `measurementSource`: `CLINIC|OWNER|IMPORTED|OTHER`; `measuredAt` gelecekte olamaz (422). Yanıt: `{ id, weightKg, measuredAt, measurementSource, note, recordedBy }`.

**Alerji** `POST`: `{ "substance": "Penisilin", "reaction": "Kaşıntı", "severity": "MODERATE", "onsetDate": "2024-01-01" }`
`substance` zorunlu ≤200; `severity`: `MILD|MODERATE|SEVERE|UNKNOWN`. Yanıt: `{ id, substance, reaction, severity, onsetDate, resolvedAt, active }`.

**Kimlik** `POST`: `{ "identifierType": "MICROCHIP", "identifierValue": "900123456789012", "issuedAt": null, "expiresAt": null, "issuingAuthority": null }`
`identifierType`: `MICROCHIP|PASSPORT|TATTOO|REGISTRY|OTHER`. **Mikroçip 9–15 haneli rakam** olmalı (422). Aynı numara başka hastada varsa 409 "Bu kimlik numarası başka bir hastaya kayıtlı." Yanıt: `{ id, identifierType, identifierValue, issuedAt, expiresAt, issuingAuthority, verified }`.

## 4.1 Hasta listesi ve hasta geçmişi (personel ekranları, `patient` modülü)

| Endpoint | Yetki | Açıklama |
|---|---|---|
| `GET /patients?q=&ownerId=&page=&size=` | `clients.read` | Hasta listesi satırları (`ownerId`: sahibin güncel hayvanları) (en fazla 200/sayfa): hayvan + güncel birincil sahip (`ownerId`, `ownerName`, `ownerPhone`) + `microchipNo` + `registeredClinicId` + `speciesCode`. `q`: hasta adı, hasta no, sahip adı, sahip telefonu (0/90 öneki yok sayılır) veya çip/kimlik no |
| `GET /patients/{id}` | `clients.read` | Aynı satır, tek hasta (kart başlığı) |
| `GET /patients/{id}/history?page=&size=` | `clients.read` veya `clinical.read` | Muayene, aşı ve randevular tek kronolojik listede (yeniden eskiye) |

Geçmiş yanıtı `PageResponse` alanlarına ek olarak `hiddenCount`, `hiddenByType: { encounters, vaccinations, appointments }` ve `clinicalAccess` taşır. Satır: `{ type: ENCOUNTER|VACCINATION|APPOINTMENT, id, occurredAt, endedAt, clinicId, clinicName, status, title, veterinarianName, chiefComplaint, assessment, treatmentPlan, doseNumber, batchNumber, nextDueAt, appointmentType, encounterId }`.

Görünürlük (FG-C5, FG-J1): aynı klinik grubundaki şubeler kayıtları paylaşır; başka klinik grubunun kayıtları RLS ile görünmez. `clinical.read` yetkisi olmayan rol (ör. resepsiyon) muayene ve aşı satırlarını görmez (`clinicalAccess: false`). Görünmeyen kayıtlar listede yer almaz; yalnızca türe göre sayısı döner (`vet.count_animal_history_records`, SECURITY DEFINER, içerik döndürmez). Başka organizasyonun / erişim dışı hastanın geçmişi `404`.

## 5. Diğer bağlantılı uçlar
- `GET /search?q=` – global arama (min 2 karakter, müşteri + hayvan, ad/telefon/çip, en çok 10 sonuç).
- `GET /vaccinations/due?status=&days=` – vadesi gelen aşılar (hasta kartı aşı sekmesi için).
- `GET /appointments?animalId={id}` – hastanın randevu geçmişi.

## 6. Önerilen ekran akışı
1. **Liste ekranı:** iki sekme (Sahipler / Hastalar), üstte arama kutusu (`q`, debounce 300 ms), `active` ve `speciesId` filtreleri.
2. **Sahip kartı:** `GET /clients/{id}` + `GET /clients/{id}/animals` + bakiye. "Yeni hasta ekle" butonu → hasta formu (sahip önceden seçili).
3. **Hasta kartı:** `GET /animals/{id}` + `health-summary`; sağlık sekmesi lazy-load.
4. **Hızlı müşteri+hasta kaydı:** önce `POST /clients` (409 gelirse diyalog, onayda `confirmDuplicate: true`), dönen `id` ile `POST /clients/{id}/animals`.

## 7. Bilinen eksikler (backend'de yok)
- Müşterinin birden fazla telefon/e-postasını listeleme/düzenleme (yalnızca tek `phone`/`email` alanı).
- Mükerrer müşteri birleştirme (merge).
- "Borcu olan müşteriler" listesi filtresi.
- Hayvan fotoğrafı yükleme.
- Müşteri silme (yalnızca pasife alma var).
- Hayvan listesinde `deceased` filtresi.
