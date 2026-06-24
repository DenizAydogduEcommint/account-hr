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
- [ ] Profil bazlı config (local/staging/prod) çalışır; ortam değişkenleri ile override edilebilir
- [ ] Secret'lar repoda değil: `.env`/ortam değişkeni veya secret manager üzerinden okunuyor
- [ ] Servis panel credential'ları DB'de **şifreli** saklanıyor (uygulama seviyesi şifreleme, master key dışarıdan)
- [ ] Yapılandırılmış (JSON veya structured) loglama; log seviyeleri ortama göre ayarlanabilir
- [ ] `audit_log` otomatik dolduruluyor: kritik mutasyonlar (fatura durumu değişimi, dosya taşıma/silme, servis düzenleme) kayıt altında — kim, ne zaman, eski→yeni değer
- [ ] Hassas veriler loglara düşmüyor (parola, token maskelenmiş)

## Alt Görevler
- [ ] Config profilleri ve `@ConfigurationProperties` sınıfları
- [ ] Secret yönetimi kararı + implementasyon (env / Vault / cloud secret manager)
- [ ] Credential şifreleme servisi (AES-GCM, master key env'den)
- [ ] Structured logging (logback JSON encoder) + correlation/trace id
- [ ] Audit altyapısı: JPA EntityListener veya AOP ile mutasyon yakalama → `audit_log`
- [ ] Hassas alan maskeleme (log filter)

## Teknik Notlar
- Credential şifreleme: master key prod'da secret manager'dan, local'de env'den
- Audit için JPA `@EntityListeners` + `@CreatedBy/@LastModifiedBy` (Spring Data Auditing) kombinasyonu pratik
- Panel login bilgileri gelecekteki "otomatik indirme" epic'i için temel; modeli şimdiden esnek tut (servis başına çok credential olabilir)
- Loglar staging/prod'da merkezi toplanabilir (faz 2: ELK/Loki) — MVP'de dosya/stdout yeter

## Açık Sorular / Riskler
- Secret manager seçimi deploy ortamına bağlı (kendi sunucu mu, bulut mu?) — E1-06 ile birlikte karara bağlanmalı
- Panel parolalarını saklamak yasal/güvenlik açısından kullanıcı onayı gerektirir; KVKK/erişim politikası netleştirilmeli
- Audit ne kadar geriye/detaylı tutulacak? (saklama süresi)
