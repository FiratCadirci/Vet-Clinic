-- Yerel geliştirme / Docker veritabanı hazırlığı (yönetici kullanıcıyla, psql ile çalışır; tekrar çalıştırılabilir).
-- Uygulama RLS'e tabi vet_app rolüyle bağlanır; Flyway migration'ları yönetici kullanıcıyla (FLYWAY_USER) uygular.
-- Docker'da ilk kurulumda /docker-entrypoint-initdb.d altından, mevcut kurulumda `npm run db:setup` ile çalışır.

SELECT 'CREATE DATABASE veterinery' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'veterinery')\gexec

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'vet_app') THEN
        CREATE ROLE vet_app LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD 'change_me';
    END IF;
END $$;

\c veterinery
CREATE SCHEMA IF NOT EXISTS vet;
GRANT CONNECT ON DATABASE veterinery TO vet_app;
GRANT USAGE ON SCHEMA vet TO vet_app;
-- Flyway'in (yönetici kullanıcı) sonradan oluşturacağı nesneler için varsayılan yetkiler
ALTER DEFAULT PRIVILEGES IN SCHEMA vet GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO vet_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA vet GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO vet_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA vet GRANT EXECUTE ON FUNCTIONS TO vet_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA vet TO vet_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA vet TO vet_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA vet TO vet_app;
