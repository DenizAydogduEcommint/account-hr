# [E9-04] Satış faturaları & tahsilat eşleştirme (gelecek vizyon)

| Alan | Değer |
|------|-------|
| Epic | E9 — Gelecek Vizyon |
| Sprint | Backlog / Sonraki çeyrek |
| Öncelik | Düşük |
| Tahmini Efor | 8 puan (~5 gün, kaba tahmin) |
| Bağımlılıklar | E9-03 (banka mutabakat), E7 (Paraşüt) |
| Etiketler | gelecek, satış, tahsilat, vizyon |

## Amaç
Kesilen satış faturalarını gelen tahsilatlarla (banka girişleri) eşleştirmek: hangi fatura ödendi, hangisi açık/gecikmiş. Kaan vizyonu madde 9-10. Platformu gider tarafından gelir/alacak tarafına genişletir.

## Açıklama / Bağlam
Outline seviyesinde. Şu ana kadarki kapsam giderler (faturaları toplama). Bu görev satış (gelir) tarafını ekler: satış faturaları (Paraşüt'ten veya manuel) ile banka tahsilatlarını eşleştirip açık alacak/tahsilat raporu üretir.

## Kabul Kriterleri (DOD)
- [ ] Satış faturaları sisteme alınıyor (Paraşüt/manuel)
- [ ] Banka tahsilatları (gelen ödemeler) ile eşleştiriliyor
- [ ] Açık alacak / tahsil edilen / gecikmiş raporu
- [ ] Kısmi tahsilat desteği

## Alt Görevler
- [ ] Satış faturası içe alma
- [ ] Tahsilat ↔ fatura eşleştirme (E9-03 banka verisi)
- [ ] Açık alacak raporu

## Teknik Notlar
- E9-03 banka mutabakat altyapısı ve E7 Paraşüt entegrasyonu temel.

## Açık Sorular / Riskler
- Müşteri/ödeme referansı tutarsızsa eşleştirme zor (kısmi/toplu ödemeler).
- Kapsam genişlemesi — ayrı epic olabilir.
