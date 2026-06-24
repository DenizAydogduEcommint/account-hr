# [E8-01] Aylık fatura paketi dışa aktarımı (zip / Drive linki / Excel)

| Alan | Değer |
|------|-------|
| Epic | E8 — Mali Müşavir Paylaşımı & Raporlama |
| Sprint | Sprint 7 |
| Öncelik | Yüksek |
| Tahmini Efor | 3 puan (~2 gün) |
| Bağımlılıklar | E1-04 (dosya depolama), E5-04 (eşleştirme), E7-01 (Excel şablonu) |
| Etiketler | raporlama, export, mali-müşavir, drive |

## Amaç
Bir ayın tüm faturalarını + özet Excel'i tek pakette (zip / Drive linki / Excel) dış mali müşavire iletmek. Mevcut "Excel + faturaları Drive'a sync edip muhasebeciye link iletme" adımının otomasyonu.

## Açıklama / Bağlam
Her ay sonunda muhasebeciye o ayın harcama Excel'i ve fatura dosyaları iletiliyor. Bu görev seçilen ay için: o aya ait fatura dosyalarını (ay klasörleri) + özet Excel'i (E7-01 formatına benzer veya CLAUDE.md kolon yapısı) toplayıp bir paket üretir; zip indirme veya Drive klasör linki olarak sunar.

## Kabul Kriterleri (DOD)
- [ ] Seçilen ay için tüm fatura dosyaları + özet Excel tek pakette toplanıyor
- [ ] Çıktı: indirilebilir zip ve/veya paylaşılabilir Drive linki
- [ ] Özet Excel CLAUDE.md kolon yapısına (12 kolon) veya Paraşüt şablonuna uygun
- [ ] Eksik faturalı satırlar pakette açıkça işaretli (eksik liste dahil)
- [ ] Paket üretimi izlenebilir (kim, ne zaman, hangi ay — audit E8-04)

## Alt Görevler
- [ ] Ay bazlı fatura dosyası + Excel toplama servisi
- [ ] Zip üretimi
- [ ] Drive'a paket yükleme + paylaşım linki (Drive API/rclone) — push kuralına dikkat (sadece paket çıktısı)
- [ ] Özet Excel üretici (E7-01 ile ortak kod)
- [ ] Eksik fatura işaretleme

## Teknik Notlar
- Java zip (java.util.zip) + Apache POI Excel.
- Drive linki için paylaşım izinleri mali müşavir e-postasına göre ayarlanmalı.
- CLAUDE.md: "Excel + faturalar Drive'a sync, muhasebeye link" akışının otomatik karşılığı.

## Açık Sorular / Riskler
- Mali müşavir hangi formatı tercih ediyor (zip mi, Drive mı, Paraşüt zaten gönderiyorsa sadece özet mi)?
- Büyük aylarda zip boyutu — Drive linki tercih edilebilir.
