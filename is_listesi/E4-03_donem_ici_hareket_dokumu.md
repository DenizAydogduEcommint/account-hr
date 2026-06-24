# [E4-03] Dönem-içi hareket dökümü işleme

| Alan | Değer |
|------|-------|
| Epic | E4 — Banka Ekstresi İşleme |
| Sprint | Sprint 5 |
| Öncelik | Orta |
| Bağımlılıklar | E4-01, E4-02 |
| Tahmini Efor | 5 puan (~3 gün) |
| Etiketler | backend, ekstre, parse |

## Amaç
Ekstre kesim tarihinden sonra ay sonuna kadar olan işlemleri (dönem-içi hareket dökümü) ayrı dosya olarak işle ve aynı ayın harcamalarına ekle. Böylece ay sonu yaklaşırken kesim sonrası çekimler de yakalanır.

## Açıklama / Bağlam
Kart ekstresi belirli bir kesim tarihinde kapanır; o tarihten ay sonuna kadar olan işlemler ayrı bir "dönem-içi hareket dökümü" olarak gelir (CLAUDE.md akışında ayrı adım). Bu görev, bu ek dökümü E4-01/E4-02 altyapısıyla işler ama ilgili ayın doğru bölümüne ekler. Kesim tarihi ile dönem-içi dönemin çakışmaması (mükerrer işlem) önemlidir.

## Kabul Kriterleri (DOD)
- [ ] Dönem-içi hareket dökümü ayrı yükleme tipi olarak işlenir
- [ ] İşlemler E4-01 parse + E4-02 eşleştirme akışından geçer
- [ ] İşlemler doğru aya/sheet'e eklenir
- [ ] Ekstre kesim dönemi ile dönem-içi dönem arası mükerrer işlem önlenir
- [ ] Hangi işlemlerin "kesim sonrası" olduğu işaretlenir/izlenebilir
- [ ] Kullanıcı yükleme sırasında dönem/kart bilgisini doğrular

## Alt Görevler
- [ ] Backend: dönem-içi döküm yükleme tipi ve metadata (kesim tarihi, dönem aralığı)
- [ ] Backend: mükerrer işlem tespiti (ekstre vs dönem-içi)
- [ ] Backend: işlemleri ilgili aya ekleme
- [ ] Angular: dönem-içi döküm yükleme akışı (E4-01 ekranını genişletme)

## Teknik Notlar
- Parse ve eşleştirme E4-01/E4-02 ile aynı altyapıyı kullanır; fark dönem/işaretleme
- Mükerrer kontrolü için işlem benzersiz anahtarı (tarih+tutar+kart+işyeri) kullanılabilir
- Kesim tarihi karttan karta değişir; metadata olarak tutulmalı

## Açık Sorular / Riskler
- Dönem-içi döküm formatı normal ekstre ile aynı mı yoksa farklı mı? — Örnek dosya gerekli
- Aynı işlem hem ekstrede hem dönem-içi dökümünde çıkarsa hangisi esas alınır? — Öneri: ekstre esas, dönem-içi sadece eksikleri ekler
