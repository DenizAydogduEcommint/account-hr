# [E1-08] Kullanıcı Yönetimi (Backoffice) — sadece ADMIN

| Alan | Değer |
|------|-------|
| Epic | E1 — Temel Altyapı (auth ailesi) |
| Öncelik | Orta-Yüksek |
| Bağımlılıklar | E1-03 (auth/JWT), E3-08 (rol matrisi) |
| Tahmini Efor | 4 puan |
| Etiketler | backend, frontend, auth, admin, guvenlik |
| YouTrack | YouTrack issue henüz açılmadı — Epic 1 (IK-291) altında açılacak |

## Amaç
Yöneticilerin sisteme giriş yapacak kullanıcıları yönetebileceği bir `/backoffice` sayfası. Başlangıç kapsamı: kullanıcı ekleme (yetki tipi seçerek), şifre sıfırlama, rol değiştirme, aktif/pasif. Sadece ADMIN erişebilir; sol menüde "Backoffice" linki yalnızca ADMIN'e görünür.

## Kapsam (başlangıç — minimal)
- Kullanıcı listesi (şifre hariç)
- Yeni kullanıcı ekle + rol (Yönetici/Muhasebe/Ekip Üyesi) seç
- Şifre sıfırla
- Rol değiştir
- Aktif/Pasif yap
- **Silme YOK** (pasifleştirme yeterli — audit/güvenlik)

## Kabul Kriterleri (DOD)
- [ ] `GET /api/v1/admin/users` (ADMIN-only) — kullanıcı listesi, **passwordHash asla dönmez**
- [ ] `POST /api/v1/admin/users` — email/ad/rol/şifre; email unique (409), şifre min 8
- [ ] `PATCH /api/v1/admin/users/{id}/password` — BCrypt re-hash + hedef kullanıcının refresh token'larını **iptal et**
- [ ] `PATCH /api/v1/admin/users/{id}/role` — rol değiştir
- [ ] `PATCH /api/v1/admin/users/{id}/active` — aktif/pasif
- [ ] **Son-admin koruması:** son aktif ADMIN'in rolü düşürülemez / pasifleştirilemez (409, anlaşılır mesaj)
- [ ] Tüm endpoint'ler ADMIN-only; non-admin → 403
- [ ] Frontend `/backoffice` route ADMIN-guard; sidebar linki yalnızca ADMIN'e görünür
- [ ] Pasif kullanıcı login olamaz (mevcut active kontrolü)
- [ ] AppUser değişiklikleri audit'lenir (mevcut interceptor)

## Güvenlik Notları (ultrathink — KRİTİK)
- **Son-admin kilitleme**: en tehlikeli senaryo — son admin'i düşürmek sistemi yönetilemez bırakır. Her rol-düşürme ve pasifleştirmede "işlem sonrası aktif admin sayısı ≥ 1" invariantı korunmalı.
- **passwordHash sızıntısı**: response DTO'da kesinlikle yok.
- **Oturum geçersizleştirme**: şifre değişince hedefin aktif refresh token'ları revoke (çalınmış oturum riski).
- **Yetki backend'de zorlanır**; UI gizleme güvenlik değildir.
- Şifre politikası: min 8 karakter (MVP).
- Rol isimleri UI'da Türkçe: ADMIN→Yönetici, ACCOUNTING→Muhasebe, TEAM_MEMBER→Ekip Üyesi.

## Açık Sorular / Riskler
- Kendi kendine işlem: admin kendi rolünü düşürebilir mi? → Son-admin koruması bunu zaten engeller (son admin değilse serbest).
- E-posta ile davet/şifre belirleme akışı → MVP dışı; admin geçici şifre verir, kullanıcı sonra değiştirir (self-change ileride).

## Tamamlanma Kaydı
- Durum: **Tamamlandı — 2026-06-27**
- YouTrack: YouTrack issue henüz açılmadı — Epic 1 (IK-291) altında açılacak
- Repo: account-hr (backend) + account-hr-frontend
- **Backend:** `AdminUserController` + `AdminUserService` (base `/api/v1/admin/users`, hepsi `@PreAuthorize("hasRole('ADMIN')")`): GET liste, POST ekle, PATCH /{id}/password|role|active. `AdminUserResponse` (**passwordHash YOK** — test'le kanıtlı). Şifre BCrypt(12), min 8. Email unique (409 DUPLICATE_EMAIL). Şifre değişince hedefin refresh token'ları iptal. **Son-admin koruması: post-mutation validation (save+flush+count<1→409) — TOCTOU yarışına dayanıklı.** AppUser artık audit'leniyor (CREATE/role/active) — passwordHash audit'e ulaşamaz (UPDATE'te SENSITIVE_FIELDS filtreli, CREATE'te yapısal yok). Migration yok (users tablosu mevcut).
- **Frontend:** `/backoffice` (roleGuard ADMIN), sidebar "Backoffice" yalnız ADMIN. Kullanıcı tablosu (TR rol/durum badge), "Yeni Kullanıcı" + "Şifre Sıfırla" modalları. **Şifre çift-giriş (eşleşme kontrolü) + göz ikonu (göster/gizle)** her iki modalda. 409 (son-admin/dup) mesajı inline. Info kartı. OnDestroy temiz.
- **Gerçek doğrulama (lokal PG14):** yeni kullanıcı 201 (passwordHash yok) + login; non-admin→403; dup→409; son-admin pasif→409 "Sistemde en az bir aktif yönetici kalmalı."; audit_log AppUser CREATE+UPDATE(role) yazılıyor, passwordHash sızıntısı 0.
- Test: backend **333/333** (+14: AdminUserManagementIT + AppUserAuditIT); frontend tsc+ng build temiz.
- **Bağımsız güvenlik review:** passwordHash sızıntısı/yetki/token-revoke/mass-assignment/contract CLEAN; 2 bulgu düzeltildi — last-admin TOCTOU (post-mutation), kullanıcı yönetimi audit izi.
