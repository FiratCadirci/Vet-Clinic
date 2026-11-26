# Resepsiyon Paneli – Ekran Tasarımı

Resepsiyon üç iş yapar: **karşılama, kayıt, tahsilat**. Ekranlar bu akışa göre planlanmıştır. Her ekran için mevcut backend durumu işaretlidir:

- ✅ Backend hazır
- 🟡 Kısmen hazır (eksikler belirtildi)
- ❌ Backend yok (şemada tablo var, API yok)

> Şema (`db/VetClinic_PostgreSQL_Kurulum_TR.sql`) değiştirilmez. Yeni tablo/kolon gerekirse Flyway V2+ migration önerilir ve onay beklenir.

## Genel Tasarım İlkeleri

1. **Global arama** her ekranın üstünde sabit: sahip adı, telefon, hayvan adı, hasta no / çip no. Sonuçlar "Sahip" ve "Hayvan" olarak gruplanır.
2. **Hızlı eylemler** (Yeni randevu, Yeni müşteri, Ödeme al) üst çubukta, en fazla 1–2 tık.
3. **Sahip ↔ Hayvan kartları çift yönlü bağlı**: sahipten hayvana, hayvandan (birincil) sahibe tek tıkla geçiş.
4. **Şube bağlamı**: üstte şube seçici; yazma isteklerinde `X-Clinic-Id` başlığı gönderilir.
5. Tarih gösterimi `yyyy-MM-dd HH:mm`; çakışma/doğrulama hataları (`409`, `422`) formda anlamlı mesaj olarak gösterilir.

## Yetkiler

| Yetki | Kullanım |
|---|---|
| `clients.read` / `clients.write` | Müşteri ve hayvan görüntüleme / düzenleme |
| `appointments.manage` | Randevu ve check-in |
| `clinical.read` | Muayene/aşı özetlerini görme (salt okunur) |
| `billing.manage` | Kasa, tahsilat |
| `inventory.read` / `inventory.write` | Stok görme / satış |
| `reports.read` | Raporlar |

---

## 1. Ana Sayfa / Günlük Özet — 🟡

**Amaç:** Güne bir bakışta hâkim olmak.

| Kart | İçerik | Veri kaynağı |
|---|---|---|
| Bugünün randevuları | Saat, hayvan, sahip, hekim, tür, durum | `GET /api/v1/appointments` 🟡 |
| Klinikte bekleyenler | Durumu `CHECKED_IN` olanlar, bekleme süresi | `GET /appointments` 🟡 |
| Geciken aşılar | Vadesi geçmiş/yaklaşan aşılar | `GET /api/v1/vaccinations/due` ✅ |
| Bugünkü kasa özeti | Tahsilat toplamı, yönteme göre dağılım | `GET /billing/cashier/daily` ✅ |

**Backend eksikleri:** Randevu listesinde tarih / durum / hekim / şube filtresi yok (şu an tümünü döner); özet endpoint'i yok.

## 2. Randevu Takvimi — 🟡

- **Görünüm:** Gün / hafta; sütunlar hekim bazlı; hekime göre filtre.
- **Renk kodu (`appointment_type`):** `EXAM` muayene, `VACCINATION` aşı, `SURGERY` operasyon, `FOLLOW_UP` kontrol, `GROOMING` bakım, `LAB`, `IMAGING`, `OTHER`.
- **Durum rozeti:** `REQUESTED`, `BOOKED`, `CONFIRMED`, `CHECKED_IN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `NO_SHOW`.
- **Hızlı randevu:** Takvimde boş slota tıkla → müşteri ara → hayvan seç → tür/hekim/saat/sebep → kaydet. Kanal (`booking_channel`): STAFF, PHONE, WEB, MOBILE, WHATSAPP.
- **Çakışma:** Aynı hekim veya aynı hayvan için çakışan randevu DB `EXCLUDE` kısıtıyla engellenir; `409/422` hatası "Bu saat dolu" olarak gösterilir. Hekim çalışma saati dışı da DB'de reddedilir.
- **Eylemler:** Onayla, iptal et (sebep zorunlu), no-show işaretle, yeniden planla.

**Backend:** `POST /api/v1/appointments` ✅, `GET /appointments`, `GET /appointments/{id}` 🟡.
**Eksikler:** tarih aralığı/hekim/durum filtresi, durum geçiş endpoint'leri (confirm/cancel/no-show/reschedule), DTO (şu an entity doğrudan dönüyor), `appointments.manage` yetki kontrolü.

## 3. Hasta Kabul / Bekleme Listesi (Check-in) — 🟡

Güncel frontend sözleşmesi: [Kabul ve Bekleme API Contract](ADMISSION_WAITING_API_CONTRACT.md). Bu bölüm için uygulama ayrıntıları orada tanımlıdır.

- **Geldi:** `PATCH /api/v1/appointments/{id}/check-in`; `BOOKED/CONFIRMED → CHECKED_IN`, `appointments.manage` gerekir.
- **Bekleyenler / muayenedekiler:** `CHECKED_IN` / `IN_PROGRESS` filtreli randevu listesi. Aktif şube için `clinicId` filtresi eklenir; önceki günden kalan açık kayıtlar tarih sınırıyla gizlenmez.
- **Randevusuz kabul:** Ayrı kuyruk API'si yoktur. Randevu oluşturup check-in yapmak mümkündür; iki istek atomik değildir ve randevu çakışma kurallarına tabidir.
- **Muayene başlatma:** `PATCH /appointments/{id}/start`, `appointments.manage` + `clinical.write` ve oturumdaki kullanıcının aktif hekim profilini gerektirir. `POST /encounters` da hekim işlemidir; resepsiyon bekleme kaydı değildir.
- **Hekim gruplama:** Randevuda atanmış hekimi gösterir; gerçek muayene hekimi başlatan kullanıcı olabilir.

**Eksikler:** API'de geliş zamanı/bekleme süresi, FIFO sıra, öncelik, oda, çağırma ve ayrı resepsiyon walk-in kabul işlemi yoktur. Bunlar frontend'de kalıcı özellikmiş gibi üretilmez.

## 4. Hayvan Sahipleri (Müşteriler) — ✅

- **Liste:** arama (`q`), aktif/pasif filtresi, sayfalama → `GET /api/v1/clients`.
- **Kart:** ad/ünvan, telefon, e-posta, adres, iletişim tercihi (`preferredChannel`), pazarlama izni, iletişim notları, bireysel/kurumsal ve vergi bilgileri.
- **Sahibin hayvanları:** `GET /clients/{id}/animals`.
- **Eylemler:** oluştur, düzenle, pasife al/aktifleştir.
- **Mükerrer uyarısı:** Kayıt sırasında yanıtta `possibleDuplicates` gelirse "Bu müşteri zaten var mı?" uyarısı gösterilir.
- **Bakiye/borç:** ✅ `GET /billing/clients/{id}/balance`.

## 5. Hastalar (Hayvanlar) — ✅

- **Liste/arama:** `GET /api/v1/animals?q=&speciesId=` (tür filtresi).
- **Kart sekmeleri:**
  - *Genel:* hasta no, ad, tür, ırk, cinsiyet, doğum tarihi (tahmini mi), renk, ayırt edici işaret, kısırlaştırma, notlar, ölüm tarihi.
  - *Sahipler:* çoklu sahip, birincil sahip ata, sahipliği bitir (`/animals/{id}/owners...`).
  - *Aşı karnesi:* `GET /animals/{id}/vaccinations`, yaklaşan: `/vaccinations/due`, şablon: `/vaccine-template`.
  - *Muayene geçmişi:* `GET /animals/{id}/history` (salt okunur, `clinical.read`).
  - *Ek bilgiler (kilo, alerji, çip no):* şemada `animal_weights`, `animal_allergies`, `animal_identifiers` var; **API ✅** (`/animals/{id}/weights|allergies|identifiers|health-summary`; çip no global aramada) — çip no ile arama ve kronik hastalık/alerji uyarısı için eklenmeli.
- **Eylemler:** yeni hayvan, düzenle, vefat kaydı.
- Tür/ırk seçimleri: `GET /species`, `GET /species/{id}/breeds`.

## 6. Kasa / Tahsilat — 🟡

Şemada `invoices`, `invoice_lines`, `payments`, `invoice_payments`, `v_invoice_totals` hazır (fatura post trigger'ı stok ve komisyonu DB'de işler); **controller/servis yok.**

- **Açık hesaplar:** Muayene sonrası açılan fatura listesi (taslak/açık), kalemler (tedavi, ürün, hizmet).
- **Ödeme alma:** nakit, kart, havale; kısmi ödeme; (taksit için şemada destek doğrulanmalı).
- **Fatura/fiş:** kes, görüntüle, yazdır.
- **Borç takibi:** vadesi geçmiş faturalar, müşteri bakiyesi.
- **Gün sonu kasa:** yönteme göre toplam, kasa kapatma (kasa kapatma tablosu şemada yok → migration önerisi gerekir).
- Yetki: `billing.manage`.

---

## Sık İhtiyaç Duyulan Ekranlar

| Ekran | Durum | Not |
|---|---|---|
| **Aşı & hatırlatma takibi** | 🟡 | Vadesi gelen aşı listesi ✅ (`/vaccinations/due?status=&days=`). Toplu SMS/WhatsApp gönderimi ❌: `notification_outbox` ve `notification_preferences` tabloları var, API yok. Müşterinin `preferredChannel` ve izni kontrol edilmeli. |
| **Stok / ürün** | ❌ | `products`, `inventory_lots`, `v_inventory_on_hand`, `minimum_stock` mevcut; kritik seviye uyarısı ve hızlı satış için API gerekir (`inventory.read/write`). |
| **Hizmet ve fiyat listesi** | ❌ | `service_catalog` (kod, ad, kategori, varsayılan fiyat, KDV, süre) mevcut; salt okunur liste yeterli. |
| **Muayene kaydı (salt okunur)** | ✅ | `GET /encounters`, `/encounters/{id}`, `/appointments/{id}/clinical-summary`, ekler (`clinical.read`). Resepsiyonda düzenleme kapalı. |
| **Raporlar** | ❌ | Günlük ciro, randevu doluluğu, en çok yapılan işlem. `v_financial_monthly_summary` var; `reports.read`. |

## Önerilen Menü

```
[Global Arama ................]   [+ Randevu] [+ Müşteri] [Ödeme Al]   [Şube ▾]

Özet | Takvim | Kabul/Bekleme | Müşteriler | Hastalar | Kasa
Aşı Takibi | Stok | Fiyat Listesi | Raporlar
```

## Ekranlar Arası Geçişler

- Müşteri kartı → Hayvanları → Hayvan kartı → (birincil) Sahip kartı
- Hayvan kartı → "Randevu oluştur" (müşteri/hayvan önceden dolu)
- Randevu → Check-in → Muayene (hekim) → Fatura → Ödeme
- Aşı takibi satırı → Sahip ara/mesaj → Randevu oluştur

## Uygulama Sırası (Öneri)

1. **Hazır API ile başla:** Müşteriler, Hastalar, Global arama, Aşı takibi (liste), Muayene salt okunur görünümü.
2. **Randevu API'sini tamamla:** filtreler, durum geçişleri (check-in/iptal/no-show), DTO + yetki → Takvim, Kabul, Ana sayfa.
3. **Kasa modülü:** fatura + ödeme + gün sonu (hata eşlemeleri: `23514`, `23503`, `RAISE EXCEPTION` → 422/409).
4. **Bildirim:** `notification_outbox` üzerinden hatırlatma gönderimi.
5. **Stok, fiyat listesi, raporlar.**

## Backend Eksik Özeti

| # | Eksik | Etkilediği ekran |
|---|---|---|
| 1 | Randevu filtreleri + durum geçiş endpoint'leri + DTO | Özet, Takvim, Kabul |
| 2 | Global arama (müşteri+hayvan, telefon/çip no) | Tümü |
| 3 | Hayvan kilo/alerji/çip no API'si | Hasta kartı |
| 4 | Fatura/ödeme/kasa API'si | Kasa, bakiye, özet |
| 5 | Bildirim gönderim API'si | Aşı hatırlatma |
| 6 | Ürün/stok ve hizmet kataloğu API'si | Stok, fiyat listesi |
| 7 | Rapor endpoint'leri | Raporlar |

> **Not:** Gün sonu kasa kapatma için şemada tablo yok; V20 migration önerisi onay bekliyor.
