-- V2__seed_reference_data.sql
-- Çekirdek referans verisi: 3 kart + 2026-01..2026-06 dönemleri.
-- Idempotent: ON CONFLICT DO NOTHING (unique kısıtlar üzerinden).

-- Kartlar (CLAUDE.md): Akbank Axess ****3800, YKB Ticari ****3909, Ziraat ****9164.
INSERT INTO cards (bank, last_four, holder_name, label, active, created_at, updated_at) VALUES
    ('Akbank',  '3800', 'Kaan Bingöl', 'Akbank Axess',              TRUE, NOW(), NOW()),
    ('YKB',     '3909', 'Kaan Bingöl', 'YKB Ticari',                TRUE, NOW(), NOW()),
    ('Ziraat',  '9164', 'Kaan Bingöl', 'Ziraat Bankkart (ek kart)', TRUE, NOW(), NOW())
ON CONFLICT (last_four) DO NOTHING;

-- İlk dönemler: 2026-01 .. 2026-06.
INSERT INTO periods (period_year, period_month, code, created_at, updated_at) VALUES
    (2026, 1, '2026-01', NOW(), NOW()),
    (2026, 2, '2026-02', NOW(), NOW()),
    (2026, 3, '2026-03', NOW(), NOW()),
    (2026, 4, '2026-04', NOW(), NOW()),
    (2026, 5, '2026-05', NOW(), NOW()),
    (2026, 6, '2026-06', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;
