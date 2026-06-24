# [E9-03] Banka hesap ekstreleri tam mutabakat (gelecek vizyon)

| Alan | Değer |
|------|-------|
| Epic | E9 — Gelecek Vizyon |
| Sprint | Backlog / Sonraki çeyrek |
| Öncelik | Düşük |
| Tahmini Efor | 8 puan (~5 gün, kaba tahmin) |
| Bağımlılıklar | E5-04 (eşleştirme), E7 (Paraşüt) |
| Etiketler | gelecek, banka, mutabakat, vizyon |

## Amaç
Banka/kredi kartı hesap ekstrelerini sistemdeki fatura/gider kayıtlarıyla tam mutabık hale getirmek: her banka hareketinin karşılığı fatura ve Paraşüt kaydı var mı? Kaan vizyonu madde 11.

## Açıklama / Bağlam
Outline seviyesinde. Şu an kart ekstreleri manuel Excel'e işleniyor. Bu görev ekstre hareketlerini otomatik içe alıp (her satır = bir işlem) fatura ve gider kayıtlarıyla eşleştirerek mutabakat raporu üretir: eşleşmeyen banka hareketleri (faturası yok) ve eşleşmeyen kayıtlar tespit edilir. Mevcut 3 kart (Akbank Axess ****3800, YKB ****3909, Ziraat ****9164).

## Kabul Kriterleri (DOD)
- [ ] Banka/kart ekstresi otomatik içe alınıyor (PDF/XLS/XLSX/API)
- [ ] Her banka hareketi fatura + gider kaydıyla eşleştiriliyor
- [ ] Mutabakat raporu: eşleşen / faturası eksik / kayıtta olup ekstrede olmayan
- [ ] Kart bazlı ayrım (3 kart)

## Alt Görevler
- [ ] Ekstre içe alma (format başına parser; dönem-içi hareket dökümü dahil)
- [ ] Banka hareketi ↔ gider eşleştirme (E5-04 motoru)
- [ ] Mutabakat raporu

## Teknik Notlar
- Mevcut akıştaki ekstre + dönem-içi hareket dökümü mantığı temel.
- Banka API'leri varsa otomatik çekim; yoksa dosya yükleme.

## Açık Sorular / Riskler
- Banka API erişimi var mı, yoksa dosya bazlı mı?
- Çok para birimli işlemlerde kur/komisyon farkları mutabakatı zorlaştırır.
