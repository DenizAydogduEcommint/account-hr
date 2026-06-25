-- V3__auth_schema.sql
-- E1-03 Kimlik doğrulama & yetkilendirme şeması — PostgreSQL hedefli.
-- users tablosuna BCrypt parola hash kolonu, refresh_tokens tablosu (DB'de
-- iptal edilebilir, hash'lenmiş refresh token saklama) ve ilk admin seed'i.
--
-- NOT: Bu dosya yalnızca PostgreSQL üzerinde (Flyway etkinken) çalışır.
-- Testlerde Flyway kapalıdır; şema entity'lerden Hibernate ile üretilir.

-- ---------------------------------------------------------------------------
-- users: BCrypt parola hash kolonu
-- ---------------------------------------------------------------------------
ALTER TABLE users ADD COLUMN password_hash VARCHAR(100);

-- ---------------------------------------------------------------------------
-- refresh_tokens: stateless JWT mimarisinde iptal edilebilir refresh token'lar.
-- token_hash = refresh token'ın SHA-256 hex digesti (asla plaintext saklanmaz).
-- ---------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- ---------------------------------------------------------------------------
-- İlk admin kullanıcısı.
-- Varsayılan parola: changeme123  (BCrypt cost 10 ile hash'lendi)
-- → İlk girişten sonra MUTLAKA değiştirilmeli.
-- Hash spring-security-crypto BCryptPasswordEncoder ile üretilip doğrulandı.
-- ---------------------------------------------------------------------------
INSERT INTO users (email, full_name, role, active, password_hash, created_at, updated_at)
VALUES (
    'admin@e-commint.com',
    'Yönetici',
    'ADMIN',
    TRUE,
    '$2a$10$pejA6bfmYDWAHK/HiAYjp.dg42oW5VD93kpL36xs47fIILnrK8/Iu',
    NOW(),
    NOW()
)
ON CONFLICT (email) DO NOTHING;
