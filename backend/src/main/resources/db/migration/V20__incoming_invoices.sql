-- V20__incoming_invoices.sql
-- E5-02 — "Ham fatura" (incoming invoice) tablosu. Drive faturalar/waiting/ (ileride mail)
-- kaynağından TOPLANMIŞ ama HENÜZ bir ekstre satırına / expense'e EŞLEŞMEMİŞ ham fatura
-- dosyalarının kaydı. Eşleştirme (matching) E5-04'ün işidir; bu tabloda satırlar NEW olarak
-- başlar.
--
-- Bu round YALNIZCA PULL + INGEST: Drive waiting/ → lokal storage'a KOPYA (rclone copy),
-- her dosya için bir ham fatura satırı. Drive'a ASLA push/delete yok; ay klasörüne taşıma /
-- waiting silme E5-04'e ertelendi.
--
-- Idempotency: aynı (source, source_ref) için TEK satır (unique index). Ek olarak servis
-- katmanı sha256 ile de mükerrer tespiti yapar (aynı içerik farklı dosya adıyla yeniden
-- inse bile yeni satır yaratılmaz).
--
-- ddl-auto=validate uyumlu (entity IncomingInvoice ile birebir):
--   source / status   VARCHAR        ↔ @Enumerated(STRING)
--   source_ref        VARCHAR(512)   NOT NULL  (idempotency anahtarı: drive göreli yolu / mail messageId)
--   file_name         VARCHAR(512)   NOT NULL
--   stored_path       VARCHAR(1024)  NOT NULL  (STORAGE_ROOT'a göreli; pull'lanan kopyanın yeri)
--   sha256            VARCHAR(64)    NOT NULL
--   received_at       TIMESTAMP      NOT NULL
--   notes             TEXT           nullable
--   version           BIGINT         NOT NULL DEFAULT 0  (BaseEntity @Version)

CREATE TABLE incoming_invoices (
    id           BIGSERIAL PRIMARY KEY,
    source       VARCHAR(32) NOT NULL,
    source_ref   VARCHAR(512) NOT NULL,
    file_name    VARCHAR(512) NOT NULL,
    stored_path  VARCHAR(1024) NOT NULL,
    sha256       VARCHAR(64) NOT NULL,
    received_at  TIMESTAMP NOT NULL,
    status       VARCHAR(16) NOT NULL,
    notes        TEXT,
    version      BIGINT NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP NOT NULL
);

-- Idempotency: aynı kaynak + kaynak referansı için tek satır.
CREATE UNIQUE INDEX ux_incoming_source_ref ON incoming_invoices (source, source_ref);
-- Durum filtreli listeleme için.
CREATE INDEX ix_incoming_status ON incoming_invoices (status);
-- sha256 ile içerik-bazlı mükerrer tespiti (servis katmanı sorgular).
CREATE INDEX ix_incoming_sha256 ON incoming_invoices (sha256);
