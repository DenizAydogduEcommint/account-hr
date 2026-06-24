# [E3-01] Dashboard: aylık özet ve eksik fatura sayacı

| Alan | Değer |
|------|-------|
| Epic | E3 — Fatura Yönetim Web Uygulaması |
| Sprint | Sprint 3 |
| Öncelik | Orta |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E1-02, E1-03, E1-07, E3-04 |
| Etiketler | frontend, backend, ui, dashboard |

## Amaç
Ekip ve muhasebe giriş yaptığında o ayın durumunu tek bakışta görsün: kaç fatura eksik, kaç bulundu, toplam harcama ne kadar. Eksik fatura acısını ana ekrana taşır.

## Açıklama / Bağlam
Hazır admin temasının kart ve grafik bileşenleri kullanılarak bir ana sayfa (dashboard) yapılacak. Üstte seçili ay için özet kartları (KPI kartları): "Toplam Harcama (TL)", "Eksik Fatura Sayısı", "Bulunan Fatura", "Araştırılacak". Altında fatura durumu dağılımını gösteren bir donut/pasta grafik (Bulundu / Bekleniyor / Araştırılacak / e-Fatura / Ignored). Ay seçici (dropdown) ile farklı aylara geçilebilir. Veriler backend'den bir özet endpoint'i üzerinden gelir, ön yüzde hesap yapılmaz.

## Kabul Kriterleri (DOD)
- [ ] Dashboard varsayılan olarak içinde bulunulan ayı gösterir
- [ ] Eksik fatura sayacı E3-04 çapraz doğrulama mantığıyla aynı sayıyı verir
- [ ] KPI kartları: toplam harcama (TL), eksik, bulundu, araştırılacak
- [ ] Durum dağılımı grafiği renk kodlarıyla uyumlu (Bulundu yeşil, Bekleniyor kırmızı, vb.)
- [ ] Ay seçici çalışır; ay değişince tüm kartlar/grafik güncellenir
- [ ] Bilgi amaçlı/Ignored satırlar operasyonel toplama dahil edilmez
- [ ] Mobil/dar ekranda kartlar alt alta düzgün dizilir

## Alt Görevler
- [ ] Backend: `GET /api/dashboard/summary?month=YYYY-MM` özet endpoint'i (toplam, durum bazlı sayımlar, eksik sayısı)
- [ ] Angular: hazır temanın KPI kart bileşenlerini yerleştir
- [ ] Angular: durum dağılımı için ApexCharts donut grafik
- [ ] Ay seçici bileşeni (paylaşılan, diğer ekranlarda da kullanılacak)
- [ ] Loading/empty state (veri yokken)

## Teknik Notlar
- Özet hesabı backend'de SQL ile yapılmalı; ön yüze ham satır listesi dökülmemeli
- Renk kodları merkezi bir sabit/enum'dan gelmeli (E3-07 ile paylaşılır)
- Hazır temanın dashboard layout'u referans alınabilir

## Açık Sorular / Riskler
- "Toplam harcama" döviz satırları için TL Karşılığı toplamı mı, yoksa para birimi bazında ayrı mı gösterilecek? (Öneri: TL Karşılığı toplamı)
- Eksik fatura sayacı E3-04'e bağımlı; E3-04 tamamlanmadan placeholder sayı gösterilebilir
