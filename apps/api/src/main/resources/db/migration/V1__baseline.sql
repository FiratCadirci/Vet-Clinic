-- ============================================================================
-- VetClinic - PostgreSQL ilk kurulum şeması (gereksinimlerle genişletilmiş)
-- Hedef sürüm: PostgreSQL 15+ (UUID, JSONB, satır düzeyinde güvenlik ve üretilen sütunlar)
-- Not: Bu dosya boş bir veritabanında ilk kurulum içindir. Mevcut şemayı silmez;
-- aynı şemada yeniden çalıştırmak için tasarlanmamıştır. Sonraki değişiklikler migration ile uygulanmalıdır.
-- Kurumlar arası veri yalıtımı: Uygulama, doğrulanmış organizasyon UUID değerini her işlem
-- (transaction) için SET LOCAL app.organization_id = '...'; ayrıca clinic web isteklerinde
-- SET LOCAL app.user_id = '...' ve SET LOCAL app.clinic_id = '...' komutlarıyla ayarlamalıdır.
-- ============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE SCHEMA IF NOT EXISTS vet;

-- ---------------------------------------------------------------------------
-- 1. Organizasyon, klinik, kullanıcı ve yetkilendirme
-- ---------------------------------------------------------------------------
CREATE TABLE vet.organizations (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                varchar(40) NOT NULL UNIQUE,
    name                varchar(200) NOT NULL,
    legal_name          varchar(250),
    tax_office          varchar(120),
    tax_number          varchar(30),
    default_currency    char(3) NOT NULL DEFAULT 'TRY',
    timezone_name       varchar(80) NOT NULL DEFAULT 'Europe/Istanbul',
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE vet.clinics (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    code                varchar(40) NOT NULL,
    name                varchar(200) NOT NULL,
    phone               varchar(40),
    email               varchar(254),
    country_code        char(2) NOT NULL DEFAULT 'TR',
    city                varchar(100),
    district            varchar(100),
    address_line        text,
    postal_code         varchar(20),
    timezone_name       varchar(80) NOT NULL DEFAULT 'Europe/Istanbul',
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_clinics_org_code UNIQUE (organization_id, code),
    CONSTRAINT uq_clinics_org_id UNIQUE (organization_id, id)
);

CREATE TABLE vet.app_users (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    email               varchar(254) NOT NULL,
    password_hash       text NOT NULL,
    display_name        varchar(200) NOT NULL,
    phone               varchar(40),
    is_active           boolean NOT NULL DEFAULT true,
    mfa_enabled         boolean NOT NULL DEFAULT false,
    last_login_at       timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_users_org_id UNIQUE (organization_id, id)
);
CREATE UNIQUE INDEX ux_app_users_org_email_lower
    ON vet.app_users (organization_id, lower(email));

CREATE TABLE vet.roles (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    code                varchar(60) NOT NULL,
    name                varchar(120) NOT NULL,
    description         text,
    is_system_role      boolean NOT NULL DEFAULT false,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_roles_org_code UNIQUE (organization_id, code),
    CONSTRAINT uq_roles_org_id UNIQUE (organization_id, id)
);

CREATE TABLE vet.permissions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                varchar(100) NOT NULL UNIQUE,
    description         text NOT NULL
);

CREATE TABLE vet.role_permissions (
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    role_id             uuid NOT NULL,
    permission_id       uuid NOT NULL REFERENCES vet.permissions(id),
    created_at          timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, role_id, permission_id),
    FOREIGN KEY (organization_id, role_id)
        REFERENCES vet.roles (organization_id, id) ON DELETE CASCADE
);

CREATE TABLE vet.user_roles (
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    user_id             uuid NOT NULL,
    role_id             uuid NOT NULL,
    assigned_at         timestamptz NOT NULL DEFAULT now(),
    assigned_by         uuid,
    PRIMARY KEY (organization_id, user_id, role_id),
    FOREIGN KEY (organization_id, user_id)
        REFERENCES vet.app_users (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, role_id)
        REFERENCES vet.roles (organization_id, id),
    FOREIGN KEY (organization_id, assigned_by)
        REFERENCES vet.app_users (organization_id, id)
);

CREATE TABLE vet.staff_members (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    user_id             uuid,
    employee_no         varchar(50),
    full_name           varchar(200) NOT NULL,
    job_title           varchar(120),
    phone               varchar(40),
    email               varchar(254),
    employment_type     varchar(30) NOT NULL DEFAULT 'EMPLOYEE'
                        CHECK (employment_type IN ('EMPLOYEE','CONTRACTOR','PART_TIME','INTERN','OTHER')),
    start_date          date,
    end_date            date,
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_staff_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_staff_org_employee_no UNIQUE (organization_id, employee_no),
    FOREIGN KEY (organization_id, user_id)
        REFERENCES vet.app_users (organization_id, id)
);

CREATE TABLE vet.staff_clinic_assignments (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    staff_member_id     uuid NOT NULL,
    clinic_id           uuid NOT NULL,
    is_primary          boolean NOT NULL DEFAULT false,
    valid_from          date NOT NULL DEFAULT CURRENT_DATE,
    valid_to            date,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_staff_clinic_assignment UNIQUE (organization_id, staff_member_id, clinic_id, valid_from),
    FOREIGN KEY (organization_id, staff_member_id)
        REFERENCES vet.staff_members (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, clinic_id)
        REFERENCES vet.clinics (organization_id, id),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE UNIQUE INDEX ux_staff_primary_clinic_current
    ON vet.staff_clinic_assignments (organization_id, staff_member_id)
    WHERE is_primary AND valid_to IS NULL;


-- Bir kullanıcının hangi şubelerde hangi rolle çalışabileceğini açıkça kaydeder.
-- Uygulama, oturum açan kullanıcının şube kapsamını bu tablodan doğrulamalıdır.
CREATE TABLE vet.user_clinic_roles (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    user_id             uuid NOT NULL,
    clinic_id           uuid NOT NULL,
    role_id             uuid NOT NULL,
    valid_from          date NOT NULL DEFAULT CURRENT_DATE,
    valid_to            date,
    assigned_by         uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_clinic_roles_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_user_clinic_role_period UNIQUE (organization_id, user_id, clinic_id, role_id, valid_from),
    FOREIGN KEY (organization_id, user_id) REFERENCES vet.app_users (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, clinic_id) REFERENCES vet.clinics (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, role_id) REFERENCES vet.roles (organization_id, id),
    FOREIGN KEY (organization_id, assigned_by) REFERENCES vet.app_users (organization_id, id),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE INDEX ix_user_clinic_roles_active
    ON vet.user_clinic_roles (organization_id, user_id, clinic_id, valid_from, valid_to);

CREATE TABLE vet.veterinarians (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    staff_member_id     uuid NOT NULL,
    license_no          varchar(100) NOT NULL,
    license_authority   varchar(150),
    graduation_year     smallint CHECK (graduation_year IS NULL OR graduation_year BETWEEN 1950 AND 2100),
    specialty           varchar(200),
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_vets_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_vets_org_staff UNIQUE (organization_id, staff_member_id),
    CONSTRAINT uq_vets_org_license UNIQUE (organization_id, license_no),
    FOREIGN KEY (organization_id, staff_member_id)
        REFERENCES vet.staff_members (organization_id, id)
);


-- Haftalık hekim çalışma saatleri şube bazında tanımlanır.
-- day_of_week: PostgreSQL EXTRACT(DOW) standardı gibi 0=Pazar, 6=Cumartesi.
CREATE TABLE vet.veterinarian_working_hours (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    clinic_id           uuid NOT NULL,
    veterinarian_id     uuid NOT NULL,
    day_of_week         smallint NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    starts_at           time NOT NULL,
    ends_at             time NOT NULL,
    effective_from      date NOT NULL DEFAULT CURRENT_DATE,
    effective_to        date,
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_vet_working_hours_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, clinic_id) REFERENCES vet.clinics (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, veterinarian_id) REFERENCES vet.veterinarians (organization_id, id) ON DELETE CASCADE,
    CHECK (ends_at > starts_at),
    CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE INDEX ix_vet_working_hours_lookup
    ON vet.veterinarian_working_hours (organization_id, clinic_id, veterinarian_id, day_of_week, is_active);

-- ---------------------------------------------------------------------------
-- 2. Türler, ırklar, müşteri ve hayvan kayıtları
-- Tür ve ırk katalogları, organizasyonlar arasında ortak kullanılan referans verileridir.
-- ---------------------------------------------------------------------------
CREATE TABLE vet.species (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                varchar(40) NOT NULL UNIQUE,
    name                varchar(100) NOT NULL UNIQUE,
    scientific_name     varchar(150),
    is_active           boolean NOT NULL DEFAULT true
);

CREATE TABLE vet.breeds (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    species_id          uuid NOT NULL REFERENCES vet.species(id),
    name                varchar(120) NOT NULL,
    is_mixed            boolean NOT NULL DEFAULT false,
    CONSTRAINT uq_breeds_species_name UNIQUE (species_id, name),
    CONSTRAINT uq_breeds_species_id UNIQUE (species_id, id)
);

CREATE TABLE vet.clients (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    client_type         varchar(20) NOT NULL DEFAULT 'PERSON'
                        CHECK (client_type IN ('PERSON','BUSINESS')),
    display_name        varchar(200) NOT NULL,
    app_user_id         uuid,
    first_name          varchar(100),
    last_name           varchar(100),
    company_name        varchar(200),
    tax_office          varchar(120),
    tax_number          varchar(30),
    identity_number_enc text,
    preferred_channel   varchar(20) CHECK (preferred_channel IS NULL OR preferred_channel IN ('SMS','EMAIL','PHONE','WHATSAPP','NONE')),
    marketing_consent   boolean NOT NULL DEFAULT false,
    communication_notes text,
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_clients_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_clients_org_app_user UNIQUE (organization_id, app_user_id),
    FOREIGN KEY (organization_id, app_user_id)
        REFERENCES vet.app_users (organization_id, id)
);

CREATE TABLE vet.client_contacts (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    client_id           uuid NOT NULL,
    contact_type        varchar(20) NOT NULL CHECK (contact_type IN ('PHONE','EMAIL','WHATSAPP','OTHER')),
    contact_value       varchar(254) NOT NULL,
    label               varchar(80),
    is_primary          boolean NOT NULL DEFAULT false,
    is_verified         boolean NOT NULL DEFAULT false,
    verified_at         timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_client_contacts_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, client_id)
        REFERENCES vet.clients (organization_id, id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX ux_client_primary_contact
    ON vet.client_contacts (organization_id, client_id, contact_type)
    WHERE is_primary;

CREATE TABLE vet.client_addresses (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    client_id           uuid NOT NULL,
    address_type        varchar(20) NOT NULL DEFAULT 'HOME'
                        CHECK (address_type IN ('HOME','WORK','BILLING','OTHER')),
    country_code        char(2) NOT NULL DEFAULT 'TR',
    city                varchar(100),
    district            varchar(100),
    neighborhood        varchar(120),
    postal_code         varchar(20),
    address_line        text NOT NULL,
    is_primary          boolean NOT NULL DEFAULT false,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_client_addresses_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, client_id)
        REFERENCES vet.clients (organization_id, id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX ux_client_primary_address
    ON vet.client_addresses (organization_id, client_id)
    WHERE is_primary;

CREATE TABLE vet.animals (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    patient_no          varchar(50) NOT NULL,
    name                varchar(150) NOT NULL,
    species_id          uuid NOT NULL REFERENCES vet.species(id),
    breed_id            uuid,
    sex                 varchar(20) NOT NULL DEFAULT 'UNKNOWN'
                        CHECK (sex IN ('MALE','FEMALE','UNKNOWN','INTERSEX')),
    birth_date          date,
    birth_date_estimated boolean NOT NULL DEFAULT false,
    color               varchar(100),
    distinguishing_marks text,
    sterilized          boolean NOT NULL DEFAULT false,
    sterilization_date  date,
    deceased_at         date,
    profile_notes       text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_animals_org_patient_no UNIQUE (organization_id, patient_no),
    CONSTRAINT uq_animals_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (species_id, breed_id)
        REFERENCES vet.breeds (species_id, id),
    CHECK (sterilization_date IS NULL OR birth_date IS NULL OR sterilization_date >= birth_date)
);

CREATE TABLE vet.animal_owners (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    animal_id           uuid NOT NULL,
    client_id           uuid NOT NULL,
    relationship_type   varchar(30) NOT NULL DEFAULT 'OWNER'
                        CHECK (relationship_type IN ('OWNER','CO_OWNER','GUARDIAN','FOSTER','EMERGENCY_CONTACT','OTHER')),
    is_primary_contact  boolean NOT NULL DEFAULT false,
    is_legal_owner      boolean NOT NULL DEFAULT true,
    valid_from          date NOT NULL DEFAULT CURRENT_DATE,
    valid_to            date,
    notes               text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_animal_owners_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, animal_id)
        REFERENCES vet.animals (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, client_id)
        REFERENCES vet.clients (organization_id, id),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE UNIQUE INDEX ux_animal_current_primary_contact
    ON vet.animal_owners (organization_id, animal_id)
    WHERE is_primary_contact AND valid_to IS NULL;


-- Müşteri/hayvan ana kayıtları tenant içinde ortak olabilir; bu tablolar hangi şubelerin
-- bu kayıtlara erişebileceğini açıkça tanımlar. Bir şubeye paylaşım verilmeden kayıt görünmez.
CREATE TABLE vet.client_clinic_access (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    client_id           uuid NOT NULL,
    clinic_id           uuid NOT NULL,
    access_level        varchar(20) NOT NULL DEFAULT 'FULL'
                        CHECK (access_level IN ('READ_ONLY','FULL')),
    granted_by          uuid,
    granted_at          timestamptz NOT NULL DEFAULT now(),
    revoked_at          timestamptz,
    notes               text,
    CONSTRAINT uq_client_clinic_access_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, client_id) REFERENCES vet.clients (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, clinic_id) REFERENCES vet.clinics (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, granted_by) REFERENCES vet.app_users (organization_id, id),
    CHECK (revoked_at IS NULL OR revoked_at >= granted_at)
);
CREATE UNIQUE INDEX ux_client_clinic_access_active
    ON vet.client_clinic_access (organization_id, client_id, clinic_id)
    WHERE revoked_at IS NULL;

CREATE TABLE vet.animal_clinic_access (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    animal_id           uuid NOT NULL,
    clinic_id           uuid NOT NULL,
    access_level        varchar(20) NOT NULL DEFAULT 'FULL'
                        CHECK (access_level IN ('READ_ONLY','FULL')),
    granted_by          uuid,
    granted_at          timestamptz NOT NULL DEFAULT now(),
    revoked_at          timestamptz,
    notes               text,
    CONSTRAINT uq_animal_clinic_access_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, animal_id) REFERENCES vet.animals (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, clinic_id) REFERENCES vet.clinics (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, granted_by) REFERENCES vet.app_users (organization_id, id),
    CHECK (revoked_at IS NULL OR revoked_at >= granted_at)
);
CREATE UNIQUE INDEX ux_animal_clinic_access_active
    ON vet.animal_clinic_access (organization_id, animal_id, clinic_id)
    WHERE revoked_at IS NULL;
CREATE INDEX ix_animal_clinic_access_lookup
    ON vet.animal_clinic_access (organization_id, clinic_id, animal_id)
    WHERE revoked_at IS NULL;


CREATE TABLE vet.user_animal_access (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    animal_id           uuid NOT NULL,
    user_id             uuid NOT NULL,
    granted_at          timestamptz NOT NULL DEFAULT now(),
    revoked_at          timestamptz,
    access_source       varchar(30) NOT NULL DEFAULT 'OWNER_RELATIONSHIP'
                        CHECK (access_source IN ('OWNER_RELATIONSHIP','MANUAL','SYSTEM')),
    CONSTRAINT uq_user_animal_access_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, animal_id) REFERENCES vet.animals (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, user_id) REFERENCES vet.app_users (organization_id, id) ON DELETE CASCADE,
    CHECK (revoked_at IS NULL OR revoked_at >= granted_at)
);
CREATE UNIQUE INDEX ux_user_animal_access_active
    ON vet.user_animal_access (organization_id, animal_id, user_id)
    WHERE revoked_at IS NULL;
CREATE INDEX ix_user_animal_access_user
    ON vet.user_animal_access (organization_id, user_id, animal_id)
    WHERE revoked_at IS NULL;

CREATE TABLE vet.animal_identifiers (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    animal_id           uuid NOT NULL,
    identifier_type     varchar(30) NOT NULL CHECK (identifier_type IN ('MICROCHIP','PASSPORT','TATTOO','REGISTRY','OTHER')),
    identifier_value    varchar(150) NOT NULL,
    issued_at           date,
    expires_at          date,
    issuing_authority   varchar(150),
    is_verified         boolean NOT NULL DEFAULT false,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_animal_identifiers_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_animal_identifier UNIQUE (organization_id, identifier_type, identifier_value),
    FOREIGN KEY (organization_id, animal_id)
        REFERENCES vet.animals (organization_id, id) ON DELETE CASCADE
);

CREATE TABLE vet.animal_weights (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    animal_id           uuid NOT NULL,
    weight_kg           numeric(7,3) NOT NULL CHECK (weight_kg > 0 AND weight_kg < 2000),
    measured_at         timestamptz NOT NULL DEFAULT now(),
    measurement_source  varchar(20) NOT NULL DEFAULT 'CLINIC'
                        CHECK (measurement_source IN ('CLINIC','OWNER','IMPORTED','OTHER')),
    note                text,
    recorded_by         uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_animal_weights_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, animal_id)
        REFERENCES vet.animals (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, recorded_by)
        REFERENCES vet.app_users (organization_id, id)
);
CREATE INDEX ix_animal_weights_history ON vet.animal_weights (organization_id, animal_id, measured_at DESC);

CREATE TABLE vet.animal_allergies (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    animal_id           uuid NOT NULL,
    substance           varchar(200) NOT NULL,
    reaction            text,
    severity            varchar(20) CHECK (severity IS NULL OR severity IN ('MILD','MODERATE','SEVERE','UNKNOWN')),
    onset_date          date,
    resolved_at         date,
    is_active           boolean NOT NULL DEFAULT true,
    recorded_by         uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_animal_allergies_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, animal_id)
        REFERENCES vet.animals (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, recorded_by)
        REFERENCES vet.app_users (organization_id, id)
);

-- ---------------------------------------------------------------------------
-- 3. Randevu ve klinik muayene geçmişi
-- ---------------------------------------------------------------------------
CREATE TABLE vet.appointments (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    clinic_id           uuid NOT NULL,
    client_id           uuid NOT NULL,
    animal_id           uuid NOT NULL,
    veterinarian_id     uuid,
    appointment_no      varchar(50) NOT NULL,
    appointment_type    varchar(30) NOT NULL DEFAULT 'EXAM'
                        CHECK (appointment_type IN ('EXAM','VACCINATION','FOLLOW_UP','SURGERY','GROOMING','LAB','IMAGING','OTHER')),
    status              varchar(24) NOT NULL DEFAULT 'BOOKED'
                        CHECK (status IN ('REQUESTED','BOOKED','CONFIRMED','CHECKED_IN','IN_PROGRESS','COMPLETED','CANCELLED','NO_SHOW')),
    starts_at           timestamptz NOT NULL,
    ends_at             timestamptz NOT NULL,
    reason              text,
    internal_notes      text,
    booking_channel      varchar(20) NOT NULL DEFAULT 'STAFF'
                        CHECK (booking_channel IN ('STAFF','PHONE','WEB','MOBILE','WHATSAPP','OTHER')),
    created_by          uuid,
    cancelled_at        timestamptz,
    cancellation_reason text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_appointments_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_appointments_org_no UNIQUE (organization_id, appointment_no),
    FOREIGN KEY (organization_id, clinic_id)
        REFERENCES vet.clinics (organization_id, id),
    FOREIGN KEY (organization_id, client_id)
        REFERENCES vet.clients (organization_id, id),
    FOREIGN KEY (organization_id, animal_id)
        REFERENCES vet.animals (organization_id, id),
    FOREIGN KEY (organization_id, veterinarian_id)
        REFERENCES vet.veterinarians (organization_id, id),
    FOREIGN KEY (organization_id, created_by)
        REFERENCES vet.app_users (organization_id, id),
    CHECK (ends_at > starts_at)
);
CREATE INDEX ix_appointments_clinic_time ON vet.appointments (organization_id, clinic_id, starts_at, ends_at);
CREATE INDEX ix_appointments_vet_time ON vet.appointments (organization_id, veterinarian_id, starts_at) WHERE veterinarian_id IS NOT NULL;
CREATE INDEX ix_appointments_animal_history ON vet.appointments (organization_id, animal_id, starts_at DESC);
-- Aynı veteriner hekimin etkin randevularının saat bakımından çakışması veritabanında engellenir.
ALTER TABLE vet.appointments
    ADD CONSTRAINT ex_appointments_vet_no_overlap
    EXCLUDE USING gist (
        organization_id WITH =,
        veterinarian_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    )
    WHERE (veterinarian_id IS NOT NULL AND status IN ('BOOKED','CONFIRMED','CHECKED_IN','IN_PROGRESS'));


-- Aynı hayvan için aynı zaman aralığında ikinci bir aktif randevu oluşturulmasını engeller.
ALTER TABLE vet.appointments
    ADD CONSTRAINT ex_appointments_animal_no_overlap
    EXCLUDE USING gist (
        organization_id WITH =,
        animal_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    )
    WHERE (status IN ('REQUESTED','BOOKED','CONFIRMED','CHECKED_IN','IN_PROGRESS'));

-- Hekimin çalışma saatleri tanımlıysa aktif randevu aralığının bu pencere içinde
-- kalmasını doğrular. Hiç çalışma saati tanımlanmamış hekimler için kurulum/demo
-- aşamasını engellememek üzere kontrol uygulanmaz.
CREATE OR REPLACE FUNCTION vet.validate_appointment_working_hours()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    clinic_timezone text;
    local_start timestamp;
    local_end timestamp;
    local_day smallint;
BEGIN
    IF NEW.veterinarian_id IS NULL
       OR NEW.status NOT IN ('REQUESTED','BOOKED','CONFIRMED','CHECKED_IN','IN_PROGRESS') THEN
        RETURN NEW;
    END IF;

    SELECT c.timezone_name INTO clinic_timezone
    FROM vet.clinics c
    WHERE c.organization_id = NEW.organization_id AND c.id = NEW.clinic_id;

    IF clinic_timezone IS NULL THEN
        RAISE EXCEPTION 'Randevu için geçerli klinik/saat dilimi bulunamadı.';
    END IF;

    local_start := NEW.starts_at AT TIME ZONE clinic_timezone;
    local_end := NEW.ends_at AT TIME ZONE clinic_timezone;
    local_day := EXTRACT(DOW FROM local_start)::smallint;

    IF local_start::date <> local_end::date THEN
        RAISE EXCEPTION 'Randevu yerel klinik saatine göre aynı gün içinde olmalıdır.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM vet.veterinarian_working_hours wh
        WHERE wh.organization_id = NEW.organization_id
          AND wh.clinic_id = NEW.clinic_id
          AND wh.veterinarian_id = NEW.veterinarian_id
          AND wh.is_active
          AND wh.effective_from <= local_start::date
          AND (wh.effective_to IS NULL OR wh.effective_to >= local_start::date)
    ) AND NOT EXISTS (
        SELECT 1 FROM vet.veterinarian_working_hours wh
        WHERE wh.organization_id = NEW.organization_id
          AND wh.clinic_id = NEW.clinic_id
          AND wh.veterinarian_id = NEW.veterinarian_id
          AND wh.day_of_week = local_day
          AND wh.is_active
          AND wh.effective_from <= local_start::date
          AND (wh.effective_to IS NULL OR wh.effective_to >= local_start::date)
          AND wh.starts_at <= local_start::time
          AND wh.ends_at >= local_end::time
    ) THEN
        RAISE EXCEPTION 'Randevu hekimin tanımlı çalışma saatleri dışında kalıyor.';
    END IF;

    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_validate_appointment_working_hours
BEFORE INSERT OR UPDATE ON vet.appointments
FOR EACH ROW EXECUTE FUNCTION vet.validate_appointment_working_hours();

CREATE TABLE vet.appointment_status_history (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    appointment_id      uuid NOT NULL,
    old_status          varchar(24),
    new_status          varchar(24) NOT NULL,
    changed_at          timestamptz NOT NULL DEFAULT now(),
    changed_by          uuid,
    note                text,
    FOREIGN KEY (organization_id, appointment_id)
        REFERENCES vet.appointments (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, changed_by)
        REFERENCES vet.app_users (organization_id, id)
);

CREATE TABLE vet.encounters (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    clinic_id           uuid NOT NULL,
    animal_id           uuid NOT NULL,
    appointment_id      uuid,
    attending_vet_id    uuid NOT NULL,
    encounter_no        varchar(50) NOT NULL,
    encounter_type      varchar(25) NOT NULL DEFAULT 'CONSULTATION'
                        CHECK (encounter_type IN ('CONSULTATION','EMERGENCY','VACCINATION','SURGERY','FOLLOW_UP','TELEMEDICINE','OTHER')),
    status              varchar(20) NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN ('OPEN','IN_PROGRESS','SIGNED','CLOSED','VOID')),
    started_at          timestamptz NOT NULL DEFAULT now(),
    ended_at            timestamptz,
    chief_complaint     text,
    history_text        text,
    examination_summary text,
    assessment_text     text,
    treatment_plan      text,
    follow_up_at        timestamptz,
    signed_at           timestamptz,
    created_by          uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_encounters_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_encounters_org_no UNIQUE (organization_id, encounter_no),
    FOREIGN KEY (organization_id, clinic_id)
        REFERENCES vet.clinics (organization_id, id),
    FOREIGN KEY (organization_id, animal_id)
        REFERENCES vet.animals (organization_id, id),
    FOREIGN KEY (organization_id, appointment_id)
        REFERENCES vet.appointments (organization_id, id),
    FOREIGN KEY (organization_id, attending_vet_id)
        REFERENCES vet.veterinarians (organization_id, id),
    FOREIGN KEY (organization_id, created_by)
        REFERENCES vet.app_users (organization_id, id),
    CHECK (ended_at IS NULL OR ended_at >= started_at)
);
CREATE UNIQUE INDEX ux_encounter_per_appointment
    ON vet.encounters (organization_id, appointment_id)
    WHERE appointment_id IS NOT NULL AND status <> 'VOID';
CREATE INDEX ix_encounters_animal_history ON vet.encounters (organization_id, animal_id, started_at DESC);
CREATE INDEX ix_encounters_vet_date ON vet.encounters (organization_id, attending_vet_id, started_at DESC);

CREATE TABLE vet.encounter_vitals (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    encounter_id        uuid NOT NULL,
    metric_code         varchar(50) NOT NULL,
    numeric_value       numeric(14,5),
    text_value          text,
    unit                varchar(30),
    measured_at         timestamptz NOT NULL DEFAULT now(),
    recorded_by         uuid,
    notes               text,
    FOREIGN KEY (organization_id, encounter_id)
        REFERENCES vet.encounters (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, recorded_by)
        REFERENCES vet.app_users (organization_id, id),
    CHECK (numeric_value IS NOT NULL OR text_value IS NOT NULL)
);
CREATE INDEX ix_encounter_vitals_metric ON vet.encounter_vitals (organization_id, encounter_id, metric_code, measured_at DESC);

CREATE TABLE vet.encounter_complaints (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    encounter_id        uuid NOT NULL,
    complaint_code      varchar(60),
    description         text NOT NULL,
    onset_at            timestamptz,
    duration_text       varchar(120),
    severity            varchar(20) CHECK (severity IS NULL OR severity IN ('MILD','MODERATE','SEVERE','UNKNOWN')),
    chronology          text,
    is_resolved         boolean NOT NULL DEFAULT false,
    created_at          timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (organization_id, encounter_id)
        REFERENCES vet.encounters (organization_id, id) ON DELETE CASCADE
);

CREATE TABLE vet.encounter_findings (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    encounter_id        uuid NOT NULL,
    body_system         varchar(80),
    anatomical_site     varchar(120),
    finding_type        varchar(30) NOT NULL DEFAULT 'OBSERVATION'
                        CHECK (finding_type IN ('OBSERVATION','ABNORMAL','NORMAL','TEST','OTHER')),
    description         text NOT NULL,
    severity            varchar(20) CHECK (severity IS NULL OR severity IN ('MILD','MODERATE','SEVERE','UNKNOWN')),
    is_abnormal         boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (organization_id, encounter_id)
        REFERENCES vet.encounters (organization_id, id) ON DELETE CASCADE
);

CREATE TABLE vet.diagnosis_catalog (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    code                varchar(80),
    name                varchar(200) NOT NULL,
    description         text,
    species_id          uuid REFERENCES vet.species(id),
    is_active           boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_diagnosis_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_diagnosis_org_code UNIQUE (organization_id, code)
);

CREATE TABLE vet.encounter_diagnoses (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    encounter_id        uuid NOT NULL,
    diagnosis_id        uuid NOT NULL,
    diagnosis_type      varchar(20) NOT NULL DEFAULT 'PRIMARY'
                        CHECK (diagnosis_type IN ('PRIMARY','SECONDARY','DIFFERENTIAL','RULE_OUT')),
    certainty           varchar(20) NOT NULL DEFAULT 'SUSPECTED'
                        CHECK (certainty IN ('SUSPECTED','PROBABLE','CONFIRMED','RULED_OUT')),
    notes               text,
    diagnosed_at        timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (organization_id, encounter_id)
        REFERENCES vet.encounters (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, diagnosis_id)
        REFERENCES vet.diagnosis_catalog (organization_id, id)
);
CREATE INDEX ix_encounter_diagnoses_encounter ON vet.encounter_diagnoses (organization_id, encounter_id);

-- ---------------------------------------------------------------------------
-- 4. Ürünler, ilaç/aşı katalog bilgileri ve stok
-- Stok bakiyesi yalnızca POSTED durumundaki hareketlerden hesaplanan görünüm üzerinden belirlenir.
-- ---------------------------------------------------------------------------
CREATE TABLE vet.products (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    sku                 varchar(80) NOT NULL,
    barcode             varchar(100),
    name                varchar(250) NOT NULL,
    product_type        varchar(25) NOT NULL
                        CHECK (product_type IN ('MEDICATION','VACCINE','FOOD','CONSUMABLE','SUPPLEMENT','EQUIPMENT','OTHER')),
    brand               varchar(150),
    base_unit           varchar(30) NOT NULL DEFAULT 'adet',
    is_stock_item       boolean NOT NULL DEFAULT true,
    tracks_lot          boolean NOT NULL DEFAULT true,
    tracks_expiry       boolean NOT NULL DEFAULT true,
    minimum_stock       numeric(14,3) NOT NULL DEFAULT 0 CHECK (minimum_stock >= 0),
    sale_price          numeric(14,2) CHECK (sale_price IS NULL OR sale_price >= 0),
    tax_rate            numeric(5,2) NOT NULL DEFAULT 20 CHECK (tax_rate >= 0 AND tax_rate <= 100),
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_products_org_sku UNIQUE (organization_id, sku),
    CONSTRAINT uq_products_org_id UNIQUE (organization_id, id)
);
CREATE UNIQUE INDEX ux_products_org_barcode
    ON vet.products (organization_id, barcode) WHERE barcode IS NOT NULL;
CREATE INDEX ix_products_search ON vet.products (organization_id, name);

CREATE TABLE vet.medication_details (
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    product_id          uuid NOT NULL,
    active_ingredient   varchar(250),
    strength            varchar(120),
    dosage_form         varchar(80),
    default_route       varchar(80),
    storage_instructions text,
    withdrawal_period_days integer CHECK (withdrawal_period_days IS NULL OR withdrawal_period_days >= 0),
    species_warnings    text,
    PRIMARY KEY (organization_id, product_id),
    FOREIGN KEY (organization_id, product_id)
        REFERENCES vet.products (organization_id, id) ON DELETE CASCADE
);

CREATE TABLE vet.vaccine_details (
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    product_id          uuid NOT NULL,
    target_diseases     text,
    target_species_id   uuid REFERENCES vet.species(id),
    minimum_age_days    integer CHECK (minimum_age_days IS NULL OR minimum_age_days >= 0),
    booster_interval_days integer CHECK (booster_interval_days IS NULL OR booster_interval_days > 0),
    storage_temperature text,
    PRIMARY KEY (organization_id, product_id),
    FOREIGN KEY (organization_id, product_id)
        REFERENCES vet.products (organization_id, id) ON DELETE CASCADE
);

CREATE TABLE vet.suppliers (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    supplier_code       varchar(50),
    name                varchar(200) NOT NULL,
    tax_number          varchar(30),
    contact_name        varchar(150),
    phone               varchar(40),
    email               varchar(254),
    address_text        text,
    payment_terms       varchar(150),
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_suppliers_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_suppliers_org_code UNIQUE (organization_id, supplier_code)
);

CREATE TABLE vet.inventory_locations (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    clinic_id           uuid NOT NULL,
    code                varchar(60) NOT NULL,
    name                varchar(150) NOT NULL,
    location_type       varchar(25) NOT NULL DEFAULT 'STOCKROOM'
                        CHECK (location_type IN ('STOCKROOM','PHARMACY','EXAM_ROOM','SURGERY','VEHICLE','OTHER')),
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_inventory_locations_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_inventory_locations_clinic_code UNIQUE (organization_id, clinic_id, code),
    FOREIGN KEY (organization_id, clinic_id)
        REFERENCES vet.clinics (organization_id, id)
);

CREATE TABLE vet.inventory_lots (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    product_id          uuid NOT NULL,
    lot_number          varchar(120) NOT NULL,
    manufactured_at     date,
    expires_at          date,
    received_at         timestamptz NOT NULL DEFAULT now(),
    supplier_id         uuid,
    unit_cost           numeric(14,4) NOT NULL DEFAULT 0 CHECK (unit_cost >= 0),
    notes               text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_inventory_lots_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_inventory_lots_org_product_lot UNIQUE (organization_id, product_id, lot_number),
    CONSTRAINT uq_inventory_lots_org_product_id UNIQUE (organization_id, product_id, id),
    FOREIGN KEY (organization_id, product_id)
        REFERENCES vet.products (organization_id, id),
    FOREIGN KEY (organization_id, supplier_id)
        REFERENCES vet.suppliers (organization_id, id),
    CHECK (expires_at IS NULL OR manufactured_at IS NULL OR expires_at >= manufactured_at)
);
CREATE INDEX ix_inventory_lots_expiry ON vet.inventory_lots (organization_id, expires_at) WHERE expires_at IS NOT NULL;

CREATE TABLE vet.inventory_transactions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    location_id         uuid NOT NULL,
    transaction_no      varchar(60) NOT NULL,
    transaction_type    varchar(25) NOT NULL
                        CHECK (transaction_type IN ('RECEIPT','DISPENSE','ADJUSTMENT','TRANSFER_IN','TRANSFER_OUT','CUSTOMER_RETURN','SUPPLIER_RETURN','WASTE','OPENING_BALANCE')),
    status              varchar(20) NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT','POSTED','CANCELLED')),
    occurred_at         timestamptz NOT NULL DEFAULT now(),
    reference_type      varchar(50),
    reference_id        uuid,
    notes               text,
    created_by          uuid,
    posted_by           uuid,
    posted_at           timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_inventory_tx_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_inventory_tx_org_no UNIQUE (organization_id, transaction_no),
    FOREIGN KEY (organization_id, location_id)
        REFERENCES vet.inventory_locations (organization_id, id),
    FOREIGN KEY (organization_id, created_by)
        REFERENCES vet.app_users (organization_id, id),
    FOREIGN KEY (organization_id, posted_by)
        REFERENCES vet.app_users (organization_id, id),
    CHECK ((status <> 'POSTED') OR (posted_at IS NOT NULL))
);

CREATE TABLE vet.inventory_transaction_lines (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    transaction_id      uuid NOT NULL,
    product_id          uuid NOT NULL,
    lot_id              uuid,
    direction           char(3) NOT NULL CHECK (direction IN ('IN','OUT')),
    quantity            numeric(14,3) NOT NULL CHECK (quantity > 0),
    unit_cost           numeric(14,4) NOT NULL DEFAULT 0 CHECK (unit_cost >= 0),
    line_note           text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (organization_id, transaction_id)
        REFERENCES vet.inventory_transactions (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, product_id)
        REFERENCES vet.products (organization_id, id),
    FOREIGN KEY (organization_id, product_id, lot_id)
        REFERENCES vet.inventory_lots (organization_id, product_id, id)
);
CREATE INDEX ix_inventory_lines_product ON vet.inventory_transaction_lines (organization_id, product_id, lot_id);

CREATE TABLE vet.purchase_orders (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    clinic_id           uuid NOT NULL,
    supplier_id         uuid NOT NULL,
    po_number           varchar(60) NOT NULL,
    status              varchar(20) NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT','SUBMITTED','PARTIALLY_RECEIVED','RECEIVED','CANCELLED')),
    ordered_at          date NOT NULL DEFAULT CURRENT_DATE,
    expected_at         date,
    currency            char(3) NOT NULL DEFAULT 'TRY',
    notes               text,
    created_by          uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_purchase_orders_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_purchase_orders_org_no UNIQUE (organization_id, po_number),
    FOREIGN KEY (organization_id, clinic_id)
        REFERENCES vet.clinics (organization_id, id),
    FOREIGN KEY (organization_id, supplier_id)
        REFERENCES vet.suppliers (organization_id, id),
    FOREIGN KEY (organization_id, created_by)
        REFERENCES vet.app_users (organization_id, id),
    CHECK (expected_at IS NULL OR expected_at >= ordered_at)
);

CREATE TABLE vet.purchase_order_lines (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    purchase_order_id   uuid NOT NULL,
    product_id          uuid NOT NULL,
    quantity_ordered    numeric(14,3) NOT NULL CHECK (quantity_ordered > 0),
    quantity_received   numeric(14,3) NOT NULL DEFAULT 0 CHECK (quantity_received >= 0),
    unit_cost           numeric(14,4) NOT NULL CHECK (unit_cost >= 0),
    tax_rate            numeric(5,2) NOT NULL DEFAULT 20 CHECK (tax_rate BETWEEN 0 AND 100),
    notes               text,
    FOREIGN KEY (organization_id, purchase_order_id)
        REFERENCES vet.purchase_orders (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, product_id)
        REFERENCES vet.products (organization_id, id),
    CHECK (quantity_received <= quantity_ordered)
);

CREATE VIEW vet.v_inventory_on_hand WITH (security_invoker = true) AS
SELECT
    t.organization_id,
    t.location_id,
    l.product_id,
    l.lot_id,
    SUM(CASE WHEN l.direction = 'IN' THEN l.quantity ELSE -l.quantity END) AS quantity_on_hand,
    MAX(t.occurred_at) AS last_movement_at
FROM vet.inventory_transactions t
JOIN vet.inventory_transaction_lines l
  ON l.organization_id = t.organization_id AND l.transaction_id = t.id
WHERE t.status = 'POSTED'
GROUP BY t.organization_id, t.location_id, l.product_id, l.lot_id;

-- ---------------------------------------------------------------------------
-- 5. Hizmet kataloğu, laboratuvar ve radyoloji
-- ---------------------------------------------------------------------------
CREATE TABLE vet.service_catalog (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    code                varchar(80) NOT NULL,
    name                varchar(200) NOT NULL,
    category            varchar(80),
    default_price       numeric(14,2) NOT NULL DEFAULT 0 CHECK (default_price >= 0),
    tax_rate            numeric(5,2) NOT NULL DEFAULT 20 CHECK (tax_rate BETWEEN 0 AND 100),
    duration_minutes    integer CHECK (duration_minutes IS NULL OR duration_minutes > 0),
    is_active           boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_service_catalog_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_service_catalog_org_code UNIQUE (organization_id, code)
);

CREATE TABLE vet.lab_test_definitions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    code                varchar(80) NOT NULL,
    name                varchar(200) NOT NULL,
    category            varchar(100),
    specimen_type       varchar(80) NOT NULL DEFAULT 'BLOOD',
    method              varchar(120),
    result_type         varchar(20) NOT NULL DEFAULT 'NUMERIC'
                        CHECK (result_type IN ('NUMERIC','TEXT','BOOLEAN','QUALITATIVE','JSON')),
    default_unit        varchar(40),
    turnaround_hours    numeric(7,2) CHECK (turnaround_hours IS NULL OR turnaround_hours >= 0),
    requires_fasting    boolean NOT NULL DEFAULT false,
    instructions        text,
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_lab_tests_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_lab_tests_org_code UNIQUE (organization_id, code)
);

CREATE TABLE vet.lab_reference_ranges (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    test_definition_id  uuid NOT NULL,
    species_id          uuid REFERENCES vet.species(id),
    sex                 varchar(20) CHECK (sex IS NULL OR sex IN ('MALE','FEMALE','UNKNOWN','INTERSEX')),
    age_days_min        integer CHECK (age_days_min IS NULL OR age_days_min >= 0),
    age_days_max        integer CHECK (age_days_max IS NULL OR age_days_max >= 0),
    reference_min       numeric(16,6),
    reference_max       numeric(16,6),
    critical_min        numeric(16,6),
    critical_max        numeric(16,6),
    unit                varchar(40),
    source_note         text,
    FOREIGN KEY (organization_id, test_definition_id)
        REFERENCES vet.lab_test_definitions (organization_id, id) ON DELETE CASCADE,
    CHECK (age_days_max IS NULL OR age_days_min IS NULL OR age_days_max >= age_days_min),
    CHECK (reference_max IS NULL OR reference_min IS NULL OR reference_max >= reference_min)
);
CREATE INDEX ix_lab_ranges_lookup ON vet.lab_reference_ranges (organization_id, test_definition_id, species_id, sex);

CREATE TABLE vet.lab_orders (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    clinic_id           uuid NOT NULL,
    animal_id           uuid NOT NULL,
    encounter_id        uuid,
    ordered_by_vet_id   uuid NOT NULL,
    order_no            varchar(60) NOT NULL,
    status              varchar(24) NOT NULL DEFAULT 'ORDERED'
                        CHECK (status IN ('ORDERED','SAMPLE_COLLECTED','IN_PROCESS','PARTIALLY_RESULTED','RESULTED','CANCELLED')),
    priority            varchar(15) NOT NULL DEFAULT 'ROUTINE'
                        CHECK (priority IN ('ROUTINE','URGENT','STAT')),
    ordered_at          timestamptz NOT NULL DEFAULT now(),
    fasting_confirmed   boolean,
    clinical_notes      text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_lab_orders_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_lab_orders_org_no UNIQUE (organization_id, order_no),
    FOREIGN KEY (organization_id, clinic_id)
        REFERENCES vet.clinics (organization_id, id),
    FOREIGN KEY (organization_id, animal_id)
        REFERENCES vet.animals (organization_id, id),
    FOREIGN KEY (organization_id, encounter_id)
        REFERENCES vet.encounters (organization_id, id),
    FOREIGN KEY (organization_id, ordered_by_vet_id)
        REFERENCES vet.veterinarians (organization_id, id)
);
CREATE INDEX ix_lab_orders_animal_date ON vet.lab_orders (organization_id, animal_id, ordered_at DESC);

CREATE TABLE vet.lab_order_items (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    lab_order_id        uuid NOT NULL,
    test_definition_id  uuid NOT NULL,
    line_no             integer NOT NULL DEFAULT 1,
    status              varchar(24) NOT NULL DEFAULT 'ORDERED'
                        CHECK (status IN ('ORDERED','SAMPLE_PENDING','IN_PROCESS','RESULTED','CANCELLED','REJECTED')),
    lab_notes           text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_lab_order_items_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_lab_order_items_line UNIQUE (organization_id, lab_order_id, line_no),
    FOREIGN KEY (organization_id, lab_order_id)
        REFERENCES vet.lab_orders (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, test_definition_id)
        REFERENCES vet.lab_test_definitions (organization_id, id)
);

CREATE TABLE vet.lab_samples (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    lab_order_item_id   uuid NOT NULL,
    accession_no        varchar(80),
    sample_type         varchar(80) NOT NULL,
    container_type      varchar(80),
    collected_at        timestamptz,
    collected_by        uuid,
    received_at         timestamptz,
    sample_status       varchar(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (sample_status IN ('PENDING','COLLECTED','RECEIVED','ACCEPTED','REJECTED','CONSUMED')),
    rejection_reason    text,
    notes               text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_lab_samples_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, lab_order_item_id)
        REFERENCES vet.lab_order_items (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, collected_by)
        REFERENCES vet.app_users (organization_id, id)
);
CREATE INDEX ix_lab_samples_accession ON vet.lab_samples (organization_id, accession_no) WHERE accession_no IS NOT NULL;

CREATE TABLE vet.lab_results (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    lab_order_item_id   uuid NOT NULL,
    result_numeric      numeric(18,6),
    result_text         text,
    result_boolean      boolean,
    result_json         jsonb,
    unit                varchar(40),
    reference_min       numeric(16,6),
    reference_max       numeric(16,6),
    critical_min        numeric(16,6),
    critical_max        numeric(16,6),
    abnormal_flag       varchar(15) NOT NULL DEFAULT 'UNKNOWN'
                        CHECK (abnormal_flag IN ('LOW','NORMAL','HIGH','CRITICAL_LOW','CRITICAL_HIGH','POSITIVE','NEGATIVE','UNKNOWN','ABNORMAL')),
    result_status       varchar(20) NOT NULL DEFAULT 'DRAFT'
                        CHECK (result_status IN ('DRAFT','PRELIMINARY','FINAL','AMENDED','CANCELLED')),
    measured_at         timestamptz,
    validated_by        uuid,
    validated_at        timestamptz,
    is_current          boolean NOT NULL DEFAULT true,
    comments             text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (organization_id, lab_order_item_id)
        REFERENCES vet.lab_order_items (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, validated_by)
        REFERENCES vet.app_users (organization_id, id),
    CHECK (result_numeric IS NOT NULL OR result_text IS NOT NULL OR result_boolean IS NOT NULL OR result_json IS NOT NULL),
    CHECK (reference_max IS NULL OR reference_min IS NULL OR reference_max >= reference_min)
);
CREATE UNIQUE INDEX ux_lab_result_current_per_item
    ON vet.lab_results (organization_id, lab_order_item_id) WHERE is_current;
CREATE INDEX ix_lab_results_status ON vet.lab_results (organization_id, result_status, measured_at DESC);

CREATE TABLE vet.imaging_procedure_definitions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    code                varchar(80) NOT NULL,
    name                varchar(200) NOT NULL,
    modality            varchar(20) NOT NULL
                        CHECK (modality IN ('XRAY','ULTRASOUND','CT','MRI','ENDOSCOPY','OTHER')),
    body_region         varchar(120),
    preparation         text,
    default_price       numeric(14,2) CHECK (default_price IS NULL OR default_price >= 0),
    is_active           boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_imaging_defs_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_imaging_defs_org_code UNIQUE (organization_id, code)
);

CREATE TABLE vet.imaging_orders (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    clinic_id           uuid NOT NULL,
    animal_id           uuid NOT NULL,
    encounter_id        uuid,
    ordered_by_vet_id   uuid NOT NULL,
    order_no            varchar(60) NOT NULL,
    status              varchar(20) NOT NULL DEFAULT 'ORDERED'
                        CHECK (status IN ('ORDERED','SCHEDULED','IN_PROGRESS','COMPLETED','REPORTED','CANCELLED')),
    priority            varchar(15) NOT NULL DEFAULT 'ROUTINE'
                        CHECK (priority IN ('ROUTINE','URGENT','STAT')),
    clinical_indication text,
    ordered_at          timestamptz NOT NULL DEFAULT now(),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_imaging_orders_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_imaging_orders_org_no UNIQUE (organization_id, order_no),
    FOREIGN KEY (organization_id, clinic_id)
        REFERENCES vet.clinics (organization_id, id),
    FOREIGN KEY (organization_id, animal_id)
        REFERENCES vet.animals (organization_id, id),
    FOREIGN KEY (organization_id, encounter_id)
        REFERENCES vet.encounters (organization_id, id),
    FOREIGN KEY (organization_id, ordered_by_vet_id)
        REFERENCES vet.veterinarians (organization_id, id)
);

CREATE TABLE vet.imaging_studies (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    imaging_order_id    uuid NOT NULL,
    procedure_definition_id uuid NOT NULL,
    study_no            varchar(80),
    body_site           varchar(120),
    laterality          varchar(15) CHECK (laterality IS NULL OR laterality IN ('LEFT','RIGHT','BILATERAL','NA')),
    view_count          integer CHECK (view_count IS NULL OR view_count >= 1),
    performed_at        timestamptz,
    performed_by        uuid,
    status              varchar(20) NOT NULL DEFAULT 'PLANNED'
                        CHECK (status IN ('PLANNED','ACQUIRED','QUALITY_CHECK','REPORTED','CANCELLED')),
    technical_notes     text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_imaging_studies_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, imaging_order_id)
        REFERENCES vet.imaging_orders (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, procedure_definition_id)
        REFERENCES vet.imaging_procedure_definitions (organization_id, id),
    FOREIGN KEY (organization_id, performed_by)
        REFERENCES vet.veterinarians (organization_id, id)
);

CREATE TABLE vet.imaging_reports (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    imaging_study_id    uuid NOT NULL,
    findings            text NOT NULL,
    impression          text,
    recommendations     text,
    reported_by         uuid,
    reported_at         timestamptz NOT NULL DEFAULT now(),
    signed_at           timestamptz,
    report_version      integer NOT NULL DEFAULT 1 CHECK (report_version > 0),
    is_current          boolean NOT NULL DEFAULT true,
    FOREIGN KEY (organization_id, imaging_study_id)
        REFERENCES vet.imaging_studies (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, reported_by)
        REFERENCES vet.veterinarians (organization_id, id)
);
CREATE UNIQUE INDEX ux_imaging_report_current
    ON vet.imaging_reports (organization_id, imaging_study_id) WHERE is_current;

CREATE TABLE vet.imaging_files (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    imaging_study_id    uuid NOT NULL,
    file_role           varchar(30) NOT NULL DEFAULT 'IMAGE'
                        CHECK (file_role IN ('IMAGE','DICOM','VIDEO','ANNOTATION','OTHER')),
    storage_provider    varchar(40) NOT NULL,
    object_key          text NOT NULL,
    original_filename   varchar(255),
    mime_type           varchar(120),
    size_bytes          bigint CHECK (size_bytes IS NULL OR size_bytes >= 0),
    sha256_checksum     char(64),
    uploaded_by         uuid,
    uploaded_at         timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (organization_id, imaging_study_id)
        REFERENCES vet.imaging_studies (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, uploaded_by)
        REFERENCES vet.app_users (organization_id, id)
);

-- ---------------------------------------------------------------------------
-- 6. Reçeteler, uygulanan ilaçlar, aşı kayıtları ve hayvan bazlı takvim
-- ---------------------------------------------------------------------------
CREATE TABLE vet.prescriptions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    encounter_id        uuid NOT NULL,
    animal_id           uuid NOT NULL,
    prescribed_by       uuid NOT NULL,
    prescription_no     varchar(60) NOT NULL,
    status              varchar(20) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('DRAFT','ACTIVE','PARTIALLY_DISPENSED','DISPENSED','CANCELLED','EXPIRED')),
    prescribed_at       timestamptz NOT NULL DEFAULT now(),
    valid_until         date,
    general_instructions text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_prescriptions_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_prescriptions_org_no UNIQUE (organization_id, prescription_no),
    FOREIGN KEY (organization_id, encounter_id)
        REFERENCES vet.encounters (organization_id, id),
    FOREIGN KEY (organization_id, animal_id)
        REFERENCES vet.animals (organization_id, id),
    FOREIGN KEY (organization_id, prescribed_by)
        REFERENCES vet.veterinarians (organization_id, id),
    CHECK (valid_until IS NULL OR valid_until >= prescribed_at::date)
);

CREATE TABLE vet.prescription_items (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    prescription_id     uuid NOT NULL,
    product_id          uuid NOT NULL,
    line_no             integer NOT NULL,
    dose                numeric(14,5),
    dose_unit            varchar(40),
    route               varchar(80),
    frequency_text      varchar(150) NOT NULL,
    duration_days       integer CHECK (duration_days IS NULL OR duration_days > 0),
    quantity_prescribed numeric(14,3) CHECK (quantity_prescribed IS NULL OR quantity_prescribed > 0),
    refills_allowed     integer NOT NULL DEFAULT 0 CHECK (refills_allowed >= 0),
    instructions        text,
    quantity_dispensed  numeric(14,3) NOT NULL DEFAULT 0 CHECK (quantity_dispensed >= 0),
    CONSTRAINT uq_prescription_items_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, prescription_id)
        REFERENCES vet.prescriptions (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, product_id)
        REFERENCES vet.products (organization_id, id),
    UNIQUE (organization_id, prescription_id, line_no)
);

CREATE TABLE vet.animal_care_schedules (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    animal_id           uuid NOT NULL,
    schedule_type       varchar(25) NOT NULL
                        CHECK (schedule_type IN ('VACCINE','MEDICATION','DEWORMING','PREVENTIVE','FOLLOW_UP','CHRONIC_CARE','OTHER')),
    product_id          uuid,
    title               varchar(200) NOT NULL,
    dose_text           varchar(150),
    route               varchar(80),
    frequency_text      varchar(150),
    recurrence_interval_days integer CHECK (recurrence_interval_days IS NULL OR recurrence_interval_days > 0),
    starts_on           date NOT NULL DEFAULT CURRENT_DATE,
    ends_on             date,
    instructions        text,
    status              varchar(20) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','PAUSED','COMPLETED','CANCELLED')),
    created_by          uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_care_schedules_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, animal_id)
        REFERENCES vet.animals (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, product_id)
        REFERENCES vet.products (organization_id, id),
    FOREIGN KEY (organization_id, created_by)
        REFERENCES vet.app_users (organization_id, id),
    CHECK (ends_on IS NULL OR ends_on >= starts_on)
);
CREATE INDEX ix_care_schedules_due ON vet.animal_care_schedules (organization_id, status, starts_on, ends_on);

CREATE TABLE vet.animal_care_schedule_events (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    schedule_id         uuid NOT NULL,
    due_at              timestamptz NOT NULL,
    status              varchar(20) NOT NULL DEFAULT 'DUE'
                        CHECK (status IN ('DUE','NOTIFIED','COMPLETED','SKIPPED','OVERDUE','CANCELLED')),
    completed_at        timestamptz,
    encounter_id        uuid,
    notes               text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_care_events_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_care_event_due UNIQUE (organization_id, schedule_id, due_at),
    FOREIGN KEY (organization_id, schedule_id)
        REFERENCES vet.animal_care_schedules (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, encounter_id)
        REFERENCES vet.encounters (organization_id, id),
    CHECK ((status <> 'COMPLETED') OR completed_at IS NOT NULL)
);
CREATE INDEX ix_care_events_due ON vet.animal_care_schedule_events (organization_id, status, due_at);

CREATE TABLE vet.medication_administrations (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    animal_id           uuid NOT NULL,
    encounter_id        uuid,
    product_id          uuid NOT NULL,
    lot_id              uuid,
    prescription_item_id uuid,
    care_schedule_event_id uuid,
    administered_at     timestamptz NOT NULL DEFAULT now(),
    dose                numeric(14,5),
    dose_unit           varchar(40),
    route               varchar(80),
    administered_by     uuid NOT NULL,
    inventory_transaction_id uuid,
    notes               text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_med_admin_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, animal_id)
        REFERENCES vet.animals (organization_id, id),
    FOREIGN KEY (organization_id, encounter_id)
        REFERENCES vet.encounters (organization_id, id),
    FOREIGN KEY (organization_id, product_id)
        REFERENCES vet.products (organization_id, id),
    FOREIGN KEY (organization_id, product_id, lot_id)
        REFERENCES vet.inventory_lots (organization_id, product_id, id),
    FOREIGN KEY (organization_id, prescription_item_id)
        REFERENCES vet.prescription_items (organization_id, id),
    FOREIGN KEY (organization_id, care_schedule_event_id)
        REFERENCES vet.animal_care_schedule_events (organization_id, id),
    FOREIGN KEY (organization_id, administered_by)
        REFERENCES vet.veterinarians (organization_id, id),
    FOREIGN KEY (organization_id, inventory_transaction_id)
        REFERENCES vet.inventory_transactions (organization_id, id)
);
CREATE UNIQUE INDEX ux_med_admin_schedule_event
    ON vet.medication_administrations (organization_id, care_schedule_event_id)
    WHERE care_schedule_event_id IS NOT NULL;
CREATE INDEX ix_med_admin_animal_date ON vet.medication_administrations (organization_id, animal_id, administered_at DESC);

CREATE TABLE vet.vaccine_records (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    animal_id           uuid NOT NULL,
    encounter_id        uuid,
    vaccine_product_id  uuid NOT NULL,
    lot_id              uuid,
    care_schedule_event_id uuid,
    administered_at     timestamptz NOT NULL DEFAULT now(),
    dose                numeric(14,5),
    dose_unit           varchar(40),
    route               varchar(80),
    administration_site varchar(100),
    administered_by     uuid NOT NULL,
    next_due_at         date,
    certificate_no      varchar(100),
    adverse_reaction    text,
    inventory_transaction_id uuid,
    notes               text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_vaccine_records_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, animal_id)
        REFERENCES vet.animals (organization_id, id),
    FOREIGN KEY (organization_id, encounter_id)
        REFERENCES vet.encounters (organization_id, id),
    FOREIGN KEY (organization_id, vaccine_product_id)
        REFERENCES vet.products (organization_id, id),
    FOREIGN KEY (organization_id, vaccine_product_id, lot_id)
        REFERENCES vet.inventory_lots (organization_id, product_id, id),
    FOREIGN KEY (organization_id, care_schedule_event_id)
        REFERENCES vet.animal_care_schedule_events (organization_id, id),
    FOREIGN KEY (organization_id, administered_by)
        REFERENCES vet.veterinarians (organization_id, id),
    FOREIGN KEY (organization_id, inventory_transaction_id)
        REFERENCES vet.inventory_transactions (organization_id, id)
);
CREATE UNIQUE INDEX ux_vaccine_schedule_event
    ON vet.vaccine_records (organization_id, care_schedule_event_id)
    WHERE care_schedule_event_id IS NOT NULL;
CREATE INDEX ix_vaccine_records_animal_date ON vet.vaccine_records (organization_id, animal_id, administered_at DESC);
CREATE INDEX ix_vaccine_records_next_due ON vet.vaccine_records (organization_id, next_due_at) WHERE next_due_at IS NOT NULL;


-- Klinik tarafından onaylanan, tür ve yaşa göre yapılandırılabilir aşı protokolleri.
-- Bu tablolar öneri/plan içindir; tıbbi takvim klinik tarafından doğrulanmalıdır.
CREATE TABLE vet.vaccine_protocols (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    code                varchar(60) NOT NULL,
    name                varchar(200) NOT NULL,
    species_id          uuid NOT NULL REFERENCES vet.species(id),
    version_no          integer NOT NULL DEFAULT 1 CHECK (version_no > 0),
    status              varchar(20) NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT','APPROVED','RETIRED')),
    approved_by         uuid,
    approved_at         timestamptz,
    notes               text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_vaccine_protocols_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_vaccine_protocols_code_version UNIQUE (organization_id, code, version_no),
    FOREIGN KEY (organization_id, approved_by) REFERENCES vet.veterinarians (organization_id, id),
    CHECK ((status <> 'APPROVED') OR (approved_by IS NOT NULL AND approved_at IS NOT NULL))
);

CREATE TABLE vet.vaccine_protocol_doses (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         uuid NOT NULL REFERENCES vet.organizations(id),
    protocol_id             uuid NOT NULL,
    dose_number             integer NOT NULL CHECK (dose_number > 0),
    vaccine_product_id      uuid,
    recommended_age_days    integer CHECK (recommended_age_days IS NULL OR recommended_age_days >= 0),
    interval_from_previous_days integer CHECK (interval_from_previous_days IS NULL OR interval_from_previous_days > 0),
    notes                   text,
    created_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_vaccine_protocol_doses_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_vaccine_protocol_dose_number UNIQUE (organization_id, protocol_id, dose_number),
    FOREIGN KEY (organization_id, protocol_id) REFERENCES vet.vaccine_protocols (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, vaccine_product_id) REFERENCES vet.products (organization_id, id)
);
CREATE INDEX ix_vaccine_protocols_species_status
    ON vet.vaccine_protocols (organization_id, species_id, status);

-- ---------------------------------------------------------------------------
-- 7. Hatırlatmalar, belgeler, onam ve denetim kayıtları
-- ---------------------------------------------------------------------------
CREATE TABLE vet.notification_outbox (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    client_id           uuid,
    animal_id           uuid,
    appointment_id      uuid,
    care_event_id       uuid,
    channel             varchar(20) NOT NULL CHECK (channel IN ('SMS','EMAIL','WHATSAPP','PUSH')),
    recipient           varchar(254) NOT NULL,
    subject             varchar(250),
    message_body        text NOT NULL,
    scheduled_at        timestamptz NOT NULL DEFAULT now(),
    status              varchar(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED','CANCELLED')),
    attempt_count       integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    provider_message_id varchar(200),
    sent_at             timestamptz,
    last_error          text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (organization_id, client_id)
        REFERENCES vet.clients (organization_id, id),
    FOREIGN KEY (organization_id, animal_id)
        REFERENCES vet.animals (organization_id, id),
    FOREIGN KEY (organization_id, appointment_id)
        REFERENCES vet.appointments (organization_id, id),
    FOREIGN KEY (organization_id, care_event_id)
        REFERENCES vet.animal_care_schedule_events (organization_id, id)
);
CREATE INDEX ix_notification_queue ON vet.notification_outbox (status, scheduled_at) WHERE status IN ('PENDING','FAILED');


CREATE TABLE vet.notification_preferences (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    client_id           uuid NOT NULL,
    notification_type   varchar(50) NOT NULL,
    channel             varchar(20) NOT NULL CHECK (channel IN ('SMS','EMAIL','WHATSAPP','PUSH')),
    is_enabled          boolean NOT NULL DEFAULT true,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_notification_preferences_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_notification_preferences_client_event UNIQUE (organization_id, client_id, notification_type, channel),
    FOREIGN KEY (organization_id, client_id) REFERENCES vet.clients (organization_id, id) ON DELETE CASCADE
);

CREATE TABLE vet.clinical_documents (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    clinic_id           uuid,
    client_id           uuid,
    animal_id           uuid,
    encounter_id        uuid,
    document_type       varchar(40) NOT NULL
                        CHECK (document_type IN ('CONSENT','PRESCRIPTION','LAB_REPORT','IMAGING_REPORT','VACCINE_CERTIFICATE','INVOICE','PHOTO','REFERRAL','OTHER')),
    title               varchar(250) NOT NULL,
    storage_provider    varchar(40) NOT NULL,
    object_key          text NOT NULL,
    original_filename   varchar(255),
    mime_type           varchar(120),
    size_bytes          bigint CHECK (size_bytes IS NULL OR size_bytes >= 0),
    sha256_checksum     char(64),
    uploaded_by         uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (organization_id, clinic_id)
        REFERENCES vet.clinics (organization_id, id),
    FOREIGN KEY (organization_id, client_id)
        REFERENCES vet.clients (organization_id, id),
    FOREIGN KEY (organization_id, animal_id)
        REFERENCES vet.animals (organization_id, id),
    FOREIGN KEY (organization_id, encounter_id)
        REFERENCES vet.encounters (organization_id, id),
    FOREIGN KEY (organization_id, uploaded_by)
        REFERENCES vet.app_users (organization_id, id),
    CHECK (client_id IS NOT NULL OR animal_id IS NOT NULL OR encounter_id IS NOT NULL)
);
CREATE INDEX ix_clinical_documents_animal ON vet.clinical_documents (organization_id, animal_id, created_at DESC) WHERE animal_id IS NOT NULL;

CREATE TABLE vet.consents (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    client_id           uuid NOT NULL,
    animal_id           uuid,
    encounter_id        uuid,
    consent_type        varchar(40) NOT NULL
                        CHECK (consent_type IN ('TREATMENT','SURGERY','ANESTHESIA','DATA_PROCESSING','PHOTO_VIDEO','OTHER')),
    document_version    varchar(40) NOT NULL,
    consent_status      varchar(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (consent_status IN ('PENDING','GRANTED','DECLINED','REVOKED','EXPIRED')),
    consent_text        text,
    signed_by_name      varchar(200),
    signed_at           timestamptz,
    expires_at          timestamptz,
    recorded_by         uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (organization_id, client_id)
        REFERENCES vet.clients (organization_id, id),
    FOREIGN KEY (organization_id, animal_id)
        REFERENCES vet.animals (organization_id, id),
    FOREIGN KEY (organization_id, encounter_id)
        REFERENCES vet.encounters (organization_id, id),
    FOREIGN KEY (organization_id, recorded_by)
        REFERENCES vet.app_users (organization_id, id),
    CHECK (expires_at IS NULL OR signed_at IS NULL OR expires_at >= signed_at)
);

CREATE TABLE vet.audit_logs (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    clinic_id           uuid,
    actor_user_id       uuid,
    action              varchar(40) NOT NULL,
    entity_type         varchar(100) NOT NULL,
    entity_id           uuid,
    occurred_at         timestamptz NOT NULL DEFAULT now(),
    request_id          varchar(100),
    ip_address          inet,
    user_agent          text,
    old_values          jsonb,
    new_values          jsonb,
    reason              text,
    FOREIGN KEY (organization_id, clinic_id)
        REFERENCES vet.clinics (organization_id, id),
    FOREIGN KEY (organization_id, actor_user_id)
        REFERENCES vet.app_users (organization_id, id)
);
CREATE INDEX ix_audit_entity ON vet.audit_logs (organization_id, entity_type, entity_id, occurred_at DESC);
CREATE INDEX ix_audit_actor ON vet.audit_logs (organization_id, actor_user_id, occurred_at DESC);

-- ---------------------------------------------------------------------------
-- 8. Faturalama ve tahsilat
-- Fatura satırlarının tutarları veritabanında üretilen sütunlarla hesaplanır.
-- ---------------------------------------------------------------------------
CREATE TABLE vet.invoices (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    clinic_id           uuid NOT NULL,
    inventory_location_id uuid,
    client_id           uuid NOT NULL,
    animal_id           uuid,
    encounter_id        uuid,
    invoice_no          varchar(70) NOT NULL,
    invoice_type        varchar(20) NOT NULL DEFAULT 'SALES'
                        CHECK (invoice_type IN ('SALES','CREDIT_NOTE','PROFORMA')),
    status              varchar(20) NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT','ISSUED','PARTIALLY_PAID','PAID','VOID','OVERDUE')),
    currency            char(3) NOT NULL DEFAULT 'TRY',
    issued_at           timestamptz,
    due_at              date,
    notes               text,
    created_by          uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_invoices_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_invoices_org_no UNIQUE (organization_id, invoice_no),
    FOREIGN KEY (organization_id, clinic_id)
        REFERENCES vet.clinics (organization_id, id),
    FOREIGN KEY (organization_id, inventory_location_id)
        REFERENCES vet.inventory_locations (organization_id, id),
    FOREIGN KEY (organization_id, client_id)
        REFERENCES vet.clients (organization_id, id),
    FOREIGN KEY (organization_id, animal_id)
        REFERENCES vet.animals (organization_id, id),
    FOREIGN KEY (organization_id, encounter_id)
        REFERENCES vet.encounters (organization_id, id),
    FOREIGN KEY (organization_id, created_by)
        REFERENCES vet.app_users (organization_id, id),
    CHECK (due_at IS NULL OR issued_at IS NULL OR due_at >= issued_at::date)
);
CREATE INDEX ix_invoices_client_date ON vet.invoices (organization_id, client_id, created_at DESC);

CREATE TABLE vet.invoice_lines (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    invoice_id          uuid NOT NULL,
    line_no             integer NOT NULL,
    line_type           varchar(20) NOT NULL CHECK (line_type IN ('SERVICE','PRODUCT','OTHER')),
    service_id          uuid,
    product_id          uuid,
    description         varchar(300) NOT NULL,
    quantity            numeric(14,3) NOT NULL CHECK (quantity > 0),
    unit_price          numeric(14,2) NOT NULL CHECK (unit_price >= 0),
    discount_rate       numeric(5,2) NOT NULL DEFAULT 0 CHECK (discount_rate BETWEEN 0 AND 100),
    tax_rate            numeric(5,2) NOT NULL DEFAULT 20 CHECK (tax_rate BETWEEN 0 AND 100),
    lot_id              uuid,
    net_amount          numeric(14,2) GENERATED ALWAYS AS (round(quantity * unit_price * (1 - discount_rate / 100), 2)) STORED,
    tax_amount          numeric(14,2) GENERATED ALWAYS AS (round(quantity * unit_price * (1 - discount_rate / 100) * tax_rate / 100, 2)) STORED,
    line_total          numeric(14,2) GENERATED ALWAYS AS (round(quantity * unit_price * (1 - discount_rate / 100) * (1 + tax_rate / 100), 2)) STORED,
    CONSTRAINT uq_invoice_lines_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, invoice_id)
        REFERENCES vet.invoices (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, service_id)
        REFERENCES vet.service_catalog (organization_id, id),
    FOREIGN KEY (organization_id, product_id)
        REFERENCES vet.products (organization_id, id),
    FOREIGN KEY (organization_id, product_id, lot_id)
        REFERENCES vet.inventory_lots (organization_id, product_id, id),
    UNIQUE (organization_id, invoice_id, line_no),
    CHECK (
      (line_type = 'SERVICE' AND service_id IS NOT NULL AND product_id IS NULL) OR
      (line_type = 'PRODUCT' AND product_id IS NOT NULL AND service_id IS NULL) OR
      (line_type = 'OTHER' AND service_id IS NULL AND product_id IS NULL)
    )
);

CREATE VIEW vet.v_invoice_totals WITH (security_invoker = true) AS
SELECT i.organization_id, i.id AS invoice_id,
       COALESCE(SUM(l.net_amount), 0)::numeric(14,2) AS subtotal,
       COALESCE(SUM(l.tax_amount), 0)::numeric(14,2) AS tax_total,
       COALESCE(SUM(l.line_total), 0)::numeric(14,2) AS grand_total
FROM vet.invoices i
LEFT JOIN vet.invoice_lines l
  ON l.organization_id = i.organization_id AND l.invoice_id = i.id
GROUP BY i.organization_id, i.id;

CREATE TABLE vet.payments (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    client_id           uuid NOT NULL,
    clinic_id           uuid NOT NULL,
    payment_no          varchar(70) NOT NULL,
    payment_method      varchar(25) NOT NULL
                        CHECK (payment_method IN ('CASH','CREDIT_CARD','BANK_TRANSFER','ONLINE','OTHER')),
    status              varchar(20) NOT NULL DEFAULT 'COMPLETED'
                        CHECK (status IN ('PENDING','COMPLETED','FAILED','REFUNDED','VOID')),
    amount              numeric(14,2) NOT NULL CHECK (amount > 0),
    currency            char(3) NOT NULL DEFAULT 'TRY',
    paid_at             timestamptz NOT NULL DEFAULT now(),
    reference_no        varchar(150),
    notes               text,
    received_by         uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_payments_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_payments_org_no UNIQUE (organization_id, payment_no),
    FOREIGN KEY (organization_id, client_id)
        REFERENCES vet.clients (organization_id, id),
    FOREIGN KEY (organization_id, clinic_id)
        REFERENCES vet.clinics (organization_id, id),
    FOREIGN KEY (organization_id, received_by)
        REFERENCES vet.app_users (organization_id, id)
);

CREATE TABLE vet.invoice_payments (
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    invoice_id          uuid NOT NULL,
    payment_id          uuid NOT NULL,
    applied_amount      numeric(14,2) NOT NULL CHECK (applied_amount > 0),
    applied_at          timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, invoice_id, payment_id),
    FOREIGN KEY (organization_id, invoice_id)
        REFERENCES vet.invoices (organization_id, id),
    FOREIGN KEY (organization_id, payment_id)
        REFERENCES vet.payments (organization_id, id)
);

-- ---------------------------------------------------------------------------
-- 9. Basit finans: gider kategorileri ve gider kayıtları
-- Gelir ve tahsilatlar invoices, payments ve invoice_payments tablolarından izlenir.
-- Hizmetlerin varsayılan ücretleri service_catalog.default_price alanında tutulur.
-- ---------------------------------------------------------------------------
CREATE TABLE vet.expense_categories (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    name                varchar(120) NOT NULL,
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_expense_categories_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_expense_categories_org_name UNIQUE (organization_id, name)
);

CREATE TABLE vet.expenses (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    clinic_id           uuid NOT NULL,
    category_id         uuid NOT NULL,
    expense_no          varchar(70) NOT NULL,
    description         varchar(300) NOT NULL,
    amount              numeric(14,2) NOT NULL CHECK (amount > 0),
    currency            char(3) NOT NULL DEFAULT 'TRY',
    expense_date        date NOT NULL DEFAULT CURRENT_DATE,
    payment_status      varchar(20) NOT NULL DEFAULT 'PAID'
                        CHECK (payment_status IN ('UNPAID','PAID','CANCELLED')),
    payment_method      varchar(25)
                        CHECK (payment_method IS NULL OR payment_method IN ('CASH','CREDIT_CARD','BANK_TRANSFER','ONLINE','OTHER')),
    vendor_name         varchar(200),
    document_no         varchar(100),
    notes               text,
    created_by          uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_expenses_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_expenses_org_no UNIQUE (organization_id, expense_no),
    FOREIGN KEY (organization_id, clinic_id)
        REFERENCES vet.clinics (organization_id, id),
    FOREIGN KEY (organization_id, category_id)
        REFERENCES vet.expense_categories (organization_id, id),
    FOREIGN KEY (organization_id, created_by)
        REFERENCES vet.app_users (organization_id, id)
);
CREATE INDEX ix_expenses_org_date ON vet.expenses (organization_id, expense_date DESC);
CREATE INDEX ix_expenses_clinic_date ON vet.expenses (organization_id, clinic_id, expense_date DESC);

-- Basit gelir/gider özeti. Gelir, tahsil edilmiş ödeme tutarından hesaplanır;
-- faturanın düzenlenmiş olması tek başına tahsilat veya nakit girişi anlamına gelmez.
CREATE VIEW vet.v_financial_monthly_summary WITH (security_invoker = true) AS
WITH revenue AS (
    SELECT p.organization_id, p.clinic_id,
           date_trunc('month', p.paid_at)::date AS month,
           SUM(p.amount)::numeric(14,2) AS revenue_amount,
           0::numeric(14,2) AS expense_amount
    FROM vet.payments p
    WHERE p.status = 'COMPLETED'
    GROUP BY p.organization_id, p.clinic_id, date_trunc('month', p.paid_at)::date
), expense AS (
    SELECT e.organization_id, e.clinic_id,
           date_trunc('month', e.expense_date)::date AS month,
           0::numeric(14,2) AS revenue_amount,
           SUM(e.amount)::numeric(14,2) AS expense_amount
    FROM vet.expenses e
    WHERE e.payment_status = 'PAID'
    GROUP BY e.organization_id, e.clinic_id, date_trunc('month', e.expense_date)::date
), cashflow AS (
    SELECT * FROM revenue
    UNION ALL
    SELECT * FROM expense
)
SELECT organization_id, clinic_id, month,
       SUM(revenue_amount)::numeric(14,2) AS revenue_amount,
       SUM(expense_amount)::numeric(14,2) AS expense_amount,
       (SUM(revenue_amount) - SUM(expense_amount))::numeric(14,2) AS net_cashflow
FROM cashflow
GROUP BY organization_id, clinic_id, month;


-- ---------------------------------------------------------------------------
-- 9A. Platform yöneticileri, abonelik paketleri ve komisyon yönetimi
-- Platform çapındaki kataloglar tenant'tan bağımsızdır; tenant işlemleri RLS kapsamındadır.
-- ---------------------------------------------------------------------------
CREATE TABLE vet.platform_admins (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email               varchar(254) NOT NULL UNIQUE,
    password_hash       text NOT NULL,
    display_name        varchar(200) NOT NULL,
    is_active           boolean NOT NULL DEFAULT true,
    last_login_at       timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE vet.subscription_plans (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                varchar(50) NOT NULL UNIQUE,
    name                varchar(120) NOT NULL,
    billing_period      varchar(15) NOT NULL CHECK (billing_period IN ('MONTHLY','YEARLY')),
    price               numeric(14,2) NOT NULL CHECK (price >= 0),
    currency            char(3) NOT NULL DEFAULT 'TRY',
    max_branches        integer NOT NULL CHECK (max_branches > 0),
    max_users           integer NOT NULL CHECK (max_users > 0),
    features            jsonb NOT NULL DEFAULT '{}'::jsonb,
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE vet.organization_subscriptions (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         uuid NOT NULL REFERENCES vet.organizations(id),
    plan_id                 uuid NOT NULL REFERENCES vet.subscription_plans(id),
    status                  varchar(20) NOT NULL DEFAULT 'TRIAL'
                            CHECK (status IN ('TRIAL','ACTIVE','PAST_DUE','CANCELLED','EXPIRED')),
    billing_period          varchar(15) NOT NULL CHECK (billing_period IN ('MONTHLY','YEARLY')),
    price_snapshot          numeric(14,2) NOT NULL CHECK (price_snapshot >= 0),
    currency                char(3) NOT NULL DEFAULT 'TRY',
    max_branches_snapshot   integer NOT NULL CHECK (max_branches_snapshot > 0),
    max_users_snapshot      integer NOT NULL CHECK (max_users_snapshot > 0),
    starts_at               timestamptz NOT NULL DEFAULT now(),
    current_period_start    timestamptz NOT NULL DEFAULT now(),
    current_period_end      timestamptz NOT NULL,
    next_renewal_at         timestamptz,
    cancelled_at            timestamptz,
    created_by              uuid,
    notes                   text,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_organization_subscriptions_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, created_by) REFERENCES vet.app_users (organization_id, id),
    CHECK (current_period_end > current_period_start),
    CHECK (next_renewal_at IS NULL OR next_renewal_at >= current_period_start)
);
CREATE UNIQUE INDEX ux_one_current_subscription_per_org
    ON vet.organization_subscriptions (organization_id)
    WHERE status IN ('TRIAL','ACTIVE','PAST_DUE');
CREATE INDEX ix_subscriptions_renewal
    ON vet.organization_subscriptions (status, next_renewal_at)
    WHERE status IN ('TRIAL','ACTIVE','PAST_DUE');

-- İlk sürümde gerçek ödeme yerine simülasyon kaydı tutulabilir.
CREATE TABLE vet.subscription_billing_events (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         uuid NOT NULL REFERENCES vet.organizations(id),
    subscription_id         uuid NOT NULL,
    event_no                varchar(70) NOT NULL,
    amount                  numeric(14,2) NOT NULL CHECK (amount >= 0),
    currency                char(3) NOT NULL DEFAULT 'TRY',
    status                  varchar(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','SUCCEEDED','FAILED','REFUNDED','CANCELLED')),
    payment_mode            varchar(20) NOT NULL DEFAULT 'SIMULATED'
                            CHECK (payment_mode IN ('SIMULATED','GATEWAY')),
    billing_period_start    timestamptz,
    billing_period_end      timestamptz,
    processed_at            timestamptz,
    provider_reference      varchar(150),
    failure_reason          text,
    created_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscription_billing_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_subscription_billing_event_no UNIQUE (organization_id, event_no),
    FOREIGN KEY (organization_id, subscription_id)
        REFERENCES vet.organization_subscriptions (organization_id, id),
    CHECK (billing_period_end IS NULL OR billing_period_start IS NULL OR billing_period_end > billing_period_start)
);
CREATE INDEX ix_subscription_billing_status ON vet.subscription_billing_events (organization_id, status, created_at DESC);

-- Kural tüm tenant'lar için platform düzeyinde tanımlanır; oran satış anında kayda kopyalanır.
CREATE TABLE vet.commission_rules (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                varchar(60) NOT NULL UNIQUE,
    name                varchar(150) NOT NULL,
    product_type        varchar(25) NOT NULL
                        CHECK (product_type IN ('MEDICATION','VACCINE','FOOD','CONSUMABLE','SUPPLEMENT','EQUIPMENT','OTHER')),
    rate_percent        numeric(5,2) NOT NULL CHECK (rate_percent BETWEEN 0 AND 100),
    valid_from          timestamptz NOT NULL DEFAULT now(),
    valid_to            timestamptz,
    is_active           boolean NOT NULL DEFAULT true,
    created_by_admin_id uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (created_by_admin_id) REFERENCES vet.platform_admins(id),
    CHECK (valid_to IS NULL OR valid_to > valid_from)
);
CREATE INDEX ix_commission_rules_active
    ON vet.commission_rules (product_type, valid_from, valid_to)
    WHERE is_active;

CREATE TABLE vet.commission_records (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES vet.organizations(id),
    clinic_id           uuid NOT NULL,
    invoice_id          uuid NOT NULL,
    invoice_line_id     uuid NOT NULL,
    commission_rule_id  uuid NOT NULL REFERENCES vet.commission_rules(id),
    eligible_amount     numeric(14,2) NOT NULL CHECK (eligible_amount >= 0),
    rate_percent        numeric(5,2) NOT NULL CHECK (rate_percent BETWEEN 0 AND 100),
    commission_amount  numeric(14,2) NOT NULL CHECK (commission_amount >= 0),
    status              varchar(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','APPROVED','SETTLED','VOID')),
    calculated_at       timestamptz NOT NULL DEFAULT now(),
    settled_at          timestamptz,
    settlement_reference varchar(150),
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_commission_records_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_commission_per_invoice_line_rule UNIQUE (organization_id, invoice_line_id, commission_rule_id),
    FOREIGN KEY (organization_id, clinic_id) REFERENCES vet.clinics (organization_id, id),
    FOREIGN KEY (organization_id, invoice_id) REFERENCES vet.invoices (organization_id, id),
    FOREIGN KEY (organization_id, invoice_line_id) REFERENCES vet.invoice_lines (organization_id, id),
    CHECK ((status <> 'SETTLED') OR settled_at IS NOT NULL)
);
CREATE INDEX ix_commission_records_report
    ON vet.commission_records (organization_id, clinic_id, status, calculated_at DESC);

-- ---------------------------------------------------------------------------
-- 10. Yardımcı görünümler ve indeksler
-- ---------------------------------------------------------------------------
CREATE VIEW vet.v_animal_current_owners WITH (security_invoker = true) AS
SELECT ao.organization_id, ao.animal_id, ao.client_id,
       ao.relationship_type, ao.is_primary_contact, ao.is_legal_owner
FROM vet.animal_owners ao
WHERE ao.valid_to IS NULL;

CREATE INDEX ix_animals_org_name ON vet.animals (organization_id, lower(name));
CREATE INDEX ix_clients_org_name ON vet.clients (organization_id, lower(display_name));
CREATE INDEX ix_care_events_animal_lookup ON vet.animal_care_schedule_events (organization_id, schedule_id, due_at);
CREATE INDEX ix_lab_order_items_status ON vet.lab_order_items (organization_id, status);
CREATE INDEX ix_imaging_orders_animal_date ON vet.imaging_orders (organization_id, animal_id, ordered_at DESC);
CREATE INDEX ix_prescriptions_animal_date ON vet.prescriptions (organization_id, animal_id, prescribed_at DESC);


-- Randevu ve şube bazlı operasyon sorguları.
CREATE INDEX ix_appointments_org_status_start ON vet.appointments (organization_id, status, starts_at);
CREATE INDEX ix_inventory_locations_clinic ON vet.inventory_locations (organization_id, clinic_id, is_active);
CREATE INDEX ix_invoice_lines_product ON vet.invoice_lines (organization_id, product_id) WHERE product_id IS NOT NULL;
CREATE INDEX ix_invoices_clinic_status_date ON vet.invoices (organization_id, clinic_id, status, created_at DESC);
-- ---------------------------------------------------------------------------
-- 11. updated_at alanı olan tablolarda otomatik güncelleme zaman damgası
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION vet.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- POSTED durumundaki stok hareketi değiştirilemez veya silinemez. Düzeltme için ters hareket oluşturulur.
CREATE OR REPLACE FUNCTION vet.prevent_posted_inventory_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    parent_status varchar(20);
    org_id uuid;
    tx_id uuid;
    lines_count bigint;
BEGIN
    IF TG_TABLE_NAME = 'inventory_transactions' THEN
        IF TG_OP = 'UPDATE' AND OLD.status = 'POSTED' THEN
            RAISE EXCEPTION 'POSTED stok hareketi değiştirilemez; ters hareket oluşturun.';
        ELSIF TG_OP = 'DELETE' AND OLD.status = 'POSTED' THEN
            RAISE EXCEPTION 'POSTED stok hareketi silinemez; ters hareket oluşturun.';
        END IF;

        IF TG_OP = 'UPDATE' AND NEW.status = 'POSTED' AND OLD.status <> 'POSTED' THEN
            IF OLD.status <> 'DRAFT' THEN
                RAISE EXCEPTION 'Yalnızca DRAFT stok hareketi POSTED yapılabilir.';
            END IF;
            SELECT COUNT(*) INTO lines_count
            FROM vet.inventory_transaction_lines
            WHERE organization_id = NEW.organization_id AND transaction_id = NEW.id;
            IF lines_count = 0 THEN
                RAISE EXCEPTION 'Satırı olmayan stok hareketi POSTED yapılamaz.';
            END IF;
            IF NEW.transaction_type IN ('RECEIPT','TRANSFER_IN','CUSTOMER_RETURN','OPENING_BALANCE')
               AND EXISTS (SELECT 1 FROM vet.inventory_transaction_lines
                           WHERE organization_id = NEW.organization_id AND transaction_id = NEW.id AND direction <> 'IN') THEN
                RAISE EXCEPTION 'Bu stok hareketi türünde tüm satırlar IN olmalıdır.';
            END IF;
            IF NEW.transaction_type IN ('DISPENSE','TRANSFER_OUT','SUPPLIER_RETURN','WASTE')
               AND EXISTS (SELECT 1 FROM vet.inventory_transaction_lines
                           WHERE organization_id = NEW.organization_id AND transaction_id = NEW.id AND direction <> 'OUT') THEN
                RAISE EXCEPTION 'Bu stok hareketi türünde tüm satırlar OUT olmalıdır.';
            END IF;
            NEW.posted_at := COALESCE(NEW.posted_at, now());
        END IF;

        IF TG_OP = 'DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
    END IF;

    -- Satır INSERT/UPDATE/DELETE işlemlerinde hem eski hem yeni hareketin
    -- POSTED durumunda olmadığını kontrol et; satırın POSTED harekete taşınmasını da engelle.
    IF TG_OP = 'INSERT' OR TG_OP = 'UPDATE' THEN
        SELECT status INTO parent_status
        FROM vet.inventory_transactions
        WHERE organization_id = NEW.organization_id AND id = NEW.transaction_id;
        IF parent_status = 'POSTED' THEN
            RAISE EXCEPTION 'POSTED stok hareketine satır eklenemez veya taşınamaz.';
        END IF;
    END IF;
    IF TG_OP = 'UPDATE' OR TG_OP = 'DELETE' THEN
        SELECT status INTO parent_status
        FROM vet.inventory_transactions
        WHERE organization_id = OLD.organization_id AND id = OLD.transaction_id;
        IF parent_status = 'POSTED' THEN
            RAISE EXCEPTION 'POSTED stok hareketinin satırları değiştirilemez; ters hareket oluşturun.';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
END;
$$;

CREATE TRIGGER trg_inventory_transactions_immutable
BEFORE UPDATE OR DELETE ON vet.inventory_transactions
FOR EACH ROW EXECUTE FUNCTION vet.prevent_posted_inventory_mutation();

CREATE TRIGGER trg_inventory_transaction_lines_immutable
BEFORE INSERT OR UPDATE OR DELETE ON vet.inventory_transaction_lines
FOR EACH ROW EXECUTE FUNCTION vet.prevent_posted_inventory_mutation();

-- Denetim kayıtları uygulama tarafından yalnızca eklenir; geçmişe dönük değiştirilmez.
CREATE OR REPLACE FUNCTION vet.prevent_audit_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Denetim kayıtları değiştirilemez veya silinemez.';
END;
$$;
CREATE TRIGGER trg_audit_logs_immutable
BEFORE UPDATE OR DELETE ON vet.audit_logs
FOR EACH ROW EXECUTE FUNCTION vet.prevent_audit_mutation();



-- Stok hareketi POSTED yapılırken, eşzamanlı işlemleri de dikkate alarak bakiyenin
-- sıfırın altına inmesini engeller. Tüm stok post işlemleri aynı advisory-lock anahtarını kullanır.
CREATE OR REPLACE FUNCTION vet.guard_inventory_stock_before_posting()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    r record;
    available_qty numeric(14,3);
    resulting_qty numeric(14,3);
    lock_key bigint;
BEGIN
    IF TG_OP <> 'UPDATE' OR NEW.status <> 'POSTED' OR OLD.status = 'POSTED' THEN
        RETURN NEW;
    END IF;

    FOR r IN
        SELECT l.product_id, l.lot_id,
               SUM(CASE WHEN l.direction = 'IN' THEN l.quantity ELSE -l.quantity END)::numeric(14,3) AS delta_qty
        FROM vet.inventory_transaction_lines l
        WHERE l.organization_id = NEW.organization_id
          AND l.transaction_id = NEW.id
        GROUP BY l.product_id, l.lot_id
        ORDER BY l.product_id, l.lot_id NULLS FIRST
    LOOP
        lock_key := hashtextextended(
            NEW.organization_id::text || ':' || NEW.location_id::text || ':' ||
            r.product_id::text || ':' || COALESCE(r.lot_id::text, '<NO_LOT>'), 0);
        PERFORM pg_advisory_xact_lock(lock_key);

        SELECT COALESCE(SUM(CASE WHEN line.direction = 'IN' THEN line.quantity ELSE -line.quantity END), 0)
          INTO available_qty
        FROM vet.inventory_transactions tx
        JOIN vet.inventory_transaction_lines line
          ON line.organization_id = tx.organization_id AND line.transaction_id = tx.id
        WHERE tx.organization_id = NEW.organization_id
          AND tx.location_id = NEW.location_id
          AND tx.status = 'POSTED'
          AND tx.id <> NEW.id
          AND line.product_id = r.product_id
          AND line.lot_id IS NOT DISTINCT FROM r.lot_id;

        resulting_qty := COALESCE(available_qty, 0) + r.delta_qty;
        IF resulting_qty < 0 THEN
            RAISE EXCEPTION 'Yetersiz stok. Ürün %, lot %, mevcut %, işlem sonrası %.',
                r.product_id, r.lot_id, COALESCE(available_qty, 0), resulting_qty
                USING ERRCODE = '23514';
        END IF;
    END LOOP;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_a_inventory_stock_guard
BEFORE UPDATE OF status ON vet.inventory_transactions
FOR EACH ROW EXECUTE FUNCTION vet.guard_inventory_stock_before_posting();

-- Fatura yalnızca DRAFT durumundayken düzenlenebilir. ISSUE anında stok çıkışı ve
-- komisyon kayıtları aynı veritabanı işlemi içinde yazılır; stok yetersizse fatura da kesilmez.
CREATE OR REPLACE FUNCTION vet.guard_invoice_line_draft_only()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    current_status varchar(20);
    org_id uuid;
    inv_id uuid;
BEGIN
    IF TG_OP = 'DELETE' THEN
        org_id := OLD.organization_id;
        inv_id := OLD.invoice_id;
    ELSE
        org_id := NEW.organization_id;
        inv_id := NEW.invoice_id;
    END IF;

    SELECT status INTO current_status
    FROM vet.invoices
    WHERE organization_id = org_id AND id = inv_id
    FOR UPDATE;

    IF current_status IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION 'Yalnızca DRAFT durumundaki faturanın satırları değiştirilebilir.';
    END IF;

    IF TG_OP = 'DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
END;
$$;
CREATE TRIGGER trg_invoice_lines_draft_only
BEFORE INSERT OR UPDATE OR DELETE ON vet.invoice_lines
FOR EACH ROW EXECUTE FUNCTION vet.guard_invoice_line_draft_only();

CREATE OR REPLACE FUNCTION vet.post_invoice_inventory_and_commission()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    sale_tx_id uuid;
    sale_tx_no varchar(60);
    has_stock_items boolean;
BEGIN
    IF OLD.status = NEW.status THEN
        RETURN NEW;
    END IF;

    IF OLD.status = 'DRAFT' THEN
        IF NEW.status NOT IN ('ISSUED','VOID') THEN
            RAISE EXCEPTION 'DRAFT faturadan yalnızca ISSUED veya VOID durumuna geçilebilir.';
        END IF;
    ELSIF OLD.status = 'ISSUED' THEN
        IF NEW.status NOT IN ('PARTIALLY_PAID','PAID','OVERDUE') THEN
            RAISE EXCEPTION 'Düzenlenmiş fatura geriye alınamaz veya doğrudan VOID yapılamaz; iade/alacak dekontu kullanın.';
        END IF;
    ELSIF OLD.status = 'PARTIALLY_PAID' THEN
        IF NEW.status NOT IN ('PAID','OVERDUE') THEN
            RAISE EXCEPTION 'Kısmen ödenmiş faturanın geçişi PAID veya OVERDUE olmalıdır.';
        END IF;
    ELSIF OLD.status = 'OVERDUE' THEN
        IF NEW.status NOT IN ('PARTIALLY_PAID','PAID') THEN
            RAISE EXCEPTION 'Gecikmiş fatura yalnızca ödeme sonrası duruma geçebilir.';
        END IF;
    ELSE
        RAISE EXCEPTION 'PAID veya VOID faturanın durumu değiştirilemez.';
    END IF;

    IF NOT (OLD.status = 'DRAFT' AND NEW.status = 'ISSUED') THEN
        RETURN NEW;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM vet.invoice_lines il
        WHERE il.organization_id = NEW.organization_id AND il.invoice_id = NEW.id
    ) THEN
        RAISE EXCEPTION 'Satırı bulunmayan fatura düzenlenemez.';
    END IF;

    NEW.issued_at := COALESCE(NEW.issued_at, now());

    -- Proforma ve alacak dekontu satış değildir; stok/iade hareketi ayrı ve açık bir işlem olmalıdır.
    IF NEW.invoice_type <> 'SALES' THEN
        RETURN NEW;
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM vet.invoice_lines il
        JOIN vet.products p
          ON p.organization_id = il.organization_id AND p.id = il.product_id
        WHERE il.organization_id = NEW.organization_id
          AND il.invoice_id = NEW.id
          AND il.line_type = 'PRODUCT'
          AND p.is_stock_item
    ) INTO has_stock_items;

    IF has_stock_items AND NEW.inventory_location_id IS NULL THEN
        RAISE EXCEPTION 'Stok kalemi içeren faturanın inventory_location_id alanı belirtilmelidir.';
    END IF;

    IF has_stock_items THEN
        IF NOT EXISTS (
            SELECT 1 FROM vet.inventory_locations loc
            WHERE loc.organization_id = NEW.organization_id
              AND loc.id = NEW.inventory_location_id
              AND loc.clinic_id = NEW.clinic_id
              AND loc.is_active
        ) THEN
            RAISE EXCEPTION 'Faturadaki stok lokasyonu aktif olmalı ve faturanın şubesine ait olmalıdır.';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM vet.invoice_lines il
            JOIN vet.products p
              ON p.organization_id = il.organization_id AND p.id = il.product_id
            LEFT JOIN vet.inventory_lots lot
              ON lot.organization_id = il.organization_id
             AND lot.product_id = il.product_id
             AND lot.id = il.lot_id
            WHERE il.organization_id = NEW.organization_id
              AND il.invoice_id = NEW.id
              AND il.line_type = 'PRODUCT'
              AND p.is_stock_item
              AND ((p.tracks_lot AND il.lot_id IS NULL)
                   OR (il.lot_id IS NOT NULL AND (lot.id IS NULL OR (lot.expires_at IS NOT NULL AND lot.expires_at < CURRENT_DATE))))
        ) THEN
            RAISE EXCEPTION 'Lot takibi zorunlu olabilir veya lot geçersiz/süresi geçmiş olabilir; ürün lot bilgilerini kontrol edin.';
        END IF;

        sale_tx_no := 'SALE-' || replace(NEW.id::text, '-', '');
        INSERT INTO vet.inventory_transactions (
            organization_id, location_id, transaction_no, transaction_type, status,
            occurred_at, reference_type, reference_id, notes, created_by
        ) VALUES (
            NEW.organization_id, NEW.inventory_location_id, sale_tx_no, 'DISPENSE', 'DRAFT',
            COALESCE(NEW.issued_at, now()), 'INVOICE', NEW.id,
            'Fatura düzenleme sırasında otomatik oluşturulan stok çıkışı.', NEW.created_by
        ) RETURNING id INTO sale_tx_id;

        INSERT INTO vet.inventory_transaction_lines (
            organization_id, transaction_id, product_id, lot_id,
            direction, quantity, unit_cost, line_note
        )
        SELECT il.organization_id, sale_tx_id, il.product_id, il.lot_id,
               'OUT', il.quantity,
               COALESCE(lot.unit_cost, 0),
               'Fatura ' || NEW.invoice_no
        FROM vet.invoice_lines il
        JOIN vet.products p
          ON p.organization_id = il.organization_id AND p.id = il.product_id
        LEFT JOIN vet.inventory_lots lot
          ON lot.organization_id = il.organization_id
         AND lot.product_id = il.product_id
         AND lot.id = il.lot_id
        WHERE il.organization_id = NEW.organization_id
          AND il.invoice_id = NEW.id
          AND il.line_type = 'PRODUCT'
          AND p.is_stock_item;

        UPDATE vet.inventory_transactions
        SET status = 'POSTED', posted_by = NEW.created_by, posted_at = now()
        WHERE organization_id = NEW.organization_id AND id = sale_tx_id;
    END IF;

    -- Komisyon, satış anındaki fatura satırı net tutarı üzerinden hesaplanır (KDV hariç).
    INSERT INTO vet.commission_records (
        organization_id, clinic_id, invoice_id, invoice_line_id, commission_rule_id,
        eligible_amount, rate_percent, commission_amount, status
    )
    SELECT NEW.organization_id, NEW.clinic_id, NEW.id, il.id, cr.id,
           il.net_amount, cr.rate_percent,
           round(il.net_amount * cr.rate_percent / 100, 2), 'PENDING'
    FROM vet.invoice_lines il
    JOIN vet.products p
      ON p.organization_id = il.organization_id AND p.id = il.product_id
    JOIN LATERAL (
        SELECT rule.id, rule.rate_percent
        FROM vet.commission_rules rule
        WHERE rule.product_type = p.product_type
          AND rule.is_active
          AND rule.valid_from <= NEW.issued_at
          AND (rule.valid_to IS NULL OR rule.valid_to > NEW.issued_at)
        ORDER BY rule.valid_from DESC, rule.created_at DESC
        LIMIT 1
    ) cr ON true
    WHERE il.organization_id = NEW.organization_id
      AND il.invoice_id = NEW.id
      AND il.line_type = 'PRODUCT'
    ON CONFLICT (organization_id, invoice_line_id, commission_rule_id) DO NOTHING;

    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_invoice_post_inventory_commission
BEFORE UPDATE OF status ON vet.invoices
FOR EACH ROW EXECUTE FUNCTION vet.post_invoice_inventory_and_commission();

DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT table_schema, table_name
        FROM information_schema.columns
        WHERE table_schema = 'vet'
          AND column_name = 'updated_at'
        GROUP BY table_schema, table_name
    LOOP
        EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE ON %I.%I FOR EACH ROW EXECUTE FUNCTION vet.set_updated_at()',
                       'trg_' || r.table_name || '_updated_at', r.table_schema, r.table_name);
    END LOOP;
END;
$$;


-- Şube düzeyinde RLS için doğrulanmış uygulama kullanıcısının aktif rol/şube eşleşmesini denetler.
-- Backend her transaction başında app.organization_id, app.user_id ve şube işlemlerinde app.clinic_id değerlerini
-- doğrulanmış oturum/JWT bağlamından ayarlamalıdır. Müşteri/hayvanı başka şubeyle paylaşmak için
-- ilgili client_clinic_access ve animal_clinic_access kayıtları oluşturulmalıdır.
CREATE OR REPLACE FUNCTION vet.current_user_has_clinic_access(p_organization_id uuid, p_clinic_id uuid)
RETURNS boolean
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    current_user_id uuid;
BEGIN
    current_user_id := NULLIF(current_setting('app.user_id', true), '')::uuid;
    IF current_user_id IS NULL OR p_organization_id IS NULL OR p_clinic_id IS NULL THEN
        RETURN false;
    END IF;

    RETURN EXISTS (
        SELECT 1
        FROM vet.user_clinic_roles ucr
        JOIN vet.app_users u
          ON u.organization_id = ucr.organization_id AND u.id = ucr.user_id
        WHERE ucr.organization_id = p_organization_id
          AND ucr.user_id = current_user_id
          AND ucr.clinic_id = p_clinic_id
          AND u.is_active
          AND ucr.valid_from <= CURRENT_DATE
          AND (ucr.valid_to IS NULL OR ucr.valid_to >= CURRENT_DATE)
    );
END;
$$;


-- Yeni müşteri/hayvan kaydı, doğrulanmış uygulama bağlamındaki şubeye otomatik yetki alır.
-- app.clinic_id kullanıcı oturumundan değil, backend'in doğrulanmış tenant/rol bağlamından set edilmelidir.
CREATE OR REPLACE FUNCTION vet.assign_new_record_to_current_clinic()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    current_clinic_id uuid;
    current_user_id uuid;
BEGIN
    current_clinic_id := NULLIF(current_setting('app.clinic_id', true), '')::uuid;
    current_user_id := NULLIF(current_setting('app.user_id', true), '')::uuid;

    IF current_clinic_id IS NULL OR NOT vet.current_user_has_clinic_access(NEW.organization_id, current_clinic_id) THEN
        RAISE EXCEPTION 'Müşteri/hayvan kaydı için aktif ve yetkili app.clinic_id bağlamı gereklidir.';
    END IF;

    IF TG_TABLE_NAME = 'clients' THEN
        INSERT INTO vet.client_clinic_access (
            organization_id, client_id, clinic_id, access_level, granted_by
        ) VALUES (
            NEW.organization_id, NEW.id, current_clinic_id, 'FULL', current_user_id
        )
        ON CONFLICT (organization_id, client_id, clinic_id) WHERE revoked_at IS NULL DO NOTHING;
    ELSE
        INSERT INTO vet.animal_clinic_access (
            organization_id, animal_id, clinic_id, access_level, granted_by
        ) VALUES (
            NEW.organization_id, NEW.id, current_clinic_id, 'FULL', current_user_id
        )
        ON CONFLICT (organization_id, animal_id, clinic_id) WHERE revoked_at IS NULL DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_clients_assign_initial_clinic_access
AFTER INSERT ON vet.clients
FOR EACH ROW EXECUTE FUNCTION vet.assign_new_record_to_current_clinic();
CREATE TRIGGER trg_animals_assign_initial_clinic_access
AFTER INSERT ON vet.animals
FOR EACH ROW EXECUTE FUNCTION vet.assign_new_record_to_current_clinic();


-- Hayvan sahibi mobil hesabı: aktif OWNER/CO_OWNER/GUARDIAN ilişkileri üzerinden hayvan erişimi verilir.
-- Sahiplik ilişkisi sonlandırıldığında backend, başka aktif sahiplik ilişkisi kalmadıysa erişimi iptal etmelidir.
CREATE OR REPLACE FUNCTION vet.grant_owner_user_animal_access()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    linked_user_id uuid;
BEGIN
    IF TG_OP = 'DELETE' OR (TG_OP = 'UPDATE' AND (NEW.valid_to IS NOT NULL OR NEW.relationship_type NOT IN ('OWNER','CO_OWNER','GUARDIAN') OR NEW.client_id <> OLD.client_id OR NEW.animal_id <> OLD.animal_id)) THEN
        SELECT c.app_user_id INTO linked_user_id
        FROM vet.clients c
        WHERE c.organization_id = OLD.organization_id AND c.id = OLD.client_id;

        IF linked_user_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM vet.animal_owners ao
            WHERE ao.organization_id = OLD.organization_id
              AND ao.animal_id = OLD.animal_id
              AND ao.client_id = OLD.client_id
              AND ao.valid_to IS NULL
              AND ao.relationship_type IN ('OWNER','CO_OWNER','GUARDIAN')
        ) THEN
            UPDATE vet.user_animal_access
            SET revoked_at = now()
            WHERE organization_id = OLD.organization_id
              AND animal_id = OLD.animal_id
              AND user_id = linked_user_id
              AND revoked_at IS NULL;
        END IF;
    END IF;

    IF TG_OP <> 'DELETE' AND NEW.valid_to IS NULL AND NEW.relationship_type IN ('OWNER','CO_OWNER','GUARDIAN') THEN
        SELECT c.app_user_id INTO linked_user_id
        FROM vet.clients c
        WHERE c.organization_id = NEW.organization_id AND c.id = NEW.client_id;

        IF linked_user_id IS NOT NULL THEN
            INSERT INTO vet.user_animal_access (organization_id, animal_id, user_id, access_source)
            VALUES (NEW.organization_id, NEW.animal_id, linked_user_id, 'OWNER_RELATIONSHIP')
            ON CONFLICT (organization_id, animal_id, user_id) WHERE revoked_at IS NULL DO NOTHING;
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
END;
$$;
CREATE TRIGGER trg_animal_owner_mobile_access
AFTER INSERT OR UPDATE OR DELETE ON vet.animal_owners
FOR EACH ROW EXECUTE FUNCTION vet.grant_owner_user_animal_access();

-- ---------------------------------------------------------------------------
-- 12. Organizasyon bazlı satır düzeyinde güvenlik (RLS)
-- Uygulama, her işlem (transaction) içinde SET LOCAL app.organization_id = '<UUID>'
-- komutunu çalıştırmalıdır. Klinik web isteğinde app.user_id ve app.clinic_id de SET LOCAL ile ayarlanmalıdır.
-- Değişken tanımlı değilse ilgili tenant/şube satırları görünmez. SUPERUSER ve BYPASSRLS rolleri
-- RLS'yi aşabilir; uygulama rolünü bu yetkilerle çalıştırmayın. Şubeler arası hasta paylaşımı için
-- client_clinic_access ve animal_clinic_access kayıtları kullanılır.
-- ---------------------------------------------------------------------------
ALTER TABLE vet.organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE vet.organizations FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_self_access ON vet.organizations;
CREATE POLICY tenant_self_access ON vet.organizations
    USING (id = NULLIF(current_setting('app.organization_id', true), '')::uuid)
    WITH CHECK (id = NULLIF(current_setting('app.organization_id', true), '')::uuid);

DO $$
DECLARE
    r record;
    policy_expr text := 'organization_id = NULLIF(current_setting(''app.organization_id'', true), '''')::uuid';
BEGIN
    FOR r IN
        SELECT DISTINCT c.table_name
        FROM information_schema.columns c
        JOIN information_schema.tables t
          ON t.table_schema = c.table_schema
         AND t.table_name = c.table_name
         AND t.table_type = 'BASE TABLE'
        WHERE c.table_schema = 'vet'
          AND c.column_name = 'organization_id'
    LOOP
        EXECUTE format('ALTER TABLE vet.%I ENABLE ROW LEVEL SECURITY', r.table_name);
        EXECUTE format('ALTER TABLE vet.%I FORCE ROW LEVEL SECURITY', r.table_name);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON vet.%I', r.table_name);
        EXECUTE format('CREATE POLICY tenant_isolation ON vet.%I USING (%s) WITH CHECK (%s)',
                       r.table_name, policy_expr, policy_expr);
    END LOOP;

END;
$$;


-- Paylaşılan müşteri/hayvan kayıtları sadece erişim eşlemesi bulunan şubeler tarafından görülebilir.
DROP POLICY IF EXISTS tenant_isolation ON vet.client_clinic_access;
CREATE POLICY tenant_branch_isolation ON vet.client_clinic_access
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_access(organization_id, clinic_id)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_access(organization_id, clinic_id)
);

DROP POLICY IF EXISTS tenant_isolation ON vet.animal_clinic_access;
CREATE POLICY tenant_branch_isolation ON vet.animal_clinic_access
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_access(organization_id, clinic_id)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_access(organization_id, clinic_id)
);

DROP POLICY IF EXISTS tenant_isolation ON vet.clients;
CREATE POLICY tenant_branch_isolation ON vet.clients
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND (
        app_user_id = NULLIF(current_setting('app.user_id', true), '')::uuid
        OR EXISTS (
            SELECT 1 FROM vet.client_clinic_access ca
            WHERE ca.organization_id = clients.organization_id
              AND ca.client_id = clients.id
              AND ca.revoked_at IS NULL
              AND vet.current_user_has_clinic_access(ca.organization_id, ca.clinic_id)
        )
    )
)
WITH CHECK (organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON vet.user_animal_access;
CREATE POLICY owner_or_clinic_animal_access ON vet.user_animal_access
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND (
        user_id = NULLIF(current_setting('app.user_id', true), '')::uuid
        OR EXISTS (
            SELECT 1 FROM vet.animal_clinic_access aa
            WHERE aa.organization_id = user_animal_access.organization_id
              AND aa.animal_id = user_animal_access.animal_id
              AND aa.revoked_at IS NULL
              AND vet.current_user_has_clinic_access(aa.organization_id, aa.clinic_id)
        )
    )
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (
        SELECT 1 FROM vet.animal_clinic_access aa
        WHERE aa.organization_id = user_animal_access.organization_id
          AND aa.animal_id = user_animal_access.animal_id
          AND aa.revoked_at IS NULL
          AND vet.current_user_has_clinic_access(aa.organization_id, aa.clinic_id)
    )
);

DROP POLICY IF EXISTS tenant_isolation ON vet.animals;
CREATE POLICY tenant_branch_isolation ON vet.animals
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND (
        EXISTS (
            SELECT 1 FROM vet.animal_clinic_access aa
            WHERE aa.organization_id = animals.organization_id
              AND aa.animal_id = animals.id
              AND aa.revoked_at IS NULL
              AND vet.current_user_has_clinic_access(aa.organization_id, aa.clinic_id)
        )
        OR EXISTS (
            SELECT 1 FROM vet.user_animal_access ua
            WHERE ua.organization_id = animals.organization_id
              AND ua.animal_id = animals.id
              AND ua.user_id = NULLIF(current_setting('app.user_id', true), '')::uuid
              AND ua.revoked_at IS NULL
        )
    )
)
WITH CHECK (organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON vet.client_contacts;
CREATE POLICY tenant_branch_isolation ON vet.client_contacts
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.clients c WHERE c.organization_id = client_contacts.organization_id AND c.id = client_contacts.client_id)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.clients c WHERE c.organization_id = client_contacts.organization_id AND c.id = client_contacts.client_id)
);

DROP POLICY IF EXISTS tenant_isolation ON vet.client_addresses;
CREATE POLICY tenant_branch_isolation ON vet.client_addresses
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.clients c WHERE c.organization_id = client_addresses.organization_id AND c.id = client_addresses.client_id)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.clients c WHERE c.organization_id = client_addresses.organization_id AND c.id = client_addresses.client_id)
);

DROP POLICY IF EXISTS tenant_isolation ON vet.animal_owners;
CREATE POLICY tenant_branch_isolation ON vet.animal_owners
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.animals a WHERE a.organization_id = animal_owners.organization_id AND a.id = animal_owners.animal_id)
    AND EXISTS (SELECT 1 FROM vet.clients c WHERE c.organization_id = animal_owners.organization_id AND c.id = animal_owners.client_id)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.animals a WHERE a.organization_id = animal_owners.organization_id AND a.id = animal_owners.animal_id)
    AND EXISTS (SELECT 1 FROM vet.clients c WHERE c.organization_id = animal_owners.organization_id AND c.id = animal_owners.client_id)
);

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['animal_identifiers','animal_weights','animal_allergies','animal_care_schedules'] LOOP
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON vet.%I', t);
        EXECUTE format(
            'CREATE POLICY tenant_branch_isolation ON vet.%I USING (organization_id = NULLIF(current_setting(''app.organization_id'', true), '''')::uuid AND EXISTS (SELECT 1 FROM vet.animals a WHERE a.organization_id = %I.organization_id AND a.id = %I.animal_id)) WITH CHECK (organization_id = NULLIF(current_setting(''app.organization_id'', true), '''')::uuid AND EXISTS (SELECT 1 FROM vet.animals a WHERE a.organization_id = %I.organization_id AND a.id = %I.animal_id))',
            t, t, t, t, t
        );
    END LOOP;
END;
$$;

DROP POLICY IF EXISTS tenant_isolation ON vet.animal_care_schedule_events;
CREATE POLICY tenant_branch_isolation ON vet.animal_care_schedule_events
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.animal_care_schedules sc
                WHERE sc.organization_id = animal_care_schedule_events.organization_id AND sc.id = animal_care_schedule_events.schedule_id)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.animal_care_schedules sc
                WHERE sc.organization_id = animal_care_schedule_events.organization_id AND sc.id = animal_care_schedule_events.schedule_id)
);

-- Operasyonel tablolar için tenant + şube kapsamı birlikte zorlanır.
-- Klinikleri listelemek/kurmak tenant kapsamında kalır; şube işlemleri için ayrıca aktif user_clinic_roles gerekir.
DO $$
DECLARE
    t text;
    expr text := 'organization_id = NULLIF(current_setting(''app.organization_id'', true), '''')::uuid AND vet.current_user_has_clinic_access(organization_id, clinic_id)';
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'appointments', 'encounters', 'veterinarian_working_hours', 'inventory_locations',
        'purchase_orders', 'lab_orders', 'imaging_orders', 'invoices', 'payments',
        'expenses', 'commission_records'
    ] LOOP
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON vet.%I', t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_branch_isolation ON vet.%I', t);
        EXECUTE format('CREATE POLICY tenant_branch_isolation ON vet.%I USING (%s) WITH CHECK (%s)', t, expr, expr);
    END LOOP;

END;
$$;


DROP POLICY IF EXISTS tenant_branch_isolation ON vet.lab_orders;
CREATE POLICY tenant_branch_isolation ON vet.lab_orders
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_access(organization_id, clinic_id)
    AND EXISTS (SELECT 1 FROM vet.animal_clinic_access aa
                WHERE aa.organization_id = lab_orders.organization_id
                  AND aa.animal_id = lab_orders.animal_id
                  AND aa.clinic_id = lab_orders.clinic_id
                  AND aa.revoked_at IS NULL)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_access(organization_id, clinic_id)
    AND EXISTS (SELECT 1 FROM vet.animal_clinic_access aa
                WHERE aa.organization_id = lab_orders.organization_id
                  AND aa.animal_id = lab_orders.animal_id
                  AND aa.clinic_id = lab_orders.clinic_id
                  AND aa.revoked_at IS NULL)
);

DROP POLICY IF EXISTS tenant_branch_isolation ON vet.imaging_orders;
CREATE POLICY tenant_branch_isolation ON vet.imaging_orders
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_access(organization_id, clinic_id)
    AND EXISTS (SELECT 1 FROM vet.animal_clinic_access aa
                WHERE aa.organization_id = imaging_orders.organization_id
                  AND aa.animal_id = imaging_orders.animal_id
                  AND aa.clinic_id = imaging_orders.clinic_id
                  AND aa.revoked_at IS NULL)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_access(organization_id, clinic_id)
    AND EXISTS (SELECT 1 FROM vet.animal_clinic_access aa
                WHERE aa.organization_id = imaging_orders.organization_id
                  AND aa.animal_id = imaging_orders.animal_id
                  AND aa.clinic_id = imaging_orders.clinic_id
                  AND aa.revoked_at IS NULL)
);

DROP POLICY IF EXISTS tenant_branch_isolation ON vet.appointments;
CREATE POLICY tenant_branch_isolation ON vet.appointments
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_access(organization_id, clinic_id)
    AND EXISTS (SELECT 1 FROM vet.animal_clinic_access aa
                WHERE aa.organization_id = appointments.organization_id
                  AND aa.animal_id = appointments.animal_id
                  AND aa.clinic_id = appointments.clinic_id
                  AND aa.revoked_at IS NULL)
    AND EXISTS (SELECT 1 FROM vet.client_clinic_access ca
                WHERE ca.organization_id = appointments.organization_id
                  AND ca.client_id = appointments.client_id
                  AND ca.clinic_id = appointments.clinic_id
                  AND ca.revoked_at IS NULL)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_access(organization_id, clinic_id)
    AND EXISTS (SELECT 1 FROM vet.animal_clinic_access aa
                WHERE aa.organization_id = appointments.organization_id
                  AND aa.animal_id = appointments.animal_id
                  AND aa.clinic_id = appointments.clinic_id
                  AND aa.revoked_at IS NULL)
    AND EXISTS (SELECT 1 FROM vet.client_clinic_access ca
                WHERE ca.organization_id = appointments.organization_id
                  AND ca.client_id = appointments.client_id
                  AND ca.clinic_id = appointments.clinic_id
                  AND ca.revoked_at IS NULL)
);

DROP POLICY IF EXISTS tenant_branch_isolation ON vet.encounters;
CREATE POLICY tenant_branch_isolation ON vet.encounters
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_access(organization_id, clinic_id)
    AND EXISTS (SELECT 1 FROM vet.animal_clinic_access aa
                WHERE aa.organization_id = encounters.organization_id
                  AND aa.animal_id = encounters.animal_id
                  AND aa.clinic_id = encounters.clinic_id
                  AND aa.revoked_at IS NULL)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_access(organization_id, clinic_id)
    AND EXISTS (SELECT 1 FROM vet.animal_clinic_access aa
                WHERE aa.organization_id = encounters.organization_id
                  AND aa.animal_id = encounters.animal_id
                  AND aa.clinic_id = encounters.clinic_id
                  AND aa.revoked_at IS NULL)
);


DROP POLICY IF EXISTS tenant_branch_isolation ON vet.invoices;
CREATE POLICY tenant_branch_isolation ON vet.invoices
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_access(organization_id, clinic_id)
    AND EXISTS (SELECT 1 FROM vet.client_clinic_access ca
                WHERE ca.organization_id = invoices.organization_id
                  AND ca.client_id = invoices.client_id
                  AND ca.clinic_id = invoices.clinic_id
                  AND ca.revoked_at IS NULL)
    AND (animal_id IS NULL OR EXISTS (SELECT 1 FROM vet.animal_clinic_access aa
                WHERE aa.organization_id = invoices.organization_id
                  AND aa.animal_id = invoices.animal_id
                  AND aa.clinic_id = invoices.clinic_id
                  AND aa.revoked_at IS NULL))
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_access(organization_id, clinic_id)
    AND EXISTS (SELECT 1 FROM vet.client_clinic_access ca
                WHERE ca.organization_id = invoices.organization_id
                  AND ca.client_id = invoices.client_id
                  AND ca.clinic_id = invoices.clinic_id
                  AND ca.revoked_at IS NULL)
    AND (animal_id IS NULL OR EXISTS (SELECT 1 FROM vet.animal_clinic_access aa
                WHERE aa.organization_id = invoices.organization_id
                  AND aa.animal_id = invoices.animal_id
                  AND aa.clinic_id = invoices.clinic_id
                  AND aa.revoked_at IS NULL))
);

DROP POLICY IF EXISTS tenant_branch_isolation ON vet.payments;
CREATE POLICY tenant_branch_isolation ON vet.payments
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_access(organization_id, clinic_id)
    AND EXISTS (SELECT 1 FROM vet.client_clinic_access ca
                WHERE ca.organization_id = payments.organization_id
                  AND ca.client_id = payments.client_id
                  AND ca.clinic_id = payments.clinic_id
                  AND ca.revoked_at IS NULL)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND vet.current_user_has_clinic_access(organization_id, clinic_id)
    AND EXISTS (SELECT 1 FROM vet.client_clinic_access ca
                WHERE ca.organization_id = payments.organization_id
                  AND ca.client_id = payments.client_id
                  AND ca.clinic_id = payments.clinic_id
                  AND ca.revoked_at IS NULL)
);


-- Hayvan sahibi mobil uygulaması için kendi hayvanlarının kayıtlarını okuma ve randevu talebi oluşturma izni.
CREATE POLICY owner_select_appointments ON vet.appointments
FOR SELECT USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.user_animal_access ua
                WHERE ua.organization_id = appointments.organization_id
                  AND ua.animal_id = appointments.animal_id
                  AND ua.user_id = NULLIF(current_setting('app.user_id', true), '')::uuid
                  AND ua.revoked_at IS NULL)
    AND EXISTS (SELECT 1 FROM vet.clients c
                WHERE c.organization_id = appointments.organization_id
                  AND c.id = appointments.client_id
                  AND c.app_user_id = NULLIF(current_setting('app.user_id', true), '')::uuid)
);

CREATE POLICY owner_insert_appointment_request ON vet.appointments
FOR INSERT WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND booking_channel = 'MOBILE'
    AND status = 'REQUESTED'
    AND EXISTS (SELECT 1 FROM vet.user_animal_access ua
                WHERE ua.organization_id = appointments.organization_id
                  AND ua.animal_id = appointments.animal_id
                  AND ua.user_id = NULLIF(current_setting('app.user_id', true), '')::uuid
                  AND ua.revoked_at IS NULL)
    AND EXISTS (SELECT 1 FROM vet.clients c
                WHERE c.organization_id = appointments.organization_id
                  AND c.id = appointments.client_id
                  AND c.app_user_id = NULLIF(current_setting('app.user_id', true), '')::uuid)
    AND EXISTS (SELECT 1 FROM vet.animal_clinic_access aa
                WHERE aa.organization_id = appointments.organization_id
                  AND aa.animal_id = appointments.animal_id
                  AND aa.clinic_id = appointments.clinic_id
                  AND aa.revoked_at IS NULL)
    AND EXISTS (SELECT 1 FROM vet.client_clinic_access ca
                WHERE ca.organization_id = appointments.organization_id
                  AND ca.client_id = appointments.client_id
                  AND ca.clinic_id = appointments.clinic_id
                  AND ca.revoked_at IS NULL)
);

CREATE POLICY owner_cancel_appointment_request ON vet.appointments
FOR UPDATE USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND status IN ('REQUESTED','BOOKED','CONFIRMED')
    AND EXISTS (SELECT 1 FROM vet.user_animal_access ua
                WHERE ua.organization_id = appointments.organization_id
                  AND ua.animal_id = appointments.animal_id
                  AND ua.user_id = NULLIF(current_setting('app.user_id', true), '')::uuid
                  AND ua.revoked_at IS NULL)
    AND EXISTS (SELECT 1 FROM vet.clients c
                WHERE c.organization_id = appointments.organization_id
                  AND c.id = appointments.client_id
                  AND c.app_user_id = NULLIF(current_setting('app.user_id', true), '')::uuid)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND status = 'CANCELLED'
    AND EXISTS (SELECT 1 FROM vet.user_animal_access ua
                WHERE ua.organization_id = appointments.organization_id
                  AND ua.animal_id = appointments.animal_id
                  AND ua.user_id = NULLIF(current_setting('app.user_id', true), '')::uuid
                  AND ua.revoked_at IS NULL)
    AND EXISTS (SELECT 1 FROM vet.clients c
                WHERE c.organization_id = appointments.organization_id
                  AND c.id = appointments.client_id
                  AND c.app_user_id = NULLIF(current_setting('app.user_id', true), '')::uuid)
);

CREATE POLICY owner_select_encounters ON vet.encounters
FOR SELECT USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.user_animal_access ua
                WHERE ua.organization_id = encounters.organization_id
                  AND ua.animal_id = encounters.animal_id
                  AND ua.user_id = NULLIF(current_setting('app.user_id', true), '')::uuid
                  AND ua.revoked_at IS NULL)
);

CREATE POLICY owner_select_invoices ON vet.invoices
FOR SELECT USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.clients c
                WHERE c.organization_id = invoices.organization_id
                  AND c.id = invoices.client_id
                  AND c.app_user_id = NULLIF(current_setting('app.user_id', true), '')::uuid)
);

CREATE POLICY owner_select_payments ON vet.payments
FOR SELECT USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.clients c
                WHERE c.organization_id = payments.organization_id
                  AND c.id = payments.client_id
                  AND c.app_user_id = NULLIF(current_setting('app.user_id', true), '')::uuid)
);

-- Şubeye ait alt kayıtlar, bağlı oldukları ana kaydın şube yetkisini devralır.
DROP POLICY IF EXISTS tenant_isolation ON vet.inventory_transactions;
CREATE POLICY tenant_branch_isolation ON vet.inventory_transactions
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (
        SELECT 1 FROM vet.inventory_locations loc
        WHERE loc.organization_id = inventory_transactions.organization_id
          AND loc.id = inventory_transactions.location_id
          AND vet.current_user_has_clinic_access(loc.organization_id, loc.clinic_id)
    )
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (
        SELECT 1 FROM vet.inventory_locations loc
        WHERE loc.organization_id = inventory_transactions.organization_id
          AND loc.id = inventory_transactions.location_id
          AND vet.current_user_has_clinic_access(loc.organization_id, loc.clinic_id)
    )
);

DROP POLICY IF EXISTS tenant_isolation ON vet.inventory_transaction_lines;
CREATE POLICY tenant_branch_isolation ON vet.inventory_transaction_lines
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (
        SELECT 1 FROM vet.inventory_transactions tx
        WHERE tx.organization_id = inventory_transaction_lines.organization_id
          AND tx.id = inventory_transaction_lines.transaction_id
    )
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (
        SELECT 1 FROM vet.inventory_transactions tx
        WHERE tx.organization_id = inventory_transaction_lines.organization_id
          AND tx.id = inventory_transaction_lines.transaction_id
    )
);

DROP POLICY IF EXISTS tenant_isolation ON vet.purchase_order_lines;
CREATE POLICY tenant_branch_isolation ON vet.purchase_order_lines
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.purchase_orders po
                WHERE po.organization_id = purchase_order_lines.organization_id AND po.id = purchase_order_lines.purchase_order_id)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.purchase_orders po
                WHERE po.organization_id = purchase_order_lines.organization_id AND po.id = purchase_order_lines.purchase_order_id)
);

DROP POLICY IF EXISTS tenant_isolation ON vet.appointment_status_history;
CREATE POLICY tenant_branch_isolation ON vet.appointment_status_history
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.appointments a
                WHERE a.organization_id = appointment_status_history.organization_id AND a.id = appointment_status_history.appointment_id)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.appointments a
                WHERE a.organization_id = appointment_status_history.organization_id AND a.id = appointment_status_history.appointment_id)
);

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['encounter_vitals','encounter_complaints','encounter_findings','encounter_diagnoses','prescriptions'] LOOP
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON vet.%I', t);
        EXECUTE format(
            'CREATE POLICY tenant_branch_isolation ON vet.%I USING (organization_id = NULLIF(current_setting(''app.organization_id'', true), '''')::uuid AND EXISTS (SELECT 1 FROM vet.encounters e WHERE e.organization_id = %I.organization_id AND e.id = %I.encounter_id)) WITH CHECK (organization_id = NULLIF(current_setting(''app.organization_id'', true), '''')::uuid AND EXISTS (SELECT 1 FROM vet.encounters e WHERE e.organization_id = %I.organization_id AND e.id = %I.encounter_id))',
            t, t, t, t, t
        );
    END LOOP;
END;
$$;

DROP POLICY IF EXISTS tenant_isolation ON vet.prescription_items;
CREATE POLICY tenant_branch_isolation ON vet.prescription_items
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.prescriptions p WHERE p.organization_id = prescription_items.organization_id AND p.id = prescription_items.prescription_id)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.prescriptions p WHERE p.organization_id = prescription_items.organization_id AND p.id = prescription_items.prescription_id)
);

DROP POLICY IF EXISTS tenant_isolation ON vet.medication_administrations;
CREATE POLICY tenant_branch_isolation ON vet.medication_administrations
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND encounter_id IS NOT NULL
    AND EXISTS (SELECT 1 FROM vet.encounters e WHERE e.organization_id = medication_administrations.organization_id AND e.id = medication_administrations.encounter_id)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND encounter_id IS NOT NULL
    AND EXISTS (SELECT 1 FROM vet.encounters e WHERE e.organization_id = medication_administrations.organization_id AND e.id = medication_administrations.encounter_id)
);

DROP POLICY IF EXISTS tenant_isolation ON vet.vaccine_records;
CREATE POLICY tenant_branch_isolation ON vet.vaccine_records
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND encounter_id IS NOT NULL
    AND EXISTS (SELECT 1 FROM vet.encounters e WHERE e.organization_id = vaccine_records.organization_id AND e.id = vaccine_records.encounter_id)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND encounter_id IS NOT NULL
    AND EXISTS (SELECT 1 FROM vet.encounters e WHERE e.organization_id = vaccine_records.organization_id AND e.id = vaccine_records.encounter_id)
);

DROP POLICY IF EXISTS tenant_isolation ON vet.lab_order_items;
CREATE POLICY tenant_branch_isolation ON vet.lab_order_items
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.lab_orders lo WHERE lo.organization_id = lab_order_items.organization_id AND lo.id = lab_order_items.lab_order_id)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.lab_orders lo WHERE lo.organization_id = lab_order_items.organization_id AND lo.id = lab_order_items.lab_order_id)
);

DROP POLICY IF EXISTS tenant_isolation ON vet.lab_samples;
CREATE POLICY tenant_branch_isolation ON vet.lab_samples
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (
        SELECT 1 FROM vet.lab_order_items li JOIN vet.lab_orders lo
          ON lo.organization_id = li.organization_id AND lo.id = li.lab_order_id
        WHERE li.organization_id = lab_samples.organization_id AND li.id = lab_samples.lab_order_item_id
    )
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (
        SELECT 1 FROM vet.lab_order_items li JOIN vet.lab_orders lo
          ON lo.organization_id = li.organization_id AND lo.id = li.lab_order_id
        WHERE li.organization_id = lab_samples.organization_id AND li.id = lab_samples.lab_order_item_id
    )
);

DROP POLICY IF EXISTS tenant_isolation ON vet.lab_results;
CREATE POLICY tenant_branch_isolation ON vet.lab_results
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (
        SELECT 1 FROM vet.lab_order_items li JOIN vet.lab_orders lo
          ON lo.organization_id = li.organization_id AND lo.id = li.lab_order_id
        WHERE li.organization_id = lab_results.organization_id AND li.id = lab_results.lab_order_item_id
    )
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (
        SELECT 1 FROM vet.lab_order_items li JOIN vet.lab_orders lo
          ON lo.organization_id = li.organization_id AND lo.id = li.lab_order_id
        WHERE li.organization_id = lab_results.organization_id AND li.id = lab_results.lab_order_item_id
    )
);

DROP POLICY IF EXISTS tenant_isolation ON vet.imaging_studies;
CREATE POLICY tenant_branch_isolation ON vet.imaging_studies
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.imaging_orders io WHERE io.organization_id = imaging_studies.organization_id AND io.id = imaging_studies.imaging_order_id)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.imaging_orders io WHERE io.organization_id = imaging_studies.organization_id AND io.id = imaging_studies.imaging_order_id)
);

DROP POLICY IF EXISTS tenant_isolation ON vet.imaging_reports;
CREATE POLICY tenant_branch_isolation ON vet.imaging_reports
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (
        SELECT 1 FROM vet.imaging_studies st JOIN vet.imaging_orders io
          ON io.organization_id = st.organization_id AND io.id = st.imaging_order_id
        WHERE st.organization_id = imaging_reports.organization_id AND st.id = imaging_reports.imaging_study_id
    )
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (
        SELECT 1 FROM vet.imaging_studies st JOIN vet.imaging_orders io
          ON io.organization_id = st.organization_id AND io.id = st.imaging_order_id
        WHERE st.organization_id = imaging_reports.organization_id AND st.id = imaging_reports.imaging_study_id
    )
);

DROP POLICY IF EXISTS tenant_isolation ON vet.imaging_files;
CREATE POLICY tenant_branch_isolation ON vet.imaging_files
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (
        SELECT 1 FROM vet.imaging_studies st JOIN vet.imaging_orders io
          ON io.organization_id = st.organization_id AND io.id = st.imaging_order_id
        WHERE st.organization_id = imaging_files.organization_id AND st.id = imaging_files.imaging_study_id
    )
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (
        SELECT 1 FROM vet.imaging_studies st JOIN vet.imaging_orders io
          ON io.organization_id = st.organization_id AND io.id = st.imaging_order_id
        WHERE st.organization_id = imaging_files.organization_id AND st.id = imaging_files.imaging_study_id
    )
);

DROP POLICY IF EXISTS tenant_isolation ON vet.invoice_lines;
CREATE POLICY tenant_branch_isolation ON vet.invoice_lines
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.invoices i WHERE i.organization_id = invoice_lines.organization_id AND i.id = invoice_lines.invoice_id)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.invoices i WHERE i.organization_id = invoice_lines.organization_id AND i.id = invoice_lines.invoice_id)
);

DROP POLICY IF EXISTS tenant_isolation ON vet.invoice_payments;
CREATE POLICY tenant_branch_isolation ON vet.invoice_payments
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.invoices i WHERE i.organization_id = invoice_payments.organization_id AND i.id = invoice_payments.invoice_id)
    AND EXISTS (SELECT 1 FROM vet.payments p WHERE p.organization_id = invoice_payments.organization_id AND p.id = invoice_payments.payment_id)
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.invoices i WHERE i.organization_id = invoice_payments.organization_id AND i.id = invoice_payments.invoice_id)
    AND EXISTS (SELECT 1 FROM vet.payments p WHERE p.organization_id = invoice_payments.organization_id AND p.id = invoice_payments.payment_id)
);


DROP POLICY IF EXISTS tenant_isolation ON vet.consents;
CREATE POLICY tenant_branch_isolation ON vet.consents
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.clients c WHERE c.organization_id = consents.organization_id AND c.id = consents.client_id)
    AND (animal_id IS NULL OR EXISTS (SELECT 1 FROM vet.animals a WHERE a.organization_id = consents.organization_id AND a.id = consents.animal_id))
    AND (encounter_id IS NULL OR EXISTS (SELECT 1 FROM vet.encounters e WHERE e.organization_id = consents.organization_id AND e.id = consents.encounter_id))
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND EXISTS (SELECT 1 FROM vet.clients c WHERE c.organization_id = consents.organization_id AND c.id = consents.client_id)
    AND (animal_id IS NULL OR EXISTS (SELECT 1 FROM vet.animals a WHERE a.organization_id = consents.organization_id AND a.id = consents.animal_id))
    AND (encounter_id IS NULL OR EXISTS (SELECT 1 FROM vet.encounters e WHERE e.organization_id = consents.organization_id AND e.id = consents.encounter_id))
);

-- Klinikle ilişkilendirilmiş belgeler, ilgili şubeye/ziyarete erişilebiliyorsa açılır.
DROP POLICY IF EXISTS tenant_isolation ON vet.clinical_documents;
CREATE POLICY tenant_branch_isolation ON vet.clinical_documents
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND (animal_id IS NULL OR EXISTS (
        SELECT 1 FROM vet.animals a WHERE a.organization_id = clinical_documents.organization_id AND a.id = clinical_documents.animal_id
    ))
    AND (client_id IS NULL OR EXISTS (
        SELECT 1 FROM vet.clients c WHERE c.organization_id = clinical_documents.organization_id AND c.id = clinical_documents.client_id
    ))
    AND (encounter_id IS NULL OR EXISTS (
        SELECT 1 FROM vet.encounters e WHERE e.organization_id = clinical_documents.organization_id AND e.id = clinical_documents.encounter_id
    ))
    AND (
        (clinic_id IS NOT NULL AND vet.current_user_has_clinic_access(organization_id, clinic_id))
        OR (clinic_id IS NULL AND encounter_id IS NOT NULL AND EXISTS (
            SELECT 1 FROM vet.encounters e WHERE e.organization_id = clinical_documents.organization_id AND e.id = clinical_documents.encounter_id
        ))
        OR (clinic_id IS NULL AND encounter_id IS NULL AND animal_id IS NOT NULL AND EXISTS (
            SELECT 1 FROM vet.animals a WHERE a.organization_id = clinical_documents.organization_id AND a.id = clinical_documents.animal_id
        ))
        OR (clinic_id IS NULL AND encounter_id IS NULL AND animal_id IS NULL AND client_id IS NOT NULL AND EXISTS (
            SELECT 1 FROM vet.clients c WHERE c.organization_id = clinical_documents.organization_id AND c.id = clinical_documents.client_id
        ))
    )
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND (animal_id IS NULL OR EXISTS (
        SELECT 1 FROM vet.animals a WHERE a.organization_id = clinical_documents.organization_id AND a.id = clinical_documents.animal_id
    ))
    AND (client_id IS NULL OR EXISTS (
        SELECT 1 FROM vet.clients c WHERE c.organization_id = clinical_documents.organization_id AND c.id = clinical_documents.client_id
    ))
    AND (encounter_id IS NULL OR EXISTS (
        SELECT 1 FROM vet.encounters e WHERE e.organization_id = clinical_documents.organization_id AND e.id = clinical_documents.encounter_id
    ))
    AND (
        (clinic_id IS NOT NULL AND vet.current_user_has_clinic_access(organization_id, clinic_id))
        OR (clinic_id IS NULL AND encounter_id IS NOT NULL AND EXISTS (
            SELECT 1 FROM vet.encounters e WHERE e.organization_id = clinical_documents.organization_id AND e.id = clinical_documents.encounter_id
        ))
        OR (clinic_id IS NULL AND encounter_id IS NULL AND animal_id IS NOT NULL AND EXISTS (
            SELECT 1 FROM vet.animals a WHERE a.organization_id = clinical_documents.organization_id AND a.id = clinical_documents.animal_id
        ))
        OR (clinic_id IS NULL AND encounter_id IS NULL AND animal_id IS NULL AND client_id IS NOT NULL AND EXISTS (
            SELECT 1 FROM vet.clients c WHERE c.organization_id = clinical_documents.organization_id AND c.id = clinical_documents.client_id
        ))
    )
);


DROP POLICY IF EXISTS tenant_isolation ON vet.notification_outbox;
CREATE POLICY tenant_branch_isolation ON vet.notification_outbox
USING (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND (
        (appointment_id IS NOT NULL AND EXISTS (SELECT 1 FROM vet.appointments a WHERE a.organization_id = notification_outbox.organization_id AND a.id = notification_outbox.appointment_id))
        OR (animal_id IS NOT NULL AND EXISTS (SELECT 1 FROM vet.animals a WHERE a.organization_id = notification_outbox.organization_id AND a.id = notification_outbox.animal_id))
        OR (client_id IS NOT NULL AND EXISTS (SELECT 1 FROM vet.clients c WHERE c.organization_id = notification_outbox.organization_id AND c.id = notification_outbox.client_id))
        OR (care_event_id IS NOT NULL AND EXISTS (SELECT 1 FROM vet.animal_care_schedule_events e WHERE e.organization_id = notification_outbox.organization_id AND e.id = notification_outbox.care_event_id))
    )
)
WITH CHECK (
    organization_id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND (
        (appointment_id IS NOT NULL AND EXISTS (SELECT 1 FROM vet.appointments a WHERE a.organization_id = notification_outbox.organization_id AND a.id = notification_outbox.appointment_id))
        OR (animal_id IS NOT NULL AND EXISTS (SELECT 1 FROM vet.animals a WHERE a.organization_id = notification_outbox.organization_id AND a.id = notification_outbox.animal_id))
        OR (client_id IS NOT NULL AND EXISTS (SELECT 1 FROM vet.clients c WHERE c.organization_id = notification_outbox.organization_id AND c.id = notification_outbox.client_id))
        OR (care_event_id IS NOT NULL AND EXISTS (SELECT 1 FROM vet.animal_care_schedule_events e WHERE e.organization_id = notification_outbox.organization_id AND e.id = notification_outbox.care_event_id))
    )
);

-- ---------------------------------------------------------------------------
-- 13. Başlangıç referans verileri (asgari tür kataloğu ve izinler)
-- Organizasyona özel roller ve klinik verileri, uygulamanın kurulum akışıyla eklenir.
-- ---------------------------------------------------------------------------
INSERT INTO vet.species (code, name, scientific_name) VALUES
    ('DOG', 'Köpek', 'Canis lupus familiaris'),
    ('CAT', 'Kedi', 'Felis catus'),
    ('RABBIT', 'Tavşan', 'Oryctolagus cuniculus'),
    ('BIRD', 'Kuş', NULL),
    ('RODENT', 'Kemirgen', NULL),
    ('REPTILE', 'Sürüngen', NULL),
    ('OTHER', 'Diğer', NULL)
ON CONFLICT (code) DO NOTHING;

INSERT INTO vet.permissions (code, description) VALUES
    ('clients.read', 'Müşteri ve hayvan kayıtlarını görüntüleme'),
    ('clients.write', 'Müşteri ve hayvan kayıtlarını oluşturma/güncelleme'),
    ('appointments.manage', 'Randevuları yönetme'),
    ('clinical.read', 'Klinik kayıtları görüntüleme'),
    ('clinical.write', 'Muayene ve tedavi kayıtlarını yönetme'),
    ('diagnostics.manage', 'Laboratuvar ve radyoloji işlemlerini yönetme'),
    ('inventory.read', 'Stok durumunu görüntüleme'),
    ('inventory.write', 'Stok hareketlerini yönetme'),
    ('billing.manage', 'Fatura ve tahsilat yönetimi'),
    ('users.manage', 'Kullanıcı ve rol yönetimi'),
    ('reports.read', 'Raporları görüntüleme'),
    ('audit.read', 'Denetim kayıtlarını görüntüleme'),
    ('clinic_roles.manage', 'Şube bazlı kullanıcı rol atamalarını yönetme'),
    ('vaccine_protocols.manage', 'Klinik onaylı aşı protokollerini yönetme'),
    ('subscriptions.read', 'Abonelik durumunu görüntüleme'),
    ('commissions.read', 'Komisyon kayıtlarını görüntüleme')
ON CONFLICT (code) DO NOTHING;

COMMIT;

-- KURULUM ŞEMASI SONU
