# [E3-08] Rol bazlı görünümler ve muhasebe giriş ekranı

| Alan | Değer |
|------|-------|
| Epic | E3 — Fatura Yönetim Web Uygulaması |
| Sprint | Sprint 3 |
| Öncelik | Orta |
| Bağımlılıklar | E1-03, E1-07, E3-01, E3-03, E3-04 |
| Tahmini Efor | 5 puan (~3 gün) |
| Etiketler | frontend, backend, auth, roller |

## Amaç
Muhasebe, ekip üyesi ve yönetici farklı yetkilerle giriş yapsın; herkes ihtiyacı olan ekranı görsün. Muhasebe faturaları görüntüleyip indirebilsin, ekip üyesi fatura yükleyebilsin.

## Açıklama / Bağlam
Auth/rol altyapısı E1-03'te kurulur; bu görev rolleri ekran/menü/aksiyon seviyesinde uygular. Roller:
- **Yönetici**: tüm ekranlar + servis yönetimi + ayarlar
- **Ekip üyesi**: harcamaları görür, kendi/ilgili servislerine fatura yükler, eksikleri görür
- **Muhasebe**: tüm ayların harcama + fatura listesini görür, faturaları indirir/önizler, eksikleri görür (yükleme zorunlu değil)

Menü ve aksiyon butonları role göre gösterilir/gizlenir; backend her endpoint'te yetki kontrolü yapar (sadece UI gizleme yeterli değil). Muhasebe için sade bir "tüm faturalar / eksikler" giriş ekranı sunulur.

## Kabul Kriterleri (DOD)
- [ ] Üç rol tanımlı ve kullanıcıya atanabilir (E1-03 ile)
- [ ] Menü ve aksiyonlar role göre gösterilir/gizlenir
- [ ] Backend endpoint'leri rol bazlı yetki kontrolü yapar (Spring Security)
- [ ] Muhasebe rolü: tüm aylar görüntüleme + fatura indirme/önizleme erişimi
- [ ] Ekip üyesi rolü: fatura yükleme + eksik görüntüleme erişimi
- [ ] Yönetici rolü: servis yönetimi + ayarlar erişimi
- [ ] Yetkisiz erişim denemesi 403 ile reddedilir (UI + API)
- [ ] Muhasebe giriş ekranı (landing) role göre uygun ekrana yönlendirir

## Alt Görevler
- [ ] Backend: endpoint'lere `@PreAuthorize`/rol kontrolleri ekle
- [ ] Backend: kullanıcı-rol ilişkisi (E1-03 modeline dayalı)
- [ ] Angular: rol bazlı route guard / menü filtreleme
- [ ] Angular: muhasebe landing ekranı
- [ ] Yetkisiz erişim için hata/yönlendirme sayfası

## Teknik Notlar
- Yetkilendirme hem UI (gizleme) hem API (zorlama) seviyesinde olmalı; UI gizleme güvenlik değildir
- Roller E1-03 auth altyapısından gelir; bu görev sadece uygulama/erişim kuralları
- Aynı API mobil tarafından da kullanılacağından yetki backend'de net olmalı

## Açık Sorular / Riskler
- Bir kullanıcı birden çok role sahip olabilir mi? — **Karar: MVP'de tek rol** (`AppUser.role`); rol-seti ileride.
- Muhasebe fatura indirebilir ama düzenleyemez mi? — **Karar: salt-okunur + indirme/önizleme** (PATCH status hariç).
- Servis bazlı yetki (sadece kendi servisleri) mi? — **Karar: MVP'de rol-bazlı, servis-bazlı DEĞİL** (tüm ekip görür); servis-bazlı E4+.

## Tamamlanma Kaydı
- Durum: Tamamlandı — 2026-06-26
- YouTrack: IK-245 (sıralı varsayım — teyit edilecek)
- Repo: account-hr (backend) + account-hr-frontend
- **Backend:** Her endpoint method-level `@PreAuthorize` ile rol matrisi (backend = tek doğru kaynak). all-three: GET expenses/missing/dashboard/services-read/teams/cards + POST invoices (yükle) + POST expenses (manuel) + GET files list/download. ADMIN+ACCOUNTING: PATCH status, POST files/trash/waiting. ADMIN-only: service create/update/setActive, admin imports/drive/reconciliation. Role→`ROLE_` authority mapping (JwtFilter + CustomUserDetailsService); AccessDenied→403. `auth/me` `@PreAuthorize(isAuthenticated)` + null-guard. `RoleAuthorizationIT` (17 test, 3 rol).
- **Frontend:** AuthService rol helper'ları (role/hasRole/hasAnyRole/homeRoute). `roleGuard` + `landingGuard` + `/403` ForbiddenComponent. Sidebar menü rol-filtreli (merkezi allowedRoles). Rol-bazlı landing (ADMIN→dashboard, ACCOUNTING→eksik fatura, TEAM_MEMBER→dashboard). Detay modalında durum-değiştirme TEAM_MEMBER'a gizli. Servisler: liste all-three görünür, yönetim (yeni/düzenle/aktif) ADMIN-gizli. 403 logout/crash yapmaz.
- **Gerçek doğrulama (lokal PG14, 3 rol token):** GET expenses 200×3; PATCH status ACCOUNTING=200, TEAM_MEMBER=403; auth/me token-yok=401 (500 değil). RoleAuthorizationIT 17/17 geçerli-çağrı 403'leri kanıtlıyor.
- Test: backend 266/266; frontend tsc+ng build temiz. Bağımsız review (role→authority CLEAN, 23 endpoint gate CLEAN); 2 bulgu düzeltildi (auth/me gate; /services frontend read all-three açıldı).
- **Borç kapandı:** "file per-invoice ownership → E3-08" — file endpoint'leri artık rol-bazlı yetkili (download/list all-three, trash/waiting ADMIN+ACCOUNTING).
