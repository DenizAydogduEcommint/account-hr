# [E1-03] Kimlik doğrulama ve yetkilendirme (JWT + Spring Security + roller)

| Alan | Değer |
|------|-------|
| Epic | E1 — Temel Altyapı & Veri Modeli |
| Sprint | Sprint 2 |
| Öncelik | Yüksek |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E1-02 |
| Etiketler | backend, security, auth |

## Amaç
Kullanıcıların güvenli giriş yapmasını ve rollerine göre (yönetici / muhasebe / ekip üyesi) yetkilendirilmesini sağlamak; aynı API'yi web ve ileride mobil kullanacağı için token tabanlı bir mekanizma kurmak.

## Açıklama / Bağlam
account-hr'da farklı yetki seviyeleri var:
- **Yönetici (admin)**: her şeyi görür/düzenler, kullanıcı yönetir, servis master'ını düzenler
- **Muhasebe (Talha + dış mali müşavir)**: faturaları görür, durumları günceller, Paraşüt/mali müşavire iletir, ama altyapı ayarlarını değiştiremez
- **Ekip üyesi**: kendi takımının servislerini/faturalarını görür, eksik fatura talebi geldiğinde yükler

Spring Security + JWT kullanılacak. Mobil de aynı REST API'yi tüketeceği için session değil stateless token mantığı tercih ediliyor.

## Kabul Kriterleri (DOD)
- [x] `POST /api/auth/login` (email + parola) → JWT access + refresh token döner (`expiresIn:900`)
- [x] `POST /api/auth/refresh` çalışır; rotate eder (eski refresh iptal), access yenilenir
- [x] Roller tanımlı: `ROLE_ADMIN`, `ROLE_ACCOUNTING`, `ROLE_TEAM_MEMBER`
- [x] Endpoint'ler role göre korunuyor (`@EnableMethodSecurity` + `@PreAuthorize`); yetkisiz erişim 403 (`/admin-check` testiyle kanıtlandı)
- [x] Parolalar BCrypt ile hashlenmiş saklanıyor
- [x] Angular login akışı + token saklama (localStorage) + korumalı route (authGuard) + interceptor (Bearer + 401'de refresh/logout) — `npm run build` ✅
- [x] Geçersiz/expired token → 401, tutarlı `{error, message}` formatı

## Alt Görevler
- [x] AppUser'a `password_hash` alanı eklendi (Flyway V3)
- [x] Spring Security config (stateless filter chain, JWT filter, BCrypt encoder)
- [x] JwtService (HS256 access JWT + opaque refresh, SHA-256 hash ile DB'de)
- [x] Login/refresh/logout/me endpoint'leri (`AuthController`)
- [x] Rol bazlı erişim (method security + örnek `/admin-check`)
- [x] Angular: login sayfası, auth service, HttpInterceptor
- [x] İlk admin kullanıcısı seed (V3) — `admin@e-commint.com`

## Teknik Notlar
- accounting@e-commint.com kurumsal mail; ileride Google SSO entegrasyonu düşünülebilir ama MVP için email+parola yeterli
- JWT secret ve süreler config/secret yönetiminden gelmeli (E1-05)
- Refresh token DB'de saklanıp iptal edilebilir olmalı (logout / güvenlik)
- account-hr "hr" eki ileride personel modülü ima ediyor olabilir; rol modeli genişletilebilir tasarlanmalı (rol tablosu vs enum kararı)

## Açık Sorular / Riskler — KARARA BAĞLANDI
- ~~Google SSO MVP'ye?~~ → **Hayır, faz 2**. MVP email+parola.
- Dış mali müşavir kullanıcı mı? → **Açık** (auth'u etkilemiyor; gerekirse ROLE_ACCOUNTING ile hesap açılır, aksi halde sadece mail hedefi). E6/E7'de netleşecek.
- ~~Rol enum mu tablo mu?~~ → **Enum** (`UserRole`, mevcut). Büyürse tabloya geçiş ileriye bırakıldı.

## Tamamlanma Kaydı
- **Durum:** ✅ Tamamlandı (canlı login + Postgres doğrulaması hariç) — 2026-06-25
- **YouTrack:** IK-227 (sıralı varsayım — teyit edilecek)
- **Repolar:** account-hr (backend) + account-hr-frontend (Angular)
- **Backend:** JWT (JJWT 0.12.6), Spring Security stateless, BCrypt, refresh token DB'de SHA-256 hash + rotate + revoke, 401/403 `{error,message}`, method security
- **Frontend:** auth service/interceptor/guard + login sayfası (AwesomeDesign) + topbar kullanıcı/çıkış
- **Seed admin:** `admin@e-commint.com` / `changeme123` (ilk girişte değiştir — BCrypt hash V3'te)
- **Doğrulananlar:** backend `./mvnw test` ✅ **12/12** (7 auth akışı dahil) · frontend `npm run build` ✅
- **Kalan iş:** canlı login round-trip (Angular→API, Postgres) Docker'lı ortamda; V3 migration Postgres'te `ddl-auto=validate` ile teyit
- **Sapmalar:** logout permitAll (refresh-token gövdeli çağrı); refresh = opaque token (JWT değil — iptal edilebilirlik için)
