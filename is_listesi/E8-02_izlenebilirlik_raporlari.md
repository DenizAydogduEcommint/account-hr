# [E8-02] İzlenebilirlik raporları: eşlenen/eşlenmeyen, eksik faturalar, durum dağılımı

| Alan | Değer |
|------|-------|
| Epic | E8 — Mali Müşavir Paylaşımı & Raporlama |
| Sprint | Sprint 7 |
| Öncelik | Yüksek |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E1-02 (veri modeli), E5-04 (eşleştirme), E3-04 (eksik fatura ekranı) |
| Etiketler | raporlama, dashboard, izlenebilirlik |

## Amaç
Tüm akışın durumunu gösteren raporlar üretmek: kaç fatura eşlendi/eşlenmedi, hangi servislerin faturası eksik, durum dağılımı (Bulundu/e-Fatura/Bekleniyor/Araştırılacak/Ignored). Kaan'ın "eşlenen/eşlenmeyen raporlanır" (madde 12) talebinin karşılığı.

## Açıklama / Bağlam
Yönetim ve muhasebenin "bu ay her şey toplandı mı, ne eksik" sorusuna anlık cevap veren raporlar/dashboard. Veriler fatura ve harcama kayıtlarından gelir. Frontend Angular ile görselleştirilir; bu görev hem rapor API'sini hem temel ekranı kapsar.

## Kabul Kriterleri (DOD)
- [ ] Eşlenen vs eşlenmeyen fatura sayısı/oranı (ay bazlı) raporlanıyor
- [ ] Eksik fatura listesi: hangi aktif servis için fatura yok (Servisler master çapraz)
- [ ] Durum dağılımı (Bulundu / e-Fatura / Bekleniyor / Araştırılacak / Ignored) grafik/tablo
- [ ] Ay ve servis bazlı filtreleme
- [ ] Rapor verisi API olarak sunuluyor (E8-03 özet maili de bunu kullanır)
- [ ] Angular ekranında görselleştirme

## Alt Görevler
- [ ] Rapor sorguları (eşleşme, eksik, durum dağılımı)
- [ ] Rapor REST API uç noktaları
- [ ] Angular dashboard ekranı (kart + grafik + tablo)
- [ ] Servisler master çapraz doğrulama ile eksik listesi
- [ ] Filtre (ay/servis/durum)

## Teknik Notlar
- Sorgular PostgreSQL üzerinde; ağır raporlar için materialized view düşünülebilir.
- Hazır temanın grafik/kart bileşenleri kullanılır.
- E3-04 eksik fatura ekranıyla veri paylaşır; tekrar yazılmasın.

## Açık Sorular / Riskler
- "Eksik" tanımı: fatura kesim günü gelmemiş servis eksik sayılmamalı — zamanlama mantığı netleşmeli.
- Tarihsel veri (Ocak-Mart manuel dönem) raporlara dahil mi?
