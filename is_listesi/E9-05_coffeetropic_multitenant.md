# [E9-05] Coffeetropic uyarlaması (multi-tenant mimari) (gelecek vizyon)

| Alan | Değer |
|------|-------|
| Epic | E9 — Gelecek Vizyon |
| Sprint | Backlog / Sonraki çeyrek |
| Öncelik | Düşük |
| Tahmini Efor | 8 puan (~5 gün+, kaba tahmin) |
| Bağımlılıklar | E1-02 (veri modeli), E5-06, E7, E8 (çekirdek akış olgunlaşması) |
| Etiketler | gelecek, multi-tenant, coffeetropic, mimari, vizyon |

## Amaç
Platformu birden çok şirkete (E-Commint + Coffeetropic) hizmet verecek multi-tenant mimariye uyarlamak. Her şirketin kendi servisleri, kartları, mali müşaviri, Paraşüt hesabı ve verisi izole olur.

## Açıklama / Bağlam
Outline seviyesinde. Önce E-Commint için tek-tenant çalışan platform, Coffeetropic gibi başka şirketlere de uyarlanacak. Bu, veri izolasyonu (tenant bazlı), konfigürasyon (servis listesi, kartlar, mali müşavir, Paraşüt kimlikleri tenant başına) ve erişim kontrolü gerektirir. Mimari karar erken alınmalı (veri modeline tenant boyutu eklemek sonradan zor).

## Kabul Kriterleri (DOD)
- [ ] Multi-tenant mimari kararı (ayrı DB / şema / tenant kolonu) verildi ve belgelendi
- [ ] Tüm veri tenant bazlı izole (bir şirketin verisi diğerine sızmaz)
- [ ] Tenant başına konfigürasyon (servisler, kartlar, mali müşavir, Paraşüt kimlikleri)
- [ ] Erişim kontrolü kullanıcıyı tenant'a bağlıyor
- [ ] Coffeetropic test tenant'ı ile uçtan uca doğrulama

## Alt Görevler
- [ ] Multi-tenant strateji kararı (row-level / schema / DB)
- [ ] Veri modeline tenant boyutu (E1-02 ile geriye dönük uyum)
- [ ] Tenant bazlı konfigürasyon yönetimi
- [ ] Erişim kontrolü / tenant kapsamı
- [ ] Coffeetropic pilot

## Teknik Notlar
- Tenant boyutu E1-02 veri modeline mümkünse baştan eklenirse (en azından kolon olarak) sonradan migration kolaylaşır — bu vizyon görevi olsa da erken mimari not.
- Spring Security ile tenant-aware erişim.

## Açık Sorular / Riskler
- İzolasyon seviyesi (paylaşımlı şema vs ayrı DB) maliyet/karmaşıklık dengesi.
- Coffeetropic'in ihtiyaçları E-Commint'ten farklıysa konfigürasyon yetmeyebilir.
- Erken karar gerekiyor ama düşük öncelik — çelişki: en azından veri modeli kararı E1-02'de düşünülmeli.
