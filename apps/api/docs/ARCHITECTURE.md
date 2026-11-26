# Mimari

## Genel Bakış

Katmanlı modüler monolit. Her modül kendi paketinde `controller / service / domain(model) / repository / dto` katmanlarına ayrılır. Entity'ler API'den doğrudan dönmez; DTO (record) + MapStruct kullanılır. Cross-aggregate ilişkiler `@ManyToOne` yerine düz `UUID` alanlarıyla tutulur.

```
HTTP → Controller (@PreAuthorize) → Service (@Transactional) → Repository (JPA / JdbcTemplate) → PostgreSQL (RLS)
```

## Modüller

| Modül | Sorumluluk |
|---|---|
| `auth` | Giriş, refresh token, şifre değiştirme, platform yöneticisi girişi, JWT |
| `organization`, `clinic` | Kiracı (organization), klinik grubu ve şubeler |
| `user` | Kullanıcı, klinik bazlı rol atamaları, veteriner profili |
| `client`, `animal` | Müşteri ve hasta (hayvan); sahiplik, tür/ırk |
| `appointment` | Randevu ve randevuya bağlı klinik özet |
| `encounter` | Muayene akışı, tedavi kalemleri, ek notlar (addenda), dosya ekleri |
| `vaccine` | Aşı protokolleri, aşı kayıtları, yaklaşan/geciken aşılar |
| `product`, `billing`, `inventory`, `commission`, ... | Şemada tanımlı; kısmen/ilerleyen aşamada |
| `common` | Tenant bağlamı, transaction yöneticisi, hata yönetimi, tarih, sıra numarası, depolama, olaylar, audit |

## Çok Kiracılılık ve RLS

- Kiracı = `organization_id`, şube = `clinic` (`clinic_id`). Şema adı: `vet`.
- Tüm tablolarda `FORCE ROW LEVEL SECURITY` açıktır.
- `TenantAwareTransactionManager` her transaction'da `app.organization_id`, `app.user_id`, `app.clinic_id` değerlerini set eder. Bu nedenle okuma işlemleri de `@Transactional(readOnly = true)` içinde yapılmalıdır.
- Aynı klinik grubundaki kardeş şubeler birbirinin verisini (yetki varsa) görebilir; farklı grup/organizasyon kayıtları 404 döner.
- İstekte şube `X-Clinic-Id` başlığı ile seçilir.

## Kuralların Veritabanında Tutulması

İş kuralları büyük oranda DB'dedir ve Java'da tekrar yazılmaz:

- Randevu çakışması: `EXCLUDE` constraint
- Stok guard trigger'ı, fatura post trigger'ı (stok/komisyon)
- Kapanmış muayene ve audit/stok hareketleri immutable (trigger)
- Bir randevuya aynı anda tek açık muayene (unique index)

DB hataları (SQLState `23P01`, `23505`, `23503`, `23514` ve `RAISE EXCEPTION`) global hata yönetiminde anlamlı HTTP yanıtlarına çevrilir (ör. 409/422).

## Randevu → Muayene → Tedavi/Aşı Zinciri

```
Appointment (CONFIRMED / CHECKED_IN)
   └─ POST /appointments/{id}/encounter → Encounter (appointment_id) ; randevu IN_PROGRESS
        ├─ Tedavi kalemleri (encounter_treatments)
        ├─ Aşı kayıtları (vaccine_records.encounter_id)
        └─ close → randevu COMPLETED ; (açık muayene silinirse randevu CHECKED_IN)
```

- Randevusuz (walk-in/kontrol) muayene de açılabilir: `POST /encounters` (`appointmentId = null`). Bu durumda randevu durumuna dokunulmaz.
- Muayene durumları: `ADMISSION → EXAMINATION → DIAGNOSIS → PLAN → CLOSED`.
- `GET /appointments/{id}/clinical-summary` randevuya bağlı muayeneleri tedavi ve aşılarıyla birlikte salt okunur döndürür.

## Modül Bağımlılıkları

`vaccine → encounter` bağımlıdır; döngü oluşmaması için randevu özeti (`AppointmentClinicalService`) `appointment` modülünde ve her iki servisi kullanır.

## Kimlik Doğrulama ve Yetkilendirme

- JWT access token (15 dk) + refresh token (14 gün).
- Yetkiler `Permission` sabitleriyle ifade edilir ve `@PreAuthorize("hasAuthority(...)")` ile uygulanır: `clients.read/write`, `appointments.manage`, `clinical.read/write/delete`, `inventory.read/write`, `billing.manage`, `users.manage`, `reports.read`, `audit.read`, `clinic_roles.manage`, `vaccine_protocols.manage`, `subscriptions.read`, `commissions.read`, `diagnostics.manage`.
- Platform yöneticisi ayrı uç noktalar (`/api/platform/auth`) kullanır; erişimleri loglanır.

## Dosya Ekleri

Muayene ekleri `STORAGE_ROOT` altında yerel diskte saklanır (en fazla 10 MB).

## Veritabanı Migration'ları

`src/main/resources/db/migration`: V1 baseline, V2–V6 platform/RLS fonksiyonları ve politikaları, V7 refresh token, V8–V12 klinik grubu ve izolasyon, V13 zorunlu şifre değişimi, V14 hasta numarası sayaçları, V15 klinik kayıtlar (muayene, tedavi, aşı, ek).
