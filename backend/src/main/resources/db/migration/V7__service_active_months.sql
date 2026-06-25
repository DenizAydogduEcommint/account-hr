-- V7__service_active_months.sql
-- E2-02 Servisler master importer için "Aktif Aylar" denormalize alanı.
-- active_months: Servisler sheet'indeki "Aktif Aylar" kolonunun virgüllü string'i
-- (ör. "2026-01, 2026-02, 2026-03") aynen saklanır. Bilgi amaçlıdır; modelde
-- service↔period ilişkisi YOKTUR, bu yüzden verbatim metin olarak tutulur.
-- Nullable: yalnızca master sheet'ten gelen servisler doldurur.

ALTER TABLE services ADD COLUMN active_months VARCHAR(255);
