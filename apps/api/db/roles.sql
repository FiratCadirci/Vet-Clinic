-- Uygulama kullanıcısı için güvenli rol tanımı.
-- Bu dosya Flyway tarafından çalıştırılmaz; uygulama ayağa kalkmadan önce elle çalıştırılır.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'vet_app') THEN
        CREATE ROLE vet_app WITH
            LOGIN
            NOSUPERUSER
            NOBYPASSRLS
            NOCREATEDB
            NOCREATEROLE
            NOINHERIT
            CONNECTION LIMIT -1
            PASSWORD 'change_me';
    END IF;
END $$;

GRANT CONNECT ON DATABASE "Veterinery" TO vet_app;
GRANT USAGE ON SCHEMA vet TO vet_app;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA vet TO vet_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA vet TO vet_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA vet TO vet_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA vet
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO vet_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA vet
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO vet_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA vet
    GRANT EXECUTE ON FUNCTIONS TO vet_app;
