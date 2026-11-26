# VetClinic – Veteriner Klinik Yönetim API'si

Çok kiracılı (multi-tenant) veteriner klinik yönetim sistemi. Müşteri/hayvan kaydı, randevu, muayene, tedavi, aşı takibi, kullanıcı/rol yönetimi ve dosya ekleri sağlar.

Ayrıntılı dokümanlar: [Kurulum](docs/KURULUM.md) · [Mimari](docs/ARCHITECTURE.md) · [API Rehberi](docs/API.md) · [Geliştirme ve Test](docs/DEVELOPMENT.md)

## Teknoloji

| Alan | Teknoloji |
|---|---|
| Dil / Çatı | Java 21, Spring Boot 3 |
| Veri | PostgreSQL 15+, Spring Data JPA + JdbcTemplate, Flyway |
| Güvenlik | Spring Security, JWT (access + refresh token) |
| Dönüşüm / Doküman | MapStruct, springdoc-openapi (Swagger UI) |
| Test | JUnit 5, MockMvc, gerçek PostgreSQL |

## Hızlı Başlangıç

**Gereksinimler:** Git, JDK 21 ve Neon projenize erişim. Docker yalnızca PostgreSQL entegrasyon testleri için gerekir.

```powershell
git clone https://github.com/Ozge05/Veterinary_Project.git
cd Veterinary_Project

# Neon bağlantısı, ilk Flyway kurulumu ve kısıtlı vet_app rolü:
# docs/KURULUM.md adımlarını izleyin.
.\gradlew.bat bootRun
```

Neon bağlantısı, ilk Flyway migration'ları, uygulama rolü ve gerekli ortam değişkenleri için [Yeni Bilgisayarda Kurulum](docs/KURULUM.md) rehberini izleyin.

`DB_URL`, `DB_USERNAME` ve `DB_PASSWORD` zorunludur; tanımlı değilse uygulama `Could not resolve placeholder 'DB_URL'` hatasıyla başlamaz. Neon için `sslmode=require` gerekir. Neon'un `postgresql://user:pass@host/db` biçimindeki bağlantı dizesini JDBC biçimine çevirin (kullanıcı/şifreyi URL'den çıkarıp ayrı değişkenlere yazın).

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI: http://localhost:8080/v3/api-docs
- Sağlık: http://localhost:8080/actuator/health

## Yapılandırma (Ortam Değişkenleri)

| Değişken | Varsayılan | Açıklama |
|---|---|---|
| `DB_URL` | **zorunlu** | JDBC URL; Neon uygulama bağlantısında pooled endpoint önerilir |
| `DB_USERNAME` / `DB_PASSWORD` | **zorunlu** | Kısıtlı `vet_app` rolü |
| `FLYWAY_URL` / `FLYWAY_USER` / `FLYWAY_PASSWORD` | `DB_*` değerleri | Neon migration'ları için direct endpoint ve yetkili rol |
| `JWT_SECRET` | geliştirme değeri | **Canlıda mutlaka değiştirin** (≥ 32 karakter) |
| `JWT_ACCESS_TOKEN_TTL_SECONDS` | `900` | Access token ömrü |
| `JWT_REFRESH_TOKEN_TTL_SECONDS` | `1209600` | Refresh token ömrü (14 gün) |
| `APP_ADMIN_USER` / `APP_ADMIN_PASSWORD` | `admin` / `change-me` | **Canlıda değiştirin** |
| `STORAGE_ROOT` | `./storage` | Muayene ekleri için dosya dizini |
| `SERVER_PORT` | `8080` | HTTP portu |

`.env.local` git'e eklenmez; yeni bilgisayarda kendiniz oluşturun.

## Proje Yapısı

```
src/main/java/org/codeflow/veterineryproject/
  <modül>/ controller · service · domain/model · repository · dto
src/main/resources/
  application*.yml · db/migration/V1..V15 (Flyway)
db/VetClinic_PostgreSQL_Kurulum_TR.sql   # şema kaynağı (tek doğruluk kaynağı)
```

## Güvenlik Notları

- Veritabanı şemasını elle değiştirmeyin; her değişiklik yeni bir Flyway migration'ı (V16+) olmalıdır.
- Varsayılan şifre ve JWT sırlarını yalnızca geliştirmede kullanın.
