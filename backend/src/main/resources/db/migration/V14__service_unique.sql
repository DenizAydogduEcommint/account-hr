-- V14__service_unique.sql
-- E1-DR-2 — services (name, provider_id) DB-düzeyi tekil kısıt.
-- Importer'lar (ExcelImportService / ServiceMasterImportService) bellek-içi dedup
-- yapar; bu kısıt eşzamanlı import yarışına karşı DB backstop'tur.
--
-- ÖN KONTROL (manuel çalıştırılabilir, migrasyon temiz DB varsayar):
--   SELECT name, provider_id, count(*) FROM services
--   GROUP BY name, provider_id HAVING count(*) > 1;
-- Eğer canlı DB'de mükerrer (name, provider_id) varsa AŞAĞIDAKİ ADD CONSTRAINT
-- BAŞARISIZ olur. DB temiz seed'lendiği için bu migrasyon temiz DB'de başarılı olur.
-- Mükerrer çıkarsa önce elle temizlenmeli (duplicate satırları birleştir/sil), sonra
-- bu migrasyon tekrar denenmeli.

ALTER TABLE services
    ADD CONSTRAINT uq_services_name_provider UNIQUE (name, provider_id);
