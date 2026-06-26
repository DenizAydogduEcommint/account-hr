-- V18__invoice_kdv.sql
-- E3-11 — Fatura KDV (VAT) kırılımı. KDV faturaya (belgeye) aittir, harcamaya değil.
--
-- Brüt TL (KDV-dahil) = expense.amount_try. Bir oran (kdv_rate, yüzde) verilirse
-- matrah (net_amount_try) ve KDV tutarı (kdv_amount_try) brütten türetilir:
--   net = gross / (1 + rate/100)
--   kdv = gross - net
-- (her ikisi de scale 2, HALF_UP). İade/refund (negatif brüt) → negatif net/kdv (doğru).
--
-- Hepsi NULLABLE, DEFAULT NULL: oran girilmeyen yüklemeler ve MEVCUT tüm faturalar
-- etkilenmez (üç alan da null kalır). ddl-auto=validate ile uyumlu:
--   kdv_rate        NUMERIC(5,2)  ↔ Invoice.kdvRate      (precision 5,  scale 2)
--   kdv_amount_try  NUMERIC(15,2) ↔ Invoice.kdvAmountTry (precision 15, scale 2)
--   net_amount_try  NUMERIC(15,2) ↔ Invoice.netAmountTry (precision 15, scale 2)

ALTER TABLE invoices
    ADD COLUMN kdv_rate NUMERIC(5,2),
    ADD COLUMN kdv_amount_try NUMERIC(15,2),
    ADD COLUMN net_amount_try NUMERIC(15,2);
