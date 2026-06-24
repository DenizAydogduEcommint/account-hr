# [E4-05] Çoklu banka/kart normalizasyonu ve para birimi/TL karşılığı

| Alan | Değer |
|------|-------|
| Epic | E4 — Banka Ekstresi İşleme |
| Sprint | Sprint 4 |
| Öncelik | Orta |
| Bağımlılıklar | E1-02, E4-01 |
| Tahmini Efor | 5 puan (~3 gün) |
| Etiketler | backend, normalizasyon, kart |

## Amaç
Farklı bankaların (Akbank, YKB, Ziraat, ileride QNB) farklı ekstre formatlarını ortak bir işlem modeline normalize et; para birimi ve TL karşılığını tutarlı şekilde ele al.

## Açıklama / Bağlam
Her banka ekstresini farklı kolon düzeni, tarih/sayı formatı ve kart gösterimiyle veriyor. Bu görev, banka bazlı bir normalizasyon katmanı kurar: ham parse çıktısını (E4-01) ortak işlem modeline çevirir. Kart son 4 hanesi tanımlı kartlarla (****3800 Akbank, ****3909 YKB, ****9164 Ziraat) eşlenir. Döviz işlemlerinde orijinal tutar + para birimi + TL karşılığı ayrı tutulur (ekstredeki TL tutarı esas).

## Kabul Kriterleri (DOD)
- [ ] Banka bazlı normalizasyon stratejisi (Akbank, YKB, Ziraat) tanımlı ve genişletilebilir (QNB eklenebilir)
- [ ] Kart son 4 hane tanımlı kart referansına eşlenir (banka + sahip dahil)
- [ ] Tarih ve sayı formatları (TR: binlik nokta, ondalık virgül) doğru ayrıştırılır
- [ ] Döviz işlemi: orijinal tutar + para birimi + TL karşılığı ayrı saklanır
- [ ] Bilinmeyen banka/format için anlaşılır hata, sessiz hata yok
- [ ] Yeni banka eklemek için kod değişikliği minimal (strateji/konfig)

## Alt Görevler
- [ ] Backend: banka bazlı parse/normalizasyon stratejisi arayüzü
- [ ] Backend: Akbank, YKB, Ziraat uyarlamaları
- [ ] Backend: kart referans eşleme (son 4 hane → kart/banka/sahip)
- [ ] Backend: TR sayı/tarih format ayrıştırıcı
- [ ] Backend: döviz/TL karşılığı alanları (E1-02 ile uyumlu)

## Teknik Notlar
- Strategy pattern: her banka için ayrı normalizer, ortak `Transaction` çıktısı
- TL karşılığı kart ekstresinden alınır; kur hesabı uygulamada YAPILMAZ (ekstre değeri esas)
- Kart referans verisi E1-02 veri modelinde tutulur
- QNB ileride eklenebilir; arayüz buna hazır olmalı

## Açık Sorular / Riskler
- QNB kartı şu an aktif mi yoksa ileriye dönük mü? — kapsamı netleştir
- Banka formatları zamanla değişirse normalizer kırılır; örnek dosyalarla regresyon testi gerekli
- Para birimi sembolleri/kodları tutarlı mı (USD/$ , EUR/€, TL/₺)? — kanonik kod (ISO) kullanılması önerilir
