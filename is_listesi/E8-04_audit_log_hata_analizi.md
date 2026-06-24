# [E8-04] Denetim/audit log görünümü & hata-nedeni analizi

| Alan | Değer |
|------|-------|
| Epic | E8 — Mali Müşavir Paylaşımı & Raporlama |
| Sprint | Sprint 8 |
| Öncelik | Orta |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E1-02 (veri modeli), E5-06 (orkestrasyon), E7-04 (gönderim) |
| Etiketler | audit, log, hata-analizi, izlenebilirlik |

## Amaç
Tüm otomatik akışın (mail tarama, Drive pull, panel indirme, eşleştirme, Paraşüt gönderim) her adımını izlenebilir kaydetmek ve hataları nedenleriyle gruplayıp analiz edebilecek bir görünüm sunmak. Kaan'ın "hata nedenleri analiz edilir" (madde 12) talebi.

## Açıklama / Bağlam
Sistem arka planda çok adımlı çalıştığından bir fatura kaçtığında "neden" sorusu kritik: panel login mi başarısız oldu, OCR mı okuyamadı, eşleşme mi bulunamadı, Paraşüt mü reddetti. Bu görev, her önemli adımı yapısal audit kaydı olarak yazar (kaynak, varlık, sonuç, hata kodu/nedeni) ve Angular ekranında filtrelenebilir/gruplanabilir bir görünüm + hata-nedeni dağılımı sunar.

## Kabul Kriterleri (DOD)
- [ ] Her önemli adım yapısal audit kaydı üretiyor (zaman, aktör/sistem, varlık, aksiyon, sonuç, hata nedeni)
- [ ] Audit kayıtları filtrelenebiliyor (tarih, kaynak, servis, sonuç)
- [ ] Hata kayıtları neden kategorisine göre gruplanıp dağılım gösteriliyor (login hatası / OCR / eşleşme yok / Paraşüt red ...)
- [ ] Bir faturanın uçtan uca izi (geldi → parse → eşleşti → gönderildi) tek yerden görülebiliyor
- [ ] Angular ekranı + API
- [ ] Kayıtlar değiştirilemez (append-only) ve makul süre saklanıyor

## Alt Görevler
- [ ] Audit log veri modeli (append-only tablo)
- [ ] Akış adımlarına audit yazımı (E5-06, E7-04, E5-05 vb.)
- [ ] Hata-nedeni taksonomisi + kategorize etme
- [ ] Audit görünümü API + Angular ekranı
- [ ] Fatura bazlı uçtan uca iz (timeline) görünümü

## Teknik Notlar
- Append-only tablo (PostgreSQL); yüksek hacimde partition/retention politikası.
- Hata taksonomisi enum + serbest mesaj; raporlanabilirlik için enum kritik.
- E8-02 raporları ve E8-03 maili audit verisinden beslenebilir.

## Açık Sorular / Riskler
- Saklama süresi / KVKK (e-posta içerikleri, credential izi loglanmamalı).
- Hata taksonomisini fazla detaylandırmak bakım yükü — pratik bir set ile başla.
