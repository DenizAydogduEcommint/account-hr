-- V6__expenses_source_row_hash.sql
-- E2-01 Excel ay-sheet importer için idempotency desteği.
-- source_row_hash: importer'ın bir Excel satırından ürettiği stabil SHA-256.
-- Tekrar çalıştırmada aynı hash'e sahip satır yeniden eklenmez (skipped-duplicate).
-- Nullable: yalnızca import edilen kayıtlar doldurur; elle/diğer yollarla oluşan
-- expense'ler null kalabilir.

ALTER TABLE expenses ADD COLUMN source_row_hash VARCHAR(64);

CREATE INDEX idx_expenses_source_row_hash ON expenses (source_row_hash);

-- amount (orijinal döviz tutarı) artık NULLABLE: TL ödemelerinde Tutar boş kalıp
-- yalnızca amount_try dolu olabilir (E2-01). V1'deki NOT NULL kısıtı kaldırılır.
ALTER TABLE expenses ALTER COLUMN amount DROP NOT NULL;

-- transaction_date artık NULLABLE: "Bekleniyor" satırlarında (Nisan) tarih boş
-- olabilir (E2-01). V1'deki NOT NULL kısıtı kaldırılır.
ALTER TABLE expenses ALTER COLUMN transaction_date DROP NOT NULL;
