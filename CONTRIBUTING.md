# Katkı rehberi (Grup 16)

Ekip: **Fırat · Berra · Özge**. Herkes kendi işini kendi adıyla, **Issue → branch → Pull Request → review** akışıyla ekler. `main` dalına doğrudan commit atılmaz.

## 1. Başlangıç (bir kez)

```bash
git clone <repo-adresi> vetclinic
cd vetclinic
git config user.name "Ad Soyad"          # commit'lerde görünecek ad
git config user.email "github-e-postan"   # GitHub hesabındaki e-posta (katkı grafiği buna göre sayılır)
```

Kurulum ve çalıştırma: [README.md](README.md). Kısaca: Node.js 24+, JDK 21, Docker Desktop → `npm install`, `npm run db:up`, `npm run db:setup`, `npm run seed`, `npm run api`, `npm run web`.

## 2. Bir görevin akışı

1. **Issue:** GitHub'da görev için Issue aç (şablon: "Görev"). Kendini ata (Assignees), uygun etiketi seç.
2. **Branch:** güncel `main`'den kendi dalını aç. Ad: `ad/kisa-aciklama`.
   ```bash
   git checkout main && git pull
   git checkout -b berra/muayene-arama
   ```
3. **Geliştir ve commit'le:** küçük, anlamlı commit'ler. Mesajlar Türkçe ve ne yapıldığını anlatır:
   `Muayene listesine hasta adı / şikâyet araması eklendi`
4. **Test et** (Docker'da PostgreSQL açıkken, repo kökünden):
   - Backend: `npm run test:api` (tek sınıf: `node scripts/gradle.mjs test --tests "*SınıfIT"`)
   - Web: `cd apps/web && npx tsc -b && npx vitest run`
   - Mobil: `cd apps/mobile && npx tsc --noEmit`
5. **Push ve Pull Request:**
   ```bash
   git push -u origin berra/muayene-arama
   ```
   GitHub'da `main`'e PR aç. Açıklamaya `Closes #<issue-no>` yaz, şablondaki kontrol listesini doldur.
6. **Review:** PR'ı bir ekip arkadaşı inceler ve **en az bir anlamlı yorum** yazar (soru, öneri ya da onay gerekçesi). Sıra:
   - Fırat → Berra'nın PR'larını
   - Berra → Özge'nin PR'larını
   - Özge → Fırat'ın PR'larını
7. **Birleştirme:** CI yeşil ve review onaylıysa PR'ı açan kişi birleştirir ("Squash and merge" değil, normal "Merge"; böylece her commit kişinin adıyla kalır). Branch silinebilir.

## 3. Kurallar

- Başkasının dalına commit atma; değişiklik önerisini review yorumu olarak yaz.
- **Uygulanmış bir Flyway migration'ını asla değiştirme.** Şema değişikliği yeni dosyayla yapılır: `apps/api/src/main/resources/db/migration/V32__...sql` (sıradaki numara). Aynı anda iki kişi migration ekleyecekse numarayı Issue'da paylaşın.
- Backend kodunda mevcut kalıplara uy: modül paketi (`controller / service / repository / dto`), SQL yalnız repository'de, okumalar da `@Transactional(readOnly = true)` (RLS için), yetki `@PreAuthorize`, Türkçe hata mesajları. Ayrıntı: [docs/architecture.md](docs/architecture.md), [apps/api/docs/DEVELOPMENT.md](apps/api/docs/DEVELOPMENT.md).
- Yeni davranışa test ekle (backend `*IT` / `*Test`, web Vitest).
- Kod değiştiren görevlerde ilgili dokümanı da güncelle (`docs/architecture.md`, `docs/requirements.md`); üretilen dokümanlar için `npm run docs`.
- Rapor bölümleri de Git'e girer: taslağı `docs/rapor/<bölüm>.md` olarak PR ile ekle, Word'e oradan aktar. Yalnız Word'de yapılan iş Git'te görünmez.
- `.env` dosyalarını, parolaları ve kişisel verileri commit'leme.

## 4. Görev listesi

Kişi başı tarihli görevler, kabul ölçütleri ve test adımları ekip paketlerinde (`05-gorevler.md`). Her görev için bir Issue açılır; GitHub Projects panosunda (Yapılacak → Devam ediyor → İncelemede → Tamamlandı) takip edilir.
