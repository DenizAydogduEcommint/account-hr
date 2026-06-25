# [E1-05] Yapılandırma, secret/credential yönetimi, loglama ve audit altyapısı

| Alan | Değer |
|------|-------|
| Epic | E1 — Temel Altyapı & Veri Modeli |
| Sprint | Sprint 2 |
| Öncelik | Yüksek |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E1-02 |
| Etiketler | backend, security, config, audit |

## Amaç
Ortam yapılandırmasını düzenli hale getirmek, hassas bilgileri (servis panel login bilgileri, JWT secret, mail kimlik bilgileri, Drive token) güvenli saklamak, yapılandırılmış loglama ve değişiklik denetimi (audit) altyapısı kurmak.

## Açıklama / Bağlam
Sistem ileride servis panellerine otomatik girip fatura indirecek (E-epic). Bunun için **panel login bilgileri (kullanıcı adı/parola/API key)** güvenli saklanmalı — düz metin asla. Ayrıca JWT secret, mail SMTP/IMAP bilgileri, Google Drive token gibi sırlar var. Bunları kodda/repoda tutamayız.

Audit ihtiyacı: Muhasebe ve denetim açısından "hangi fatura durumu ne zaman kim tarafından değişti", "dosya kim tarafından silindi/taşındı" izlenebilir olmalı (E1-02'deki `audit_log` tablosu burada doldurulur).

## Kabul Kriterleri (DOD)
- [x] Profil bazlı config (local/staging/prod) + env override; `@ConfigurationProperties` (JwtProperties, CredentialEncryptionProperties)
- [x] Secret'lar repoda değil: env/`.env`'den; prod master-key default boş → set edilmezse fail-fast
- [x] Servis panel credential'ları DB'de **şifreli** (AES-256-GCM, master key env'den, `ServiceCredential.secret` AttributeConverter ile at-rest şifreli — testle kanıtlandı)
- [x] Structured logging: prod/staging native JSON (ECS), local okunabilir; correlation id (MDC, X-Request-Id)
- [x] `audit_log` otomatik dolu: Hibernate Interceptor → Invoice status (STATUS_CHANGE), FileAsset, Service mutasyonları; kim (AuditorAware), ne zaman, eski→yeni
- [x] Hassas veriler loglara/audit'e düşmüyor (parola/token/secret maskeli/atlanır — testle doğrulandı)

## Alt Görevler
- [x] Config profilleri + `@ConfigurationProperties` sınıfları
- [x] Secret yönetimi: env tabanlı (MVP); Vault/cloud manager için temiz soyutlama bırakıldı (E1-06)
- [x] Credential şifreleme servisi (AES-256-GCM, rastgele IV, master key env'den, fail-fast)
- [x] Structured logging (Spring Boot native JSON) + correlation id
- [x] Audit altyapısı: Hibernate Interceptor (onFlushDirty old→new) → `audit_log`
- [x] Hassas alan maskeleme (LogMasker + audit SENSITIVE_FIELDS)

## Teknik Notlar
- Credential şifreleme: master key prod'da secret manager'dan, local'de env'den
- Audit için JPA `@EntityListeners` + `@CreatedBy/@LastModifiedBy` (Spring Data Auditing) kombinasyonu pratik
- Panel login bilgileri gelecekteki "otomatik indirme" epic'i için temel; modeli şimdiden esnek tut (servis başına çok credential olabilir)
- Loglar staging/prod'da merkezi toplanabilir (faz 2: ELK/Loki) — MVP'de dosya/stdout yeter

## Açık Sorular / Riskler — KISMEN KARARA BAĞLANDI
- Secret manager seçimi → MVP'de **env tabanlı**; Vault/cloud manager için soyutlama bırakıldı, deploy ortamı belli olunca (E1-06) eklenecek.
- Panel parolaları saklama (KVKK) → Bu görevde sadece **şifreli saklama altyapısı** kuruldu; gerçek panel parolaları E5'te, erişim/onay politikasıyla girilecek. AÇIK.
- Audit saklama süresi → şimdilik sınırsız; arşiv/temizlik politikası sonraya. AÇIK.

## Tamamlanma Kaydı
- Durum: Tamamlandı — 2026-06-25
- YouTrack: IK-229 (sıralı varsayım — teyit edilecek)
- Repo: account-hr (backend)
- Üretilenler: JwtProperties + CredentialEncryptionProperties, EncryptionService (AES-256-GCM) + EncryptedStringConverter + ServiceCredential + Flyway V5, structured logging (native JSON) + CorrelationIdFilter, Hibernate AuditInterceptor + SecurityAuditorAware, LogMasker
- Doğrulananlar: `./mvnw test` 38/38 (15 yeni: encryption round-trip + at-rest şifreli kanıtı + audit STATUS_CHANGE + maskeleme). Lokal PostgreSQL 14'te: V5 uygulandı (success=true), `ddl-auto=validate` geçti, service_credentials tablosu oluştu, health UP
- Güvenlik: hiçbir secret/token loglanmıyor, audit'e hassas alan yazılmıyor; prod master-key set edilmezse fail-fast
- Not: dev master key sadece local/test profilinde; prod default boş
