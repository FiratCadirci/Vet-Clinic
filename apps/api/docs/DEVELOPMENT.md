# Geliştirme ve Test Rehberi

## Derleme ve Çalıştırma

```powershell
.\gradlew.bat build          # derleme + test
.\gradlew.bat bootRun        # uygulamayı çalıştır
.\gradlew.bat bootJar        # çalıştırılabilir jar (build/libs)
```

## Testler

- Birim testleri: `*Test` (servis, token, filtre vb.)
- Entegrasyon testleri: `*IT` (MockMvc + gerçek PostgreSQL). RLS, trigger ve constraint'ler gerçek veritabanında doğrulanır.
- IT'ler yerel PostgreSQL üzerinde çalışır. Her çalıştırmada geçici bir `vet_it_*` veritabanı oluşturulur ve `vet_app` rolü kurulur. Gerekli değişkenler:
  - `VET_IT_URL` (örn. `jdbc:postgresql://localhost:5432`)
  - `VET_IT_ADMIN_USER` (varsayılan `postgres`)
  - `VET_IT_ADMIN_PASSWORD` (varsayılan `postgres`)
  - `VET_IT_APP_PASSWORD` (varsayılan `change_me`)
- `VET_IT_URL` verilmezse ve Docker yoksa IT'ler atlanır.

```powershell
$env:VET_IT_URL="jdbc:postgresql://localhost:5432"; $env:VET_IT_ADMIN_PASSWORD="postgres_sifren"
.\gradlew.bat test                                           # tüm testler
.\gradlew.bat test --tests "*AppointmentClinicalSummaryIT"   # tek sınıf
```

Raporlar: `build/test-results/test`, `build/reports/tests/test`.

Test yardımcıları: `ApiTestSupport` (token/get/post), `RegistrationTestData` / `TenantTestData` (tohum veri), `ClinicalTestData` (süper kullanıcı ile randevu vb. hazırlama).

## Yeni Özellik Ekleme Kontrol Listesi

1. Modül paketinde `controller / service / repository / dto` katmanlarını kullanın; entity döndürmeyin.
2. Cross-aggregate ilişkileri düz UUID olarak tutun.
3. Okuma işlemleri de `@Transactional(readOnly = true)` olmalı (RLS bağlamı için).
4. İş kuralını DB zaten uyguluyorsa Java'da tekrar yazmayın; DB hatalarını anlamlı HTTP kodlarına çevirin.
5. Yetki: `@PreAuthorize("hasAuthority(Permission.X)")`.
6. Şema değişikliği gerekiyorsa `V16+` Flyway migration'ı önerin; `db/VetClinic_PostgreSQL_Kurulum_TR.sql` dosyasını değiştirmeyin.
7. Kiracı/şube izolasyonu için çapraz klinik 404/403 testi ekleyin.

## Sorun Giderme

### Pull sonrası `FileStorage` bulunamıyor

`.gitignore` içindeki `/storage/` kuralı yalnızca proje kökündeki yükleme dizinini dışlar. Başında `/` olmadan yazılan `storage/`, Java kaynaklarındaki `common/storage` paketini de dışladığı için kullanılmamalıdır. `FileStorage.java` ve `LocalDiskFileStorage.java` kaynakları birlikte Git'te tutulmalıdır.

Düzeltme commit'i paylaşıldıktan sonra yerel değişikliklerinizi koruyarak ilgili dalı pull edin, IntelliJ IDEA'da Gradle projesini yeniden yükleyin ve proje kökünde çalıştırın:

```powershell
git pull --ff-only
.\gradlew.bat build
```

`git pull --ff-only` yerel commit'ler nedeniyle durursa `reset --hard` kullanmayın; dal farklılığını ekip ile çözün. Java/Gradle JVM olarak JDK 21 seçili olmalıdır. Bu hata için Neon şemasında değişiklik veya veritabanını yeniden kurma gerekmez.

| Belirti | Çözüm |
|---|---|
| `SCRAM ... password is an empty string` | `$env:DB_PASSWORD` değerini verin |
| Uygulama DB'ye bağlanamıyor | PostgreSQL servisi çalışıyor mu, `Veterinery` veritabanı var mı kontrol edin |
| IT testleri atlanıyor | `VET_IT_URL` ve `VET_IT_ADMIN_PASSWORD` verin |
| 400 (başlık) | Yazma isteklerine `X-Clinic-Id` ekleyin |
| 404 (var olan kayıt) | Kayıt farklı klinik grubuna/organizasyona ait olabilir (RLS) |
