-- V9__files_sha256_unique.sql
-- E2-03 idempotency sertleştirme: files.sha256 üzerinde KISMİ TEKİL index.
-- Migrasyon importer'ı (InvoiceFileImportService) uygulama düzeyinde existsBySha256 ile
-- duplicate'i engeller; ancak iki eşzamanlı import isteği (ör. çift tıklama) aynı SHA-256'yı
-- ikisi de commit etmeden okuyabilir → çift files satırı. Bu kısmi tekil index yarışı
-- DB düzeyinde kapatır. sha256 NULL olabilir (ör. tipi belirsiz eski kayıt) → yalnızca
-- dolu olanlar tekil.
--
-- NOT: Bu dosya yalnızca PostgreSQL üzerinde (Flyway etkinken) çalışır.
-- Testlerde Flyway kapalıdır; şema entity'lerden Hibernate ile üretilir; idempotency
-- testte uygulama düzeyi existsBySha256 ile doğrulanır.

-- Önceki (tekil olmayan) index'i bırak; partial unique onun yerine geçer.
DROP INDEX IF EXISTS idx_files_sha256;

CREATE UNIQUE INDEX uq_files_sha256 ON files (sha256) WHERE sha256 IS NOT NULL;
