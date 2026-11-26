# Yeni Bilgisayarda Kurulum (Neon)

Bu rehber VetClinic **backend API'sini**, Neon'daki PostgreSQL veritabanınıza bağlayarak başka bir bilgisayarda çalıştırmak içindir. Proje bir web arayüzü içermez; çalıştırıldıktan sonra API, Swagger UI üzerinden incelenebilir.

## Gereksinimler

- Git
- JDK 21 (yalnızca JRE yeterli değildir)
- Neon hesabınıza ve mevcut proje/branch'e erişim
- Docker Desktop yalnızca PostgreSQL entegrasyon testlerini Testcontainers ile çalıştırmak için gereklidir

İsteğe bağlı: SQL Editor yerine `psql` ile Neon'a bağlanmak için PostgreSQL komut satırı araçları.

```powershell
java -version
git --version
```

Java sürümü 21 olmalıdır. `java` bulunamıyorsa JDK'nın `bin` dizininin `PATH` içinde ve `JAVA_HOME` değişkeninin JDK dizinini gösterdiğini kontrol edin.

## 1. Kaynak kodunu alın

PowerShell:

```powershell
git clone https://github.com/Ozge05/Veterinary_Project.git
Set-Location Veterinary_Project
```

Linux/macOS:

```bash
git clone https://github.com/Ozge05/Veterinary_Project.git
cd Veterinary_Project
```

İlk Gradle çalıştırması Gradle dağıtımını ve bağımlılıkları indirir; internet bağlantısı gerekir.

## 2. Neon bağlantı bilgilerini alın

Neon Console'da projenizi açın ve **Connect** penceresinden doğru branch, database ve role'u seçin. Uygulama trafiği için **Connection pooling** açıkken verilen connection string'i; Flyway migration'ları için ise pooling kapalı (doğrudan bağlantı) string'ini kullanın. Pooled host adında `-pooler` bulunur, direct host adında bulunmaz.

Bu projede `DB_URL` JDBC URL'sidir; Neon'un `postgresql://` bağlantı string'ini olduğu gibi kopyalamayın. Kullanıcı adı ve parolayı ayrı ortam değişkenlerinde tutun:

```text
jdbc:postgresql://<neon-host>/<database>?sslmode=require&channelBinding=require
```

Neon'un verdiği bağlantıda `channel_binding=require` parametresi varsa JDBC URL'de `channelBinding=require` olarak yazın. `DB_USERNAME` ve `DB_PASSWORD` değerlerini Connect penceresinde seçtiğiniz database role ve onun parolasından alın. Gerçek URL'yi/parolayı Git'e eklemeyin veya sohbet/ekran görüntüsünde paylaşmayın.

> Neon connection string'inizi komut satırında parola içerecek şekilde kullanmak yerine host, database, kullanıcı ve parolayı ayrı değişkenlere koyun.

## 3. İlk Flyway migration'larını çalıştırın

Neon projesinde veritabanı ve varsayılan yönetici rolü zaten bulunur; yerel PostgreSQL kurmanız veya ayrıca veritabanı oluşturmanız gerekmez. İlk kurulumda Flyway, şema ve migration geçmişini oluşturmak için Neon'un yetkili rolünü kullanmalıdır. Neon Connect penceresinde bu rolün direct bağlantı bilgilerini alın ve PowerShell'de:

```powershell
$env:DB_URL = "jdbc:postgresql://<neon-direct-host>/<database>?sslmode=require&channelBinding=require"
$env:DB_USERNAME = "<neon_yetkili_rol>"
$env:DB_PASSWORD = "<neon_yetkili_rol_parolasi>"
$env:FLYWAY_URL = $env:DB_URL
$env:FLYWAY_USER = $env:DB_USERNAME
$env:FLYWAY_PASSWORD = $env:DB_PASSWORD
$env:JWT_SECRET = "<en_az_32_karakterlik_gelistirme_anahtari>"
.\gradlew.bat bootRun
```

`Started ...` mesajını gördükten sonra uygulamayı `Ctrl+C` ile durdurun. Bu ilk çalıştırma migration'ları kurar. Yetkili Neon rolünü günlük uygulama bağlantısı olarak kullanmayın; sonraki adımda RLS'i devre dışı bırakamayan ayrı bir uygulama rolü oluşturun.

`db/VetClinic_PostgreSQL_Kurulum_TR.sql` dosyasını Neon'daki veritabanına ayrıca çalıştırmayın; şema ve migration'lar uygulamanın Flyway dosyalarıyla yönetilir. Şemayı elle değiştirmeyin.

## 4. Neon'da kısıtlı uygulama rolü oluşturun

Neon SQL Editor'de (veya direct bağlantıyla `psql` kullanarak) aşağıdaki SQL'i çalıştırın. `<neon_yetkili_rol>` yer tutucusunu migration'ları çalıştıran gerçek Neon rolüyle değiştirin; bu, yeni tablolar için varsayılan yetkilerin doğru nesne sahibi rolüne verilmesini sağlar. Bu örnek SQL'i parola eklenmiş halde dosyaya kaydetmeyin.

```sql
CREATE ROLE vet_app
    LOGIN PASSWORD '<guclu_uygulama_parolasi>'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;

GRANT CONNECT ON DATABASE <database_adi> TO vet_app;
GRANT USAGE ON SCHEMA vet TO vet_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA vet TO vet_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA vet TO vet_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA vet TO vet_app;

ALTER DEFAULT PRIVILEGES FOR ROLE <neon_yetkili_rol> IN SCHEMA vet
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO vet_app;
ALTER DEFAULT PRIVILEGES FOR ROLE <neon_yetkili_rol> IN SCHEMA vet
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO vet_app;
ALTER DEFAULT PRIVILEGES FOR ROLE <neon_yetkili_rol> IN SCHEMA vet
    GRANT EXECUTE ON FUNCTIONS TO vet_app;
```

`<database_adi>` ve `<neon_yetkili_rol>` yerlerini kendi değerlerinizle değiştirin; bunlar SQL'de parametre olarak kullanılmaz. Parola için Neon'un güncel parola gereksinimlerine uygun güçlü bir değer belirleyin. `vet_app` rolü `NOBYPASSRLS` kalmalıdır; tenant izolasyonu PostgreSQL Row-Level Security'ye dayanır.

> `vet_app` rolü zaten varsa `CREATE ROLE` satırını tekrar çalıştırmayın. Rolün `LOGIN` ve `NOBYPASSRLS` olduğunu, şema/tablo/sequence/function izinlerini ve varsayılan yetkileri kontrol edin.

## 5. Uygulamayı Neon'a bağlayıp başlatın

Günlük kullanımda uygulama sorgularını pooled Neon endpoint'ine; Flyway'i doğrudan endpoint'e bağlayın. Aşağıdaki host adlarını Neon Connect penceresinden alın: pooled hostta `-pooler` soneki olur, direct hostta olmaz.

PowerShell:

```powershell
$env:DB_URL = "jdbc:postgresql://<neon-pooled-host>/<database>?sslmode=require&channelBinding=require"
$env:DB_USERNAME = "vet_app"
$env:DB_PASSWORD = "<vet_app_parolasi>"
$env:FLYWAY_URL = "jdbc:postgresql://<neon-direct-host>/<database>?sslmode=require&channelBinding=require"
$env:FLYWAY_USER = "<neon_yetkili_rol>"
$env:FLYWAY_PASSWORD = "<neon_yetkili_rol_parolasi>"
$env:JWT_SECRET = "<en_az_32_karakterlik_gelistirme_anahtari>"
.\gradlew.bat bootRun
```

Linux/macOS:

```bash
export DB_URL='jdbc:postgresql://<neon-pooled-host>/<database>?sslmode=require&channelBinding=require'
export DB_USERNAME='vet_app'
export DB_PASSWORD='<vet_app_parolasi>'
export FLYWAY_URL='jdbc:postgresql://<neon-direct-host>/<database>?sslmode=require&channelBinding=require'
export FLYWAY_USER='<neon_yetkili_rol>'
export FLYWAY_PASSWORD='<neon_yetkili_rol_parolasi>'
export JWT_SECRET='<en_az_32_karakterlik_gelistirme_anahtari>'
./gradlew bootRun
```

Bu değişkenler yalnızca açık terminal oturumu için geçerlidir; yeni terminal açınca yeniden tanımlayın veya IDE'nin Run Configuration'ında ayarlayın. Alternatif olarak kök dizinde `.env.local` oluşturabilirsiniz; `.gitignore` bu dosyayı Git dışında tutar, ancak Spring Boot bu dosyayı kendiliğinden yüklemez. IDE'den çalıştırırken değerleri Run Configuration ortam değişkenlerine girin.

Bu backend tenant bağlamını her transaction içinde `set_config(..., true)` ile transaction-local ayarlar. Bu yüzden normal uygulama bağlantısında Neon'un transaction-mode pooler'ı kullanılabilir. Flyway schema migration'ları ise pooled değil direct connection üzerinden çalışmalıdır.

### Bağlantı sorunları

- `DB_URL`, `DB_USERNAME` ve `DB_PASSWORD` zorunludur.
- Neon için TLS kullanın (`sslmode=require`).
- Uzak Neon URL'si `jdbc:postgresql://` ile başlamalıdır; `postgresql://` JDBC URL değildir.
- Host, database ve role'un aynı Neon project/branch için seçildiğini doğrulayın. Branch'ler ayrı endpoint ve bağlantı bilgilerine sahiptir.
- Flyway yetki hatasında `FLYWAY_URL`'nin direct endpoint olduğunu ve `FLYWAY_USER`'ın migration yetkili rol olduğunu kontrol edin.
- Uygulama `permission denied` veya RLS hatası verirse `vet_app` izinlerini ve `NOBYPASSRLS` niteliğini kontrol edin.
- `Could not resolve placeholder 'DB_URL'` hatasında bağlantı değişkenlerini uygulamayı başlattığınız aynı terminal/IDE konfigürasyonuna ekleyin.

## 6. Çalıştığını doğrulayın

Uygulama varsayılan olarak `8080` portunda açılır:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Sağlık kontrolü: <http://localhost:8080/actuator/health>

Port değiştirmek için başlamadan önce `$env:SERVER_PORT = "8081"` (Linux/macOS: `export SERVER_PORT=8081`) ayarlayın.

## 7. Testleri çalıştırın

PowerShell:

```powershell
.\gradlew.bat test
```

Linux/macOS:

```bash
./gradlew test
```

Birim testleri Docker gerektirmez. PostgreSQL entegrasyon testleri Docker/Testcontainers kullanır; Docker yoksa atlanır. Harici PostgreSQL ile entegrasyon testleri çalıştırma adımları için [Geliştirme ve Test Rehberi](DEVELOPMENT.md)'ndeki `VET_IT_*` değişkenlerine bakın. Bu testler geçici `vet_it_*` veritabanları oluşturur; Neon'daki canlı veritabanınızı `VET_IT_URL` olarak vermeyin.

## Önemli: ilk kullanıcı ve kurum

Flyway veritabanı şemasını kurar; otomatik olarak organizasyon, klinik veya giriş kullanıcısı oluşturmaz. Bu yüzden boş kurulumda `/api/auth/organizations` boş liste döndürebilir ve giriş yapılamaz. Kurum, kullanıcı ve rol atamalarının proje yöneticisinin yetkili provisioning süreciyle oluşturulması gerekir; evrensel bir varsayılan giriş hesabı yoktur.

Ek API ve çalışma kuralları için [API Rehberi](API.md), [Mimari](ARCHITECTURE.md) ve [Geliştirme ve Test Rehberi](DEVELOPMENT.md) belgelerine bakın.
