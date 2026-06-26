-- V16__teams_name_lower_unique.sql
-- teams.name büyük/küçük harf DUYARSIZ UNIQUE (providers V11 / services V14 ile tutarlılık).
--
-- Sorun: uq_teams_name (V1) case-SENSITIVE'di + Team.@Column(unique=true); ancak importer'lar
-- findByNameIgnoreCase ile dedup yapıyor → TOCTOU yarışı / case-variant duplicate ("DevOps" ve
-- "devops" ikisi de eklenebilir) mümkündü. Case-sensitive tekili düşürüp lower(name) üzerinde
-- fonksiyonel bir unique index ile değiştiririz (providers V11 deseninin aynısı).
--
-- NOT: Önceden case-variant duplicate satırlar varsa bu index OLUŞTURULAMAZ (migration fail
-- eder). DB temiz re-seed edildiğinden sorun beklenmez; çıkarsa önce elle temizlenmeli.
--
-- Bu dosya yalnızca PostgreSQL üzerinde (Flyway etkinken) çalışır. Testlerde Flyway kapalıdır;
-- şema entity'lerden Hibernate ile üretilir (Team @Column nullable=false).

ALTER TABLE teams DROP CONSTRAINT uq_teams_name;
CREATE UNIQUE INDEX uq_teams_name_lower ON teams (lower(name));
