-- V19__raw_transactions.sql
-- E4-01 — Ham işlem (raw transaction) tablosu. Kart ekstresinden / dönem-içi hareket
-- dökümünden parse edilen ama HENÜZ servise/expense'e eşleşmemiş satırlar burada tutulur.
-- Eşleştirme (matching) + expense üretimi E4-02'nin işidir; bu tabloda satırlar
-- matched=false olarak başlar.
--
-- Akış: upload → parse → PENDING satırlar yazılır → kullanıcı önizler → confirm
-- (CONFIRMED) / discard (DISCARDED). source_file_sha256 = batch anahtarı + idempotency:
-- aynı (sha256 + kart + dönem) için CONFIRMED bir batch varsa yeniden parse edilmez.
--
-- ddl-auto=validate uyumlu (entity RawTransaction ile birebir):
--   amount / amount_try  NUMERIC(15,2)  ↔ precision 15, scale 2
--   currency / status     VARCHAR        ↔ @Enumerated(STRING)
--   source_file_sha256    VARCHAR(64)    NOT NULL
--   matched               BOOLEAN        NOT NULL DEFAULT FALSE
--   version               BIGINT         NOT NULL DEFAULT 0  (BaseEntity @Version)

CREATE TABLE raw_transactions (
    id                  BIGSERIAL PRIMARY KEY,
    card_id             BIGINT REFERENCES cards (id),
    period_id           BIGINT REFERENCES periods (id),
    transaction_date    DATE,
    description         TEXT,
    amount              NUMERIC(15, 2),
    currency            VARCHAR(8) NOT NULL,
    amount_try          NUMERIC(15, 2),
    status              VARCHAR(16) NOT NULL,
    source_file_sha256  VARCHAR(64) NOT NULL,
    source_file_name    VARCHAR(255),
    raw_text            TEXT,
    parse_warning       TEXT,
    matched             BOOLEAN NOT NULL DEFAULT FALSE,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL
);

CREATE INDEX ix_raw_txn_sha256 ON raw_transactions (source_file_sha256);
CREATE INDEX ix_raw_txn_period ON raw_transactions (period_id);
CREATE INDEX ix_raw_txn_status ON raw_transactions (status);
