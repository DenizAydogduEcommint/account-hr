-- V11__e1_review_hardening.sql
-- E1 core-infra deep review düzeltmeleri (PostgreSQL).
--
-- 1) FK ON DELETE SET NULL (#9): audit_log.changed_by ve files.uploaded_by varsayılan
--    RESTRICT ile users(id)'e bağlıydı → bir kullanıcı silmek bu satırlar yüzünden
--    BAŞARISIZ oluyordu. Her iki kolon da nullable olduğundan FK'leri ON DELETE SET NULL
--    ile yeniden oluştururuz; kullanıcı silindiğinde denetim/dosya kaydı korunur, sahip
--    alanı NULL'a düşer.
--
-- 2) providers.name büyük/küçük harf duyarsız UNIQUE (#15): importer'lar
--    findByNameIgnoreCase ile dedup yapıyor ama uq_providers_name (name) case-SENSITIVE'di
--    → "AWS" ve "Aws" ikisi de eklenebiliyordu. Case-sensitive unique'i düşürüp lower(name)
--    üzerinde fonksiyonel bir unique index ile değiştiririz.
--    NOT: Önceden case-variant duplicate satırlar varsa bu index OLUŞTURULAMAZ (migration
--    fail eder). DB temiz re-seed edildiğinden sorun beklenmez.

-- --- 1) FK'ler: drop + recreate ON DELETE SET NULL ---
ALTER TABLE audit_log DROP CONSTRAINT fk_audit_log_changed_by;
ALTER TABLE audit_log
    ADD CONSTRAINT fk_audit_log_changed_by
    FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE files DROP CONSTRAINT fk_files_uploaded_by;
ALTER TABLE files
    ADD CONSTRAINT fk_files_uploaded_by
    FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE SET NULL;

-- --- 2) providers.name: case-insensitive UNIQUE ---
ALTER TABLE providers DROP CONSTRAINT uq_providers_name;
CREATE UNIQUE INDEX uq_providers_name_lower ON providers (lower(name));
