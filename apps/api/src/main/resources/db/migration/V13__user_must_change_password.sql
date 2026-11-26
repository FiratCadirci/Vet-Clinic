-- Yönetici tarafından oluşturulan kullanıcı ilk girişte şifresini değiştirmek zorundadır.
ALTER TABLE vet.app_users
    ADD COLUMN IF NOT EXISTS must_change_password boolean NOT NULL DEFAULT false;
