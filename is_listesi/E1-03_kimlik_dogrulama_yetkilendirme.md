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
- [ ] `POST /api/auth/login` (email + parola) → JWT access token (+ refresh token) döner
- [ ] `POST /api/auth/refresh` çalışır; access token süresi dolunca yenilenir
- [ ] Roller tanımlı: `ROLE_ADMIN`, `ROLE_ACCOUNTING`, `ROLE_TEAM_MEMBER`
- [ ] Endpoint'ler role göre korunuyor (`@PreAuthorize` veya security config); yetkisiz erişim 403 döner
- [ ] Parolalar BCrypt ile hashlenmiş şekilde saklanıyor
- [ ] Angular tarafında login akışı + token saklama + korumalı route iskeleti çalışıyor
- [ ] Geçersiz/expired token → 401, tutarlı hata formatı (E1-07 ile uyumlu)

## Alt Görevler
- [ ] User entity'ye parola hash, rol, aktiflik alanları ekle
- [ ] Spring Security config (filter chain, JWT filter, password encoder)
- [ ] JWT üretme/doğrulama servisi (access + refresh)
- [ ] Login/refresh/me endpoint'leri
- [ ] Rol bazlı erişim kuralları (method security)
- [ ] Angular: login sayfası, auth service, HttpInterceptor (token ekleme + 401'de logout)
- [ ] İlk admin kullanıcısını seed et

## Teknik Notlar
- accounting@e-commint.com kurumsal mail; ileride Google SSO entegrasyonu düşünülebilir ama MVP için email+parola yeterli
- JWT secret ve süreler config/secret yönetiminden gelmeli (E1-05)
- Refresh token DB'de saklanıp iptal edilebilir olmalı (logout / güvenlik)
- account-hr "hr" eki ileride personel modülü ima ediyor olabilir; rol modeli genişletilebilir tasarlanmalı (rol tablosu vs enum kararı)

## Açık Sorular / Riskler
- Google SSO MVP'ye girecek mi? (Öneri: hayır, faz 2)
- Dış mali müşavir gerçek kullanıcı mı yoksa sadece "ilet" hedefi mi? Kullanıcı hesabı mı açılacak yoksa sadece mail mi atılacak netleştirilmeli
- Rol enum mu tablo mu? (account-hr büyüyecekse tablo daha esnek)
