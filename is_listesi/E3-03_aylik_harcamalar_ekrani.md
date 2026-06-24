# [E3-03] Aylık harcamalar ekranı: 12 kolonlu tablo

| Alan | Değer |
|------|-------|
| Epic | E3 — Fatura Yönetim Web Uygulaması |
| Sprint | Sprint 2 |
| Öncelik | Yüksek |
| Bağımlılıklar | E1-02, E1-03, E1-07, E2-01 |
| Tahmini Efor | 5 puan (~3 gün) |
| Etiketler | frontend, backend, ui, harcamalar |

## Amaç
Bir ayın tüm harcama satırlarını Excel'deki gibi tablo halinde göster; ekip ve muhasebe ay bazında tüm giderleri ve fatura durumlarını görebilsin.

## Açıklama / Bağlam
Excel ay sheet'inin web karşılığı. Seçili ay için harcama satırları 12 kolonlu tabloda listelenir: Tarih, Hizmet, Sağlayıcı, Tutar, Para Birimi, TL Karşılığı, Kart, Kullanan Takım, Amaç, Muhasebe E-posta, Fatura Durumu, Fatura Notu. Fatura Durumu kolonu renkli rozet (badge) olarak gösterilir (renk kuralları E3-07). Ay seçici, kart filtresi, durum filtresi ve serbest metin arama bulunur. Tablo altında operasyonel toplam (TL) gösterilir; Ignored/bilgi amaçlı satırlar toplama dahil edilmez ama listede ayrı bir bölümde görünür.

## Kabul Kriterleri (DOD)
- [ ] 12 kolonun tamamı tabloda gösterilir
- [ ] Ay seçici ile farklı aylar listelenir
- [ ] Fatura Durumu renkli rozet olarak gösterilir (renk kuralları E3-07 ile aynı)
- [ ] Kart, durum ve serbest metin (Hizmet/Sağlayıcı) filtreleri çalışır
- [ ] Operasyonel toplam (TL Karşılığı) tablo altında gösterilir
- [ ] Ignored / bilgi amaçlı satırlar (Multinet, sigorta, vergi) ayrı bölümde, toplama dahil edilmeden gösterilir
- [ ] Satıra tıklayınca detay/aksiyon (durum değiştir, fatura ekle) erişilebilir
- [ ] Tutar ve TL kolonları `#,##0.00` formatında gösterilir

## Alt Görevler
- [ ] Backend: `GET /api/expenses?month=YYYY-MM&card=&status=&q=` listeleme endpoint'i
- [ ] Backend: operasyonel/bilgi amaçlı ayrımı (is_informational flag)
- [ ] Angular: 12 kolonlu veri tablosu (sıralama + sayfalama)
- [ ] Angular: filtre çubuğu (ay/kart/durum/arama)
- [ ] Angular: durum rozeti bileşeni (ortak)
- [ ] Toplam satırı hesaplama (backend'den gelen toplam)

## Teknik Notlar
- Para birimi bazında değerler ham sayı olarak saklanır; format ön yüzde uygulanır
- Tarih formatı `DD.MM.YYYY`
- Bilgi amaçlı satırlar için satır seviyesinde flag, Excel'deki gri başlıklı bölümün karşılığı
- Aynı durum rozeti bileşeni dashboard ve eksik fatura ekranında da kullanılır

## Açık Sorular / Riskler
- Inline düzenleme mi yoksa detay modalında düzenleme mi? (Öneri: önce modal, inline sonraki iterasyon)
- Çok satırlı aylarda sayfalama mı sonsuz kaydırma mı? (Öneri: sayfalama)
