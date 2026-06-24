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
- Bir kullanıcı birden çok role sahip olabilir mi? (Öneri: evet, rol seti)
- Muhasebe fatura indirebilir ama düzenleyemez mi? — Öneri: salt-okunur + indirme
- Servis bazlı yetki (sadece kendi servisleri) gerekecek mi yoksa tüm ekip her şeyi görür mü? — Karar gerekli
