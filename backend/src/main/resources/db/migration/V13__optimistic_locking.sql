-- V13__optimistic_locking.sql
-- E1-DR-1 — Optimistic locking (@Version).
-- BaseEntity'ye @Version eklendi (id/created_at/updated_at taşıyan tüm mutable
-- entity'ler bu kolonu kazanır). Ayrıca BaseEntity'yi GENİŞLETMEYEN ama mutable
-- olan refresh_tokens (revoked rotasyonu) ve files'a da @Version eklendi.
--
-- Her tabloya version BIGINT NOT NULL DEFAULT 0 — mevcut satırlar 0'a düşer; JPA
-- her UPDATE'te artırır. Eskimiş (detached) kopya UPDATE'i OptimisticLockException
-- (→ ObjectOptimisticLockingFailureException) ile reddedilir.
--
-- ddl-auto=validate: Long ↔ BIGINT eşleşir; nullable=false ↔ NOT NULL eşleşir.

ALTER TABLE providers          ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE cards              ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE teams              ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users              ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE periods            ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE services           ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE service_contacts   ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE service_credentials ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE expenses           ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE invoices           ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE refresh_tokens     ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE files              ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
