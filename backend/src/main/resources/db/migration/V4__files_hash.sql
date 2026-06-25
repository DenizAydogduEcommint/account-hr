-- V4__files_hash.sql
-- E1-04 Fatura dosya depolama servisi — files tablosuna SHA-256 hash kolonu.
-- Hash hem duplicate tespiti (aynı içerik) hem bütünlük doğrulaması için tutulur.
--
-- NOT: Bu dosya yalnızca PostgreSQL üzerinde (Flyway etkinken) çalışır.
-- Testlerde Flyway kapalıdır; şema entity'lerden Hibernate ile üretilir.

ALTER TABLE files ADD COLUMN sha256 VARCHAR(64);

-- Aynı içeriğe sahip dosyaları hızlıca bulmak için (duplicate tespiti).
CREATE INDEX idx_files_sha256 ON files (sha256);
