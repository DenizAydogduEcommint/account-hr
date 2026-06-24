# [E9-01] Personel masraf raporları + OCR (gelecek vizyon)

| Alan | Değer |
|------|-------|
| Epic | E9 — Gelecek Vizyon |
| Sprint | Backlog / Sonraki çeyrek |
| Öncelik | Düşük |
| Tahmini Efor | 8 puan (~5 gün, kaba tahmin) |
| Bağımlılıklar | E5-03 (belge okuma/OCR), E7-03 (gider dönüştürücü) |
| Etiketler | gelecek, personel-masraf, ocr, vizyon |

## Amaç
Personelin fiş/makbuz/masraf belgelerini fotoğraflayıp sisteme yüklemesi, OCR ile tutar/tarih/satıcı çıkarımı ve masraf raporuna dönüştürülmesi. Kaan vizyonu madde 5-6.

## Açıklama / Bağlam
Outline seviyesinde. Çalışanlar yemek/ulaşım/ekipman fişlerini mobil/web ile yükler; sistem OCR ile okur, kategorize eder, onay akışından geçirip Paraşüt'e gider olarak gönderir (E9-02 ile konsolide). E5-03 OCR altyapısı yeniden kullanılır.

## Kabul Kriterleri (DOD)
- [ ] Personel masraf belgesi yükleme akışı (foto/PDF)
- [ ] OCR ile tutar/tarih/satıcı/kategori çıkarımı
- [ ] Onay akışı (çalışan → yönetici onayı)
- [ ] Onaylanan masraf Paraşüt gider formatına dönüşebiliyor (E7-03 ile)

## Alt Görevler
- [ ] Yükleme arayüzü (web/mobil)
- [ ] OCR pipeline (E5-03 yeniden kullan)
- [ ] Onay iş akışı
- [ ] Gider dönüşümü entegrasyonu

## Teknik Notlar
- E5-03 OCR çekirdeği temel alınır.
- Detaylandırma ileride; şu an yön belirleme amaçlı.

## Açık Sorular / Riskler
- Mobil mi web mi öncelik?
- Onay hiyerarşisi ve harcama politikaları tanımlı değil.
