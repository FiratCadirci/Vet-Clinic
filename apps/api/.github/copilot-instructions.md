# VetClinic – Kalıcı Kurallar
- Stack: Java 21, Spring Boot 3, Spring Data JPA, Spring Security (JWT), PostgreSQL 15+, Flyway, Swagger (springdoc), Testcontainers, Docker.
- Önce docs/ altındaki tüm .md proje standartlarını oku ve onlara uy. Çelişki varsa dur ve bana sor.
- Veritabanı şeması db/VetClinic_PostgreSQL_Kurulum_TR.sql dosyasıdır ve tek doğruluk kaynağıdır. Şemayı ASLA değiştirme; gerekirse yeni Flyway migration (V2+) öner ve onay bekle.
- Şema adı: vet. Tenant = organization_id, şube = clinic (clinic_id). Tabloların tamamında FORCE ROW LEVEL SECURITY açıktır; her transaction'da app.organization_id, app.user_id, app.clinic_id set edilmelidir.
- Çoğu ilişki bileşik FK kullanır: (organization_id, id). Entity'lerde cross-aggregate ilişkileri @ManyToOne yerine düz UUID alanlarla tut.
- İş kuralları büyük oranda DB'dedir (randevu çakışması EXCLUDE constraint, stok guard trigger'ı, fatura post trigger'ı ile stok/komisyon, immutable audit/stok hareketi). Bunları Java'da tekrar yazma; DB hatalarını (SQLState 23P01, 23505, 23503, 23514 ve RAISE EXCEPTION) anlamlı HTTP yanıtlarına çevir.
- Katmanlı modüler monolit: controller / service / domain / repository / dto, modül başına paket. Entity doğrudan API'den dönmez, DTO + MapStruct kullan.
- Kod değişikliğinden sonra derle, testleri çalıştır, değişikliklerin kısa özetini ver. İstenmeyen adıma geçme.
