-- V12__expense_source.sql
-- E3-06 — Elle harcama satırı girişi.
-- source: bir expense satırının kaynağını ayırt eder (STATEMENT = ekstre/Excel importer,
-- MANUAL = kullanıcının ekstreden önce/olmadan elle girdiği satır).
-- Mevcut tüm satırlar STATEMENT'tır (importer'dan geldi) → NOT NULL DEFAULT 'STATEMENT'.
ALTER TABLE expenses ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'STATEMENT';
