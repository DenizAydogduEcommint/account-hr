-- V8__files_invoice_nullable.sql
-- E2-03 Migrasyon modu: faturalar/ klasörü taranıp her fizik dosya files tablosuna
-- yazılır. waiting/ ve trash/ altındaki ya da hiçbir invoice'a eşleşmeyen dosyalar
-- "unmatched"/"trashed" olarak invoice_id'siz kaydedilir. Bu yüzden files.invoice_id
-- artık NULLABLE olmalı (E2-03 öncesi NOT NULL idi).
--
-- NOT: Bu dosya yalnızca PostgreSQL üzerinde (Flyway etkinken) çalışır.
-- Testlerde Flyway kapalıdır; şema entity'lerden Hibernate ile üretilir.

ALTER TABLE files ALTER COLUMN invoice_id DROP NOT NULL;
