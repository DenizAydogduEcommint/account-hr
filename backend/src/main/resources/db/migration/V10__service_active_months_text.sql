-- V10__service_active_months_text.sql
-- E2-02 düzeltme: active_months VARCHAR(255) overflow riski.
-- "Aktif Aylar" kolonu virgüllü ay listesidir (ör. "2026-01, 2026-02, ..."). Her ay
-- ~9 karakter (kod + ", "); ~28 aydan sonra (~2028) 255 baytı aşar. PostgreSQL sessiz
-- truncate YAPMAZ, hata fırlatır → import patlar. Bilgi-amaçlı denormalize bir alan
-- olduğundan TEXT'e genişletmek bedelsizdir (uzunluk sınırı kaldırılır).

ALTER TABLE services ALTER COLUMN active_months TYPE TEXT;
